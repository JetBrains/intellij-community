/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.SafeWriteRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public abstract class XmlElementStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String ATTR_NAME = "name";

  protected TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  @NotNull private final String myRootElementName;
  private Object mySession;
  private StorageData myLoadedData;
  protected final StreamProvider myStreamProvider;
  protected final String myFileSpec;
  private final ComponentRoamingManager myComponentRoamingManager;
  protected final boolean myIsProjectSettings;
  protected boolean myBlockSavingTheContent = false;
  protected Integer myUpToDateHash;
  protected Integer myProviderUpToDateHash;
  private boolean mySavingDisabled = false;

  private final Map<String, Object> myStorageComponentStates = new THashMap<String, Object>(); // at loading we store Element, on setState Integer of hash// at loading we store Element, on setState Integer of hash

  private final ComponentVersionProvider myLocalVersionProvider;
  private final ComponentVersionProvider myRemoteVersionProvider;

  protected Map<String, Long> myProviderVersions = null;

  protected ComponentVersionListener myListener = new ComponentVersionListener(){
    @Override
    public void componentStateChanged(String componentName) {
      myLocalVersionProvider.changeVersion(componentName,  System.currentTimeMillis());
    }
  };

  private boolean myDisposed;


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
    myIsProjectSettings = StoragePathMacros.PROJECT_FILE.equals(myFileSpec) || myFileSpec.startsWith(StoragePathMacros.PROJECT_CONFIG_DIR);

    myLocalVersionProvider = localComponentVersionsProvider;

    myRemoteVersionProvider = new ComponentVersionProvider(){
      @Override
      public long getVersion(String name) {
        if (myProviderVersions == null) {
          loadProviderVersions();
        }

        return myProviderVersions.containsKey(name) ? myProviderVersions.get(name).longValue() : 0;

      }

      @Override
      public void changeVersion(String name, long version) {
        if (myProviderVersions == null) {
          loadProviderVersions();
        }

        myProviderVersions.put(name, version);
      }
    };
  }

  protected boolean isDisposed() {
    return myDisposed;
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

  @Override
  public boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException {
    final StorageData storageData = getStorageData(reloadData);
    return storageData.hasState(componentName);
  }

  @Override
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
  protected StorageData loadData(boolean useProvidersData, @SuppressWarnings("UnusedParameters") ComponentVersionListener listener) throws StateStorageException {
    Document document = loadDocument();

    StorageData result = createStorageData();

    if (document != null) {
      loadState(result, document.getRootElement());
    }

    if (!myIsProjectSettings && useProvidersData && myStreamProvider.isEnabled()) {
      for (RoamingType roamingType : RoamingType.values()) {
        if (roamingType != RoamingType.DISABLED && roamingType != RoamingType.GLOBAL) {
          loadProviderData(result, roamingType);
        }
      }
    }

    return result;
  }

  private void loadProviderData(StorageData result, RoamingType roamingType) {
    try {
      final Document sharedDocument = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec, roamingType));
      if (sharedDocument != null) {
        filterComponentsDisabledForRoaming(sharedDocument.getRootElement(), roamingType);
        filterOutOfDateComponents(sharedDocument.getRootElement());
        loadState(result, sharedDocument.getRootElement());
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  protected void loadState(final StorageData result, final Element element) throws StateStorageException {
    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(element);
    }

    IdeaPluginDescriptorImpl.internJDOMElement(element);

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

  @Override
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

  @Override
  @NotNull
  public SaveSession startSave(@NotNull final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession saveSession = mySavingDisabled ? createNullSession() : createSaveSession((MyExternalizationSession)externalizationSession);
    mySession = saveSession;
    return saveSession;
  }

  private static SaveSession createNullSession() {
    return new SaveSession(){
      @Override
      public void save() throws StateStorageException {

      }

      @Override
      public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
        return Collections.emptySet();
      }

      @NotNull
      @Override
      public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
        return Collections.emptySet();
      }

      @NotNull
      @Override
      public List<IFile> getAllStorageFiles() {
        return Collections.emptyList();
      }
    };
  }

  protected abstract MySaveSession createSaveSession(final MyExternalizationSession externalizationSession);

  @Override
  public void finishSave(@NotNull final SaveSession saveSession) {
    try {
      if (mySession != saveSession) {
        LOG.error("mySession=" + mySession + " saveSession=" + saveSession);
      }
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

    @Override
    public void setState(@NotNull final Object component, final String componentName, @NotNull final Object state, final Storage storageSpec) throws StateStorageException {
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
      int hash = JDOMUtil.getTreeHash(element);

      try {
        Object oldElementState = myStorageComponentStates.get(componentName);

        if (oldElementState instanceof Element && !JDOMUtil.areElementsEqual((Element)oldElementState, element) ||
            oldElementState instanceof Integer && hash != (Integer)oldElementState
           ) {
          myListener.componentStateChanged(componentName);
        }
      }
      finally {
        myStorageComponentStates.put(componentName, hash);
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

  protected abstract class MySaveSession implements SaveSession, SafeWriteRequestor {
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

    protected Integer calcHash() {
      return null;
    }

    @Override
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

                  if (!copy.getRootElement().getChildren().isEmpty()) {
                    StorageUtil.sendContent(myStreamProvider, myFileSpec, copy, roamingType, true);
                    Document versionDoc = createVersionDocument(copy);
                    if (!versionDoc.getRootElement().getChildren().isEmpty()) {
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

    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
      try {
        Document document = loadDocument();

        StorageData storageData = createStorageData();

        if (document == null) {
          return Collections.emptySet();
        }
        loadState(storageData, document.getRootElement());
        return storageData.getDifference(myStorageData, myPathMacroSubstitutor);
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
    THashMap<String, Long> result = new THashMap<String, Long>();

    List list = copy.getRootElement().getChildren(StorageData.COMPONENT);
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

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public void resetData(){
    myLoadedData = null;
  }

  @Override
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
          emptyElement.setAttribute(StorageData.NAME, componentToRetain);
          storageData.myComponentStates.put(componentToRetain, emptyElement);
        }
      }

      storageData.myComponentStates.keySet().retainAll(componentsToRetain);
    }

    myLoadedData = storageData;
  }

  private void filterComponentsDisabledForRoaming(final Element element, final RoamingType roamingType) {
    final List components = element.getChildren(StorageData.COMPONENT);

    List<Element> toDelete = new ArrayList<Element>();

    for (Object componentObj : components) {
      final Element componentElement = (Element)componentObj;
      final String nameAttr = componentElement.getAttributeValue(StorageData.NAME);

      if (myComponentRoamingManager.getRoamingType(nameAttr) != roamingType) {
        toDelete.add(componentElement);
      }
    }

    for (Element toDeleteElement : toDelete) {
      element.removeContent(toDeleteElement);
    }
  }

  private void filterOutOfDateComponents(final Element element) {
    final List components = element.getChildren(StorageData.COMPONENT);

    List<Element> toDelete = new ArrayList<Element>();

    for (Object componentObj : components) {
      final Element componentElement = (Element)componentObj;
      final String nameAttr = componentElement.getAttributeValue(StorageData.NAME);

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
    myProviderVersions = new THashMap<String, Long>();
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

  @Nullable
  Document logComponents() throws StateStorageException {
    return mySession instanceof MySaveSession ? getDocument(((MySaveSession)mySession).myStorageData) : null;
  }
}
