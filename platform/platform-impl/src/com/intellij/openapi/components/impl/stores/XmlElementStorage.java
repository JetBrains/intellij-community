/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.io.fs.IFile;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public abstract class XmlElementStorage implements StateStorage, Disposable {
  @NonNls private static final Set<String> OBSOLETE_COMPONENT_NAMES = new HashSet<String>(Arrays.asList(
    "Palette"
  ));
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String ATTR_NAME = "name";
  @NonNls private static final String NAME = ATTR_NAME;

  protected TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  @NotNull private final String myRootElementName;
  private Object mySession;
  private StorageData myLoadedData;
  protected static StringInterner ourInterner = new StringInterner();
  protected final StreamProvider myStreamProvider;
  protected final String myFileSpec;
  private final ComponentRoamingManager myComponentRoamingManager;
  protected final boolean myIsProjectSettings;
  protected boolean myBlockSavingTheContent = false;
  protected Integer myUpToDateHash;
  protected Integer myProviderUpToDateHash;
  private boolean mySavingDisabled = false;

  private final Map<String, Element> myStorageComponentStates = new TreeMap<String, Element>();

  private final ComponentVersionProvider myLocalVersionProvider;
  private final ComponentVersionProvider myRemoteVersionProvider;

  protected Map<String, Long> myProviderVersions = null;

  protected ComponentVersionListener myListener = new ComponentVersionListener(){
    public void componentStateChanged(String componentName) {
      myLocalVersionProvider.changeVersion(componentName,  System.currentTimeMillis());
    }
  };


  protected XmlElementStorage(@Nullable final TrackingPathMacroSubstitutor pathMacroSubstitutor,
                              @NotNull Disposable parentDisposable,
                              @NotNull String rootElementName,
                              StreamProvider streamProvider,
                              String fileSpec,
                              ComponentRoamingManager componentRoamingManager, ComponentVersionProvider localComponentVersionsProvider) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myRootElementName = rootElementName;
    myStreamProvider = streamProvider;
    myFileSpec = fileSpec;
    myComponentRoamingManager = componentRoamingManager;
    Disposer.register(parentDisposable, this);
    myIsProjectSettings = "$PROJECT_FILE$".equals(myFileSpec) || myFileSpec.startsWith("$PROJECT_CONFIG_DIR$");

    myLocalVersionProvider = localComponentVersionsProvider;

    myRemoteVersionProvider = new ComponentVersionProvider(){
      public long getVersion(String name) {
        if (myProviderVersions == null) {
          loadProviderVersions();
        }

        return myProviderVersions.containsKey(name) ? myProviderVersions.get(name).longValue() : 0;

      }

      public void changeVersion(String name, long version) {
        if (myProviderVersions == null) {
          loadProviderVersions();
        }

        myProviderVersions.put(name, version);
      }
    };
  }

  @Nullable
  protected abstract Document loadDocument() throws StateStorageException;

  @Nullable
  public synchronized Element getState(final String componentName) throws StateStorageException {
    final StorageData storageData = getStorageData(false);
    final Element state = storageData.getState(componentName);



    if (state != null) {
      if (!myStorageComponentStates.containsKey(componentName)) {
        myStorageComponentStates.put(componentName, state);
      }
      storageData.removeState(componentName);
    }

    return state;
  }

  public boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException {
    final StorageData storageData = getStorageData(reloadData);
    return storageData.hasState(componentName);
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    final Element element = getState(componentName);
    return DefaultStateSerializer.deserializeState(element, stateClass, mergeInto);
  }

  @NotNull
  protected StorageData getStorageData(final boolean reloadData) throws StateStorageException {
    if (myLoadedData != null && !reloadData) return myLoadedData;

    myLoadedData = loadData(true, myListener);

    return myLoadedData;
  }

  @NotNull
  protected StorageData loadData(final boolean useProvidersData, ComponentVersionListener listener) throws StateStorageException {
    Document document = loadDocument();

    StorageData result = createStorageData();

    if (document != null) {
      loadState(result, document.getRootElement());
    }
    else {
      LOG.info("Document was not loaded for " + myFileSpec);      
    }

    if (!myIsProjectSettings && useProvidersData) {
      for (RoamingType roamingType : RoamingType.values()) {
        if (roamingType != RoamingType.DISABLED && roamingType != RoamingType.GLOBAL) {
          try {
            if (myStreamProvider.isEnabled()) {
              final Document sharedDocument = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec, roamingType));

              if (sharedDocument != null) {
                filterComponentsDisabledForRoaming(sharedDocument.getRootElement(), roamingType);
                filterOutOfDateComponents(sharedDocument.getRootElement());

                loadState(result, sharedDocument.getRootElement());
              }
            }
          }
          catch (Exception e) {
            LOG.warn(e);
          }
        }

      }
    }


    return result;
  }

  protected void loadState(final StorageData result, final Element element) throws StateStorageException {
    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(element);
    }

    JDOMUtil.internElement(element, ourInterner);

    try {
      result.load(element);
      result.checkUnknownMacros(myPathMacroSubstitutor);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  @NotNull
  protected StorageData createStorageData() {
    return new StorageData(myRootElementName);
  }

  public void setDefaultState(final Element element) {
    myLoadedData = createStorageData();
    try {
      loadState(myLoadedData, element);
    }
    catch (StateStorageException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    try {
      final ExternalizationSession session = new MyExternalizationSession(getStorageData(false).clone(), myListener);

      mySession = session;
      return session;
    }
    catch (StateStorageException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession saveSession = mySavingDisabled ? createNullSession() : createSaveSession((MyExternalizationSession)externalizationSession);
    mySession = saveSession;
    return saveSession;
  }

  private SaveSession createNullSession() {
    return new SaveSession(){
      public void save() throws StateStorageException {

      }

      public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
        return Collections.emptySet();
      }

      public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
        return Collections.emptySet();
      }

      public List<IFile> getAllStorageFiles() {
        return Collections.emptyList();
      }
    };
  }

  protected abstract MySaveSession createSaveSession(final MyExternalizationSession externalizationSession);

  public void finishSave(final SaveSession saveSession) {
    try {
      LOG.assertTrue(mySession == saveSession, "mySession=" + mySession + " saveSession=" + saveSession);
    } finally {
      mySession = null;
    }
  }

  public void disableSaving() {
    mySavingDisabled = true;
  }

  protected class MyExternalizationSession implements ExternalizationSession {
    private final StorageData myStorageData;
    private final ComponentVersionListener myListener;

    public MyExternalizationSession(final StorageData storageData, ComponentVersionListener listener) {
      myStorageData = storageData;
      myListener = listener;
    }

    public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec) throws StateStorageException {
      assert mySession == this;

      try {
        setState(componentName, DefaultStateSerializer.serializeState(state, storageSpec));
      }
      catch (WriteExternalException e) {
        LOG.debug(e);
      }
    }

    private synchronized void setState(final String componentName, final Element element)  {
      if (element.getAttributes().isEmpty() && element.getChildren().isEmpty()) return;

      myStorageData.setState(componentName, element);

      Element oldElement = myStorageComponentStates.get(componentName);
      try {
        if (oldElement != null && !JDOMUtil.areElementsEqual(oldElement, element)) {
          myListener.componentStateChanged(componentName);
        }
      }
      finally {
        myStorageComponentStates.put(componentName, (Element)element.clone());
      }



    }
  }

  protected Document getDocument(StorageData data)  {
    final Element element = data.save();

    if (myPathMacroSubstitutor != null) {
      try {
        myPathMacroSubstitutor.collapsePaths(element);
      } finally {
        myPathMacroSubstitutor.reset();
      }
    }

    return new Document(element);
  }

  protected abstract class MySaveSession implements SaveSession {
    StorageData myStorageData;
    private Document myDocumentToSave;

    public MySaveSession(MyExternalizationSession externalizationSession) {
      myStorageData = externalizationSession.myStorageData;

    }

    public final boolean needsSave() throws StateStorageException {
      assert mySession == this;
      return _needsSave(calcHash());
    }

    private boolean _needsSave(final Integer hash) {
      if (myBlockSavingTheContent) return false;
      if (myUpToDateHash == null) {
        if (hash != null) {
          if (!physicalContentNeedsSave()) {
            myUpToDateHash = hash;
            return false;
          }
          else {
            return true;
          }
        }
        else {
          return true;
        }
      }
      else {
        if (hash != null) {
          if (hash.intValue() == myUpToDateHash.intValue()) {
            return false;
          }
          if (!physicalContentNeedsSave()) {
            myUpToDateHash = hash;
            return false;
          }
          else {
            return true;
          }

        }
        else {
          return physicalContentNeedsSave();
        }

      }
    }

    protected boolean physicalContentNeedsSave() {
      return true;
    }

    protected abstract void doSave() throws StateStorageException;

    public void clearHash() {
      myUpToDateHash = null;
    }

    protected Integer calcHash() {
      return null;
    }

    public final void save() throws StateStorageException {
      assert mySession == this;

      if (myBlockSavingTheContent) return;

      Integer hash = calcHash();

      try {
        saveForProviders(hash);
      }
      finally {
        saveLocally(hash);
      }
    }

    private void saveLocally(final Integer hash) {
      try {
        if (!isHashUpToDate(hash)) {
          if (_needsSave(hash)) {
            doSave();
          }
        }
      }
      finally {
        myUpToDateHash = hash;
      }
    }

    private void saveForProviders(final Integer hash) {
      if (myProviderUpToDateHash == null || !myProviderUpToDateHash.equals(hash)) {
        try {
          if (!myIsProjectSettings) {
            for (RoamingType roamingType : RoamingType.values()) {
              if (roamingType != RoamingType.DISABLED) {
                try {
                  Document copy = (Document)getDocumentToSave().clone();
                  filterComponentsDisabledForRoaming(copy.getRootElement(), roamingType);

                  if (copy.getRootElement().getChildren().size() > 0) {
                    StorageUtil.sendContent(myStreamProvider, myFileSpec, copy, roamingType, true);
                    Document versionDoc = createVersionDocument(copy);
                    if (versionDoc.getRootElement().getChildren().size() > 0) {
                      StorageUtil.sendContent(myStreamProvider, myFileSpec + ".ver", versionDoc, roamingType, true);
                    }
                  }
                }
                catch (IOException e) {
                  LOG.warn(e);
                }
              }

            }
          }
        }
        finally {
          myProviderUpToDateHash = hash;
        }

      }
    }

    private boolean isHashUpToDate(final Integer hash) {
      return myUpToDateHash != null && myUpToDateHash.equals(hash);
    }

    public boolean isHashUpToDate() {
      return isHashUpToDate(calcHash());
    }

    protected Document getDocumentToSave()  {
      if (myDocumentToSave != null) return myDocumentToSave;

      final Element element = myStorageData.save();
      myDocumentToSave = new Document(element);

      if (myPathMacroSubstitutor != null) {
        myPathMacroSubstitutor.collapsePaths(element);
      }

      return myDocumentToSave;
    }

    public StorageData getData() {
      return myStorageData;
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
      try {
        Document document = loadDocument();

        StorageData storageData = createStorageData();

        if (document != null) {
          loadState(storageData, document.getRootElement());
          return storageData.getDifference(myStorageData, myPathMacroSubstitutor);
        }
        else {
          return Collections.emptySet();
        }


      }
      catch (StateStorageException e) {
        LOG.info(e);
      }

      return null;
    }
  }

  private Document createVersionDocument(Document copy) {
    return new Document(StateStorageManagerImpl.createComponentVersionsXml(loadVersions(copy)));
  }

  private Map<String, Long> loadVersions(Document copy) {

    HashMap<String, Long> result = new HashMap<String, Long>();

    List list = copy.getRootElement().getChildren(COMPONENT);
    for (Object o : list) {
      if (o instanceof Element) {
        Element component = (Element)o;
        String name = component.getAttributeValue(ATTR_NAME);
        if (name != null) {
          long version = myLocalVersionProvider.getVersion(name);
          if (version != 0) {
            result.put(name, version);
          }
        }
      }
    }

    return result;
  }

  public void dispose() {
  }

  protected static class StorageData {
    private final Map<String, Element> myComponentStates;
    protected final String myRootElementName;
    private Integer myHash;

    public StorageData(final String rootElementName) {
      myComponentStates = new TreeMap<String, Element>();
      myRootElementName = rootElementName;
    }

    protected StorageData(StorageData storageData) {
      myRootElementName = storageData.myRootElementName;
      myComponentStates = new TreeMap<String, Element>(storageData.myComponentStates);
    }

    protected void load(@NotNull Element rootElement) throws IOException {
      final Element[] elements = JDOMUtil.getElements(rootElement);
      for (Element element : elements) {
        if (element.getName().equals(COMPONENT)) {
          final String name = element.getAttributeValue(NAME);

          if (name == null) {
            LOG.info("Broken content in file : " + this);
            continue;
          }

          if (OBSOLETE_COMPONENT_NAMES.contains(name)) continue;

          element.detach();

          if (element.getAttributes().size() > 1 || !element.getChildren().isEmpty()) {
            assert element.getAttributeValue(NAME) != null : "No name attribute for component: " + name + " in " + this;

            Element existingElement = myComponentStates.get(name);

            if (existingElement != null) {
              element = mergeElements(name, element, existingElement);
            }

            myComponentStates.put(name, element);
          }
        }
      }
    }

    private Element mergeElements(final String name, final Element element1, final Element element2) {
      ExtensionPoint<XmlConfigurationMerger> point = Extensions.getRootArea().getExtensionPoint("com.intellij.componentConfigurationMerger");
      XmlConfigurationMerger[] mergers = point.getExtensions();
      for (XmlConfigurationMerger merger : mergers) {
        if (merger.getComponentName().equals(name)) {
          return merger.merge(element1, element2);
        }
      }
      return element1;
    }

    @NotNull
    protected Element save() {
      Element rootElement = new Element(myRootElementName);

      for (String componentName : myComponentStates.keySet()) {
        assert componentName != null;
        final Element element = myComponentStates.get(componentName);

        if (element.getAttribute(NAME) == null) element.setAttribute(NAME, componentName);

        rootElement.addContent((Element)element.clone());
      }

      return rootElement;
    }

    @Nullable
    public Element getState(final String name) {
      final Element e = myComponentStates.get(name);

      if (e != null) {
        assert e.getAttributeValue(NAME) != null : "No name attribute for component: " + name + " in " + this;
        e.removeAttribute(NAME);
      }

      return e;
    }

    public void removeState(final String componentName) {
      myComponentStates.remove(componentName);
      clearHash();
    }

    private void setState(@NotNull final String componentName, final Element element) {
      element.setName(COMPONENT);

      //componentName should be first!
      final List attributes = new ArrayList(element.getAttributes());
      for (Object attribute : attributes) {
        Attribute attr = (Attribute)attribute;
        element.removeAttribute(attr);
      }

      element.setAttribute(NAME, componentName);

      for (Object attribute : attributes) {
        Attribute attr = (Attribute)attribute;
        element.setAttribute(attr.getName(), attr.getValue());
      }

      myComponentStates.put(componentName, element);
      clearHash();
    }

    public StorageData clone() {
      return new StorageData(this);
    }
    
    public final int getHash() {
      if (myHash == null) {
        myHash = computeHash();
      }
      return myHash.intValue();
    }

    protected int computeHash() {
      int result = 0;

      for (String name : myComponentStates.keySet()) {
        result = 31*result + name.hashCode();
        result = 31*result + JDOMUtil.getTreeHash(myComponentStates.get(name));
      }

      return result;
    }

    protected void clearHash() {
      myHash = null;
    }

    public Set<String> getDifference(final StorageData storageData, PathMacroSubstitutor substitutor) {
      Set<String> bothStates = new HashSet<String>(myComponentStates.keySet());
      bothStates.retainAll(storageData.myComponentStates.keySet());

      Set<String> diffs = new HashSet<String>();
      diffs.addAll(storageData.myComponentStates.keySet());
      diffs.addAll(myComponentStates.keySet());
      diffs.removeAll(bothStates);

      for (String componentName : bothStates) {
        final Element e1 = myComponentStates.get(componentName);
        final Element e2 = storageData.myComponentStates.get(componentName);

        // some configurations want to collapse path elements in writeExternal so make sure paths are expanded
        if (substitutor != null) {
          substitutor.expandPaths(e2);
        }

        if (!JDOMUtil.areElementsEqual(e1, e2)) {
          diffs.add(componentName);
        }
      }


      return diffs;
    }

    public boolean isEmpty() {
      return myComponentStates.size() == 0;
    }

    public boolean hasState(final String componentName) {
        return myComponentStates.containsKey(componentName);
    }

    public void checkUnknownMacros(TrackingPathMacroSubstitutor pathMacroSubstitutor) {
      for (String componentName : myComponentStates.keySet()) {
        final Set<String> unknownMacros = StorageUtil.getMacroNames(myComponentStates.get(componentName));
        if (!unknownMacros.isEmpty()) {
          pathMacroSubstitutor.addUnknownMacros(componentName, unknownMacros);
        }
      }
    }
  }

  public void resetData(){
    myLoadedData = null;
  }

  public void reload(@NotNull final Set<String> changedComponents) throws StateStorageException {
    final StorageData storageData = loadData(false, myListener);

    final StorageData oldLoadedData = myLoadedData;

    if (oldLoadedData != null) {
      Set<String> componentsToRetain = new HashSet<String>(oldLoadedData.myComponentStates.keySet());
      componentsToRetain.addAll(changedComponents);

      // add empty configuration tags for removed components
      for (String componentToRetain : componentsToRetain) {
        if (!storageData.myComponentStates.containsKey(componentToRetain) && myStorageComponentStates.containsKey(componentToRetain)) {
          Element emptyElement = new Element("component");
          LOG.info("Create empty component element for " + componentsToRetain);
          emptyElement.setAttribute(NAME, componentToRetain);
          storageData.myComponentStates.put(componentToRetain, emptyElement);
        }
      }

      storageData.myComponentStates.keySet().retainAll(componentsToRetain);
    }
    
    myLoadedData = storageData;
  }

  private void filterComponentsDisabledForRoaming(final Element element, final RoamingType roamingType) {
    final List components = element.getChildren(COMPONENT);

    List<Element> toDelete = new ArrayList<Element>();

    for (Object componentObj : components) {
      final Element componentElement = (Element)componentObj;
      final String nameAttr = componentElement.getAttributeValue(NAME);

      if (myComponentRoamingManager.getRoamingType(nameAttr) != roamingType) {
        toDelete.add(componentElement);
      }
    }

    for (Element toDeleteElement : toDelete) {
      element.removeContent(toDeleteElement);
    }
  }

  private void filterOutOfDateComponents(final Element element) {
    final List components = element.getChildren(COMPONENT);

    List<Element> toDelete = new ArrayList<Element>();

    for (Object componentObj : components) {
      final Element componentElement = (Element)componentObj;
      final String nameAttr = componentElement.getAttributeValue(NAME);

      if (myRemoteVersionProvider.getVersion(nameAttr) <= myLocalVersionProvider.getVersion(nameAttr)) {
        toDelete.add(componentElement);
      }
      else {
        myLocalVersionProvider.changeVersion(nameAttr, myRemoteVersionProvider.getVersion(nameAttr));
      }
    }

    for (Element toDeleteElement : toDelete) {
      element.removeContent(toDeleteElement);
    }

  }

  private void loadProviderVersions() {
    myProviderVersions = new TreeMap<String, Long>();
    for (RoamingType type : RoamingType.values()) {
      Document doc = null;
      if (myStreamProvider.isEnabled()) {
        try {
          doc = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec + ".ver", type));
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
      if (doc != null) {
        StateStorageManagerImpl.loadComponentVersions(myProviderVersions, doc);
      }

    }
  }

}
