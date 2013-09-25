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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.SafeWriteRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import gnu.trove.THashMap;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public abstract class XmlElementStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String ATTR_NAME = "name";
  private static final String VERSION_FILE_SUFFIX = ".ver";

  protected TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  @NotNull private final String myRootElementName;
  private Object mySession;
  private StorageData myLoadedData;
  protected final StreamProvider myStreamProvider;
  protected final String myFileSpec;
  private final ComponentRoamingManager myComponentRoamingManager;
  protected boolean myBlockSavingTheContent = false;
  protected int myUpToDateHash = -1;
  protected int myProviderUpToDateHash = -1;
  private boolean mySavingDisabled = false;

  private final Map<String, Object> myStorageComponentStates = new THashMap<String, Object>(); // at loading we store Element, on setState Integer of hash// at loading we store Element, on setState Integer of hash

  private final ComponentVersionProvider myLocalVersionProvider;
  protected final RemoteComponentVersionProvider myRemoteVersionProvider;

  protected ComponentVersionListener myListener = new ComponentVersionListener(){
    @Override
    public void componentStateChanged(String componentName) {
      myLocalVersionProvider.changeVersion(componentName,  System.currentTimeMillis());
    }
  };

  private boolean myDisposed;

  protected XmlElementStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                              @NotNull Disposable parentDisposable,
                              @NotNull String rootElementName,
                              @Nullable StreamProvider streamProvider,
                              String fileSpec,
                              ComponentRoamingManager componentRoamingManager, ComponentVersionProvider localComponentVersionsProvider) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myRootElementName = rootElementName;
    myStreamProvider = streamProvider;
    myFileSpec = fileSpec;
    myComponentRoamingManager = componentRoamingManager;
    Disposer.register(parentDisposable, this);

    myLocalVersionProvider = localComponentVersionsProvider;
    myRemoteVersionProvider = streamProvider == null || !streamProvider.isVersioningRequired() ? null : new RemoteComponentVersionProvider();
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
    return getStorageData(reloadData).hasState(componentName);
  }

  @Override
  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    return DefaultStateSerializer.deserializeState(getState(componentName), stateClass, mergeInto);
  }

  @NotNull
  protected StorageData getStorageData(final boolean reloadData) throws StateStorageException {
    if (myLoadedData != null && !reloadData) {
      return myLoadedData;
    }

    myLoadedData = loadData(true);
    return myLoadedData;
  }

  @NotNull
  protected StorageData loadData(boolean useProvidersData) throws StateStorageException {
    Document document = loadDocument();
    StorageData result = createStorageData();

    if (document != null) {
      loadState(result, document.getRootElement());
    }

    if (useProvidersData && myStreamProvider != null && myStreamProvider.isEnabled()) {
      for (RoamingType roamingType : RoamingType.values()) {
        if (roamingType != RoamingType.DISABLED && roamingType != RoamingType.GLOBAL) {
          try {
            Document sharedDocument = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec, roamingType));
            if (sharedDocument != null) {
              filterOutOfDate(sharedDocument.getRootElement());
              loadState(result, sharedDocument.getRootElement());
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
    LOG.assertTrue(mySession == externalizationSession);

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
      if (element.getAttributes().isEmpty() && element.getChildren().isEmpty()) {
        return;
      }

      myStorageData.setState(componentName, element);
      int hash = JDOMUtil.getTreeHash(element);
      try {
        Object oldElementState = myStorageComponentStates.get(componentName);
        if (oldElementState instanceof Element && !JDOMUtil.areElementsEqual((Element)oldElementState, element) ||
            oldElementState instanceof Integer && hash != (Integer)oldElementState) {
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
      }
      finally {
        myPathMacroSubstitutor.reset();
      }
    }

    return new Document(element);
  }

  protected abstract class MySaveSession implements SaveSession, SafeWriteRequestor {
    final StorageData myStorageData;
    private Document myDocumentToSave;

    public MySaveSession(MyExternalizationSession externalizationSession) {
      myStorageData = externalizationSession.myStorageData;
    }

    public final boolean needsSave() throws StateStorageException {
      assert mySession == this;
      return _needsSave(calcHash());
    }

    private boolean _needsSave(int hash) {
      if (myBlockSavingTheContent) {
        return false;
      }

      if (myUpToDateHash == -1) {
        if (hash != -1) {
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
      else if (hash != -1) {
        if (hash == myUpToDateHash) {
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

    protected boolean physicalContentNeedsSave() {
      return true;
    }

    protected abstract void doSave() throws StateStorageException;

    protected int calcHash() {
      return -1;
    }

    @Override
    public final void save() throws StateStorageException {
      assert mySession == this;

      if (myBlockSavingTheContent) {
        return;
      }

      int hash = calcHash();
      try {
        if (myStreamProvider != null && myStreamProvider.isEnabled() && (myProviderUpToDateHash == -1 || myProviderUpToDateHash != hash)) {
          try {
            //noinspection IfStatementWithIdenticalBranches
            if (saveForProvider(myStreamProvider)) {
              //noinspection UnnecessaryReturnStatement
              return;
            }
          }
          finally {
            myProviderUpToDateHash = hash;
          }
        }
      }
      finally {
        saveLocally(hash);
      }
    }

    private void saveLocally(final Integer hash) {
      try {
        if (!isHashUpToDate(hash) && _needsSave(hash)) {
          doSave();
        }
      }
      finally {
        myUpToDateHash = hash;
      }
    }

    private boolean saveForProvider(@NotNull StreamProvider streamProvider) {
      if (!streamProvider.isApplicable(myFileSpec, RoamingType.PER_USER)) {
        return false;
      }

      Document document = getDocumentToSave();
      Element rootElement = document.getRootElement();
      if (rootElement.getChildren().isEmpty()) {
        return false;
      }

      // skip the whole document if some component has disabled roaming type
      // you must not store components with different roaming types in one document
      // one exclusion: workspace file (you don't have choice in this case)
      // for example, it is important for ICS ProjectId - we cannot keep project in another place,
      // but this project id must not be shared
      if (!myFileSpec.equals(StoragePathMacros.WORKSPACE_FILE) &&
          rootElement.getContent(new RoamingElementFilter(RoamingType.DISABLED)).iterator().hasNext()) {
        return false;
      }

      RoamingElementFilter perPlatformFilter = new RoamingElementFilter(RoamingType.PER_PLATFORM);
      if (rootElement.getContent(perPlatformFilter).iterator().hasNext()) {
        return doSaveForProvider(rootElement, new RoamingElementFilter(RoamingType.PER_USER)) ||
               doSaveForProvider(rootElement, perPlatformFilter);
      }
      else {
        return doSaveForProvider(document, RoamingType.PER_USER, streamProvider);
      }
    }

    private boolean doSaveForProvider(Element element, RoamingElementFilter filter) {
      Element copiedElement = JDOMUtil.cloneElement(element, filter);
      return copiedElement != null && doSaveForProvider(new Document(copiedElement), filter.myRoamingType, myStreamProvider);
    }

    private boolean doSaveForProvider(Document actualDocument, RoamingType roamingType, StreamProvider streamProvider) {
      try {
        boolean result = StorageUtil.doSendContent(streamProvider, myFileSpec, actualDocument, roamingType, true);
        if (streamProvider.isVersioningRequired()) {
          TObjectLongHashMap<String> versions = loadVersions(actualDocument.getRootElement().getChildren(StorageData.COMPONENT));
          if (!versions.isEmpty()) {
            Document versionDoc = new Document(StateStorageManagerImpl.createComponentVersionsXml(versions));
            StorageUtil.doSendContent(streamProvider, myFileSpec + VERSION_FILE_SUFFIX, versionDoc, roamingType, true);
          }
        }
        return result;
      }
      catch (IOException e) {
        LOG.warn(e);
        return false;
      }
    }

    private boolean isHashUpToDate(final Integer hash) {
      return myUpToDateHash != -1 && myUpToDateHash == hash;
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

    private class RoamingElementFilter extends ElementFilter {
      final RoamingType myRoamingType;

      public RoamingElementFilter(RoamingType roamingType) {
        super(StorageData.COMPONENT);

        myRoamingType = roamingType;
      }

      @Override
      public boolean matches(Object obj) {
        return super.matches(obj) && myComponentRoamingManager.getRoamingType(((Element)obj).getAttributeValue(StorageData.NAME)) == myRoamingType;
      }
    }
  }

  private TObjectLongHashMap<String> loadVersions(List<Element> elements) {
    TObjectLongHashMap<String> result = new TObjectLongHashMap<String>();
    for (Element component : elements) {
      String name = component.getAttributeValue(ATTR_NAME);
      if (name != null) {
        long version = myLocalVersionProvider.getVersion(name);
        if (version > 0) {
          result.put(name, version);
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
    final StorageData storageData = loadData(false);

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

  private void filterOutOfDate(Element element) {
    if (myRemoteVersionProvider == null) {
      return;
    }

    Iterator<Element> iterator = element.getContent(new ElementFilter(StorageData.COMPONENT)).iterator();
    while (iterator.hasNext()) {
      String name = iterator.next().getAttributeValue(StorageData.NAME);
      long remoteVersion = myRemoteVersionProvider.getVersion(name);
      if (remoteVersion <= myLocalVersionProvider.getVersion(name)) {
        iterator.remove();
      }
      else {
        myLocalVersionProvider.changeVersion(name, remoteVersion);
      }
    }
  }

  @Nullable
  Document logComponents() throws StateStorageException {
    return mySession instanceof MySaveSession ? getDocument(((MySaveSession)mySession).myStorageData) : null;
  }

  protected class RemoteComponentVersionProvider implements ComponentVersionProvider {
    protected TObjectLongHashMap<String> myProviderVersions;

    @Override
    public long getVersion(String name) {
      if (myProviderVersions == null) {
        loadProviderVersions();
      }
      return myProviderVersions == null ? -1 : myProviderVersions.get(name);
    }

    @Override
    public void changeVersion(String name, long version) {
      if (myProviderVersions == null) {
        loadProviderVersions();
      }
      if (myProviderVersions != null) {
        myProviderVersions.put(name, version);
      }
    }

    private void loadProviderVersions() {
      assert myStreamProvider != null;
      if (!myStreamProvider.isEnabled()) {
        return;
      }

      myProviderVersions = new TObjectLongHashMap<String>();
      for (RoamingType type : RoamingType.values()) {
        try {
          Document doc = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec + VERSION_FILE_SUFFIX, type));
          if (doc != null) {
            StateStorageManagerImpl.loadComponentVersions(myProviderVersions, doc);
          }
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
  }
}
