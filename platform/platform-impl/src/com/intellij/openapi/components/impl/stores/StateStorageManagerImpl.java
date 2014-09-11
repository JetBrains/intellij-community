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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StateStorageManagerImpl implements StateStorageManager, Disposable, ComponentVersionProvider {
  private static final Logger LOG = Logger.getInstance(StateStorageManagerImpl.class);

  private static final boolean ourHeadlessEnvironment;
  static {
    final Application app = ApplicationManager.getApplication();
    ourHeadlessEnvironment = app.isHeadlessEnvironment() || app.isUnitTestMode();
  }

  private final Map<String, String> myMacros = new LinkedHashMap<String, String>();
  private final Lock myStorageLock = new ReentrantLock();
  private final Map<String, StateStorage> myStorages = new THashMap<String, StateStorage>();
  private final Map<String, StateStorage> myPathToStorage = new THashMap<String, StateStorage>();
  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final String myRootTagName;
  private Object mySession;
  private final PicoContainer myPicoContainer;

  private TObjectLongHashMap<String> myComponentVersions;
  private final Object myComponentVersionsLock = new Object();

  private String myVersionsFilePath;

  private boolean isDirty;

  private StreamProvider myStreamProvider;

  private final OldStreamProviderManager myOldStreamProvider = new OldStreamProviderManager();

  public StateStorageManagerImpl(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                                 String rootTagName,
                                 @Nullable Disposable parentDisposable,
                                 PicoContainer picoContainer) {
    myPicoContainer = picoContainer;
    myRootTagName = rootTagName;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  @Override
  public TrackingPathMacroSubstitutor getMacroSubstitutor() {
    return myPathMacroSubstitutor;
  }

  @Override
  public synchronized void addMacro(@NotNull String macro, @NotNull String expansion) {
    assert !macro.isEmpty();
    // backward compatibility
    if (macro.charAt(0) != '$') {
      LOG.warn("Add macros instead of macro name: " + macro);
      expansion = '$' + macro + '$';
    }
    myMacros.put(macro, expansion);
  }

  @Override
  @Nullable
  public StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorageException {
    String key = getStorageSpecId(storageSpec);

    myStorageLock.lock();
    try {
      StateStorage stateStorage = myStorages.get(key);
      if (stateStorage == null) {
        stateStorage = createStateStorage(storageSpec);
        putStorageToMap(key, stateStorage);
      }
      return stateStorage;
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @Nullable
  @Override
  public StateStorage getStateStorage(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    myStorageLock.lock();
    try {
      StateStorage stateStorage = myStorages.get(fileSpec);
      if (stateStorage == null) {
        stateStorage = createFileStateStorage(fileSpec, roamingType);
        putStorageToMap(fileSpec, stateStorage);
      }
      return stateStorage;
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @Override
  @Nullable
  public StateStorage getFileStateStorage(@NotNull String fileSpec) {
    return getStateStorage(fileSpec, RoamingType.PER_USER);
  }

  @NotNull
  @Override
  public Collection<String> getStorageFileNames() {
    myStorageLock.lock();
    try {
      if (myStorages.isEmpty()) {
        return Collections.emptyList();
      }
      return Collections.unmodifiableCollection(myStorages.keySet());
    }
    finally {
      myStorageLock.unlock();
    }
  }

  private void putStorageToMap(final String key, final StateStorage stateStorage) {
    if (stateStorage != null) {
      if (stateStorage instanceof FileBasedStorage) {
        //fixing problem with 2 macros for the same directory (APP_CONFIG and OPTIONS)
        String filePath = ((FileBasedStorage)stateStorage).getFilePath();
        if (myPathToStorage.containsKey(filePath)) {
          StateStorage existing = myPathToStorage.get(filePath);
          myStorages.put(key, existing);
        }
        else {
          myPathToStorage.put(filePath, stateStorage);
          myStorages.put(key, stateStorage);
        }
      }
      else {
        myStorages.put(key, stateStorage);
      }
    }
  }

  @Nullable
  private StateStorage createStateStorage(Storage storageSpec) throws StateStorageException {
    if (!storageSpec.storageClass().equals(StateStorage.class)) {
      String key = UUID.randomUUID().toString();
      ((MutablePicoContainer)myPicoContainer).registerComponentImplementation(key, storageSpec.storageClass());
      return (StateStorage)myPicoContainer.getComponentInstance(key);
    }
    if (!storageSpec.stateSplitter().equals(StateSplitter.class)) {
      return createDirectoryStateStorage(storageSpec.file(), storageSpec.stateSplitter());
    }
    return createFileStateStorage(storageSpec.file(), storageSpec.roamingType());
  }

  private static String getStorageSpecId(Storage storageSpec) {
    if (!storageSpec.storageClass().equals(StateStorage.class)) {
      return storageSpec.storageClass().getName();
    }
    else {
      return storageSpec.file();
    }
  }

  @Override
  public void clearStateStorage(@NotNull String file) {
    myStorageLock.lock();
    try {
      myStorages.remove(file);
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @Nullable
  private StateStorage createDirectoryStateStorage(String file, Class<? extends StateSplitter> splitterClass) throws StateStorageException {
    final StateSplitter splitter;
    try {
      splitter = ReflectionUtil.newInstance(splitterClass);
    }
    catch (RuntimeException e) {
      throw new StateStorageException(e);
    }
    return new DirectoryBasedStorage(myPathMacroSubstitutor, expandMacros(file), splitter, this, myPicoContainer);
  }

  @Nullable
  private StateStorage createFileStateStorage(@NotNull final String fileSpec, @Nullable RoamingType roamingType) {
    String expandedFile = expandMacros(fileSpec);

    if (!ourHeadlessEnvironment && PathUtilRt.getFileName(expandedFile).lastIndexOf('.') < 0) {
      throw new IllegalArgumentException("Extension is missing for storage file: " + expandedFile);
    }

    if (roamingType != RoamingType.PER_USER && fileSpec.equals(StoragePathMacros.WORKSPACE_FILE)) {
      roamingType = RoamingType.DISABLED;
    }

    return new FileBasedStorage(expandedFile, fileSpec, roamingType, getMacroSubstitutor(fileSpec), myRootTagName, this,
                                myPicoContainer, getStreamProvider(), this) {
      @Override
      @NotNull
      protected StorageData createStorageData() {
        return StateStorageManagerImpl.this.createStorageData(fileSpec);
      }
    };
  }

  @Override
  public long getVersion(String name) {
    return getComponentVersions().get(name);
  }

  @TestOnly
  public void changeVersionsFilePath(String newPath) {
    myVersionsFilePath = newPath;
    synchronized (myComponentVersionsLock) {
      myComponentVersions = null;
    }
    isDirty = false;
  }

  private TObjectLongHashMap<String> loadVersions() {
    TObjectLongHashMap<String> result = new TObjectLongHashMap<String>();
    String filePath = getNotNullVersionsFilePath();
    if (filePath != null) {
      try {
        loadComponentVersions(result, JDOMUtil.loadDocument(new File(filePath)));
      }
      catch (JDOMException e) {
        LOG.debug(e);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }
    return result;
  }

  private String getNotNullVersionsFilePath() {
    if (myVersionsFilePath == null) {
      myVersionsFilePath = getVersionsFilePath();
    }

    return myVersionsFilePath;
  }

  public static void loadComponentVersions(TObjectLongHashMap<String> result, Document document) {
    List<Element> componentObjects = document.getRootElement().getChildren("component");
    result.ensureCapacity(componentObjects.size());
    for (Element component : componentObjects) {
      String name = component.getAttributeValue("name");
      String version = component.getAttributeValue("version");
      if (name != null && version != null) {
        try {
          result.put(name, Long.parseLong(version));
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  protected abstract String getVersionsFilePath();

  @Override
  public void changeVersion(String name, long version) {
    getComponentVersions().put(name, version);
    isDirty = true;
  }

  @Nullable
  @Override
  public StreamProvider getStreamProvider() {
    return ObjectUtils.chooseNotNull(myStreamProvider, myOldStreamProvider);
  }

  protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
    return myPathMacroSubstitutor;
  }

  protected abstract StorageData createStorageData(String storageSpec);

  private static final Pattern MACRO_PATTERN = Pattern.compile("(\\$[^\\$]*\\$)");

  @Override
  @NotNull
  public synchronized String expandMacros(@NotNull String file) {
    Matcher matcher = MACRO_PATTERN.matcher(file);
    while (matcher.find()) {
      String m = matcher.group(1);
      if (!myMacros.containsKey(m)) {
        throw new IllegalArgumentException("Unknown macro: " + m + " in storage file spec: " + file);
      }
    }

    String expanded = file;
    for (String macro : myMacros.keySet()) {
      expanded = StringUtil.replace(expanded, macro, myMacros.get(macro));
    }
    return expanded;
  }

  @NotNull
  @Override
  public ExternalizationSession startExternalization() {
    if (mySession != null) {
      LOG.error("Starting duplicate externalization session: " + mySession);
    }
    ExternalizationSession session = new MyExternalizationSession();
    mySession = session;
    return session;
  }

  @NotNull
  @Override
  public SaveSession startSave(@NotNull final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;
    SaveSession session = createSaveSession(externalizationSession);
    mySession = session;
    return session;
  }

  @NotNull
  protected MySaveSession createSaveSession(final ExternalizationSession externalizationSession) {
    return new MySaveSession((MyExternalizationSession)externalizationSession);
  }

  @Override
  public void finishSave(@NotNull final SaveSession saveSession) {
    try {
      assert mySession == saveSession : "mySession=" + mySession + " saveSession=" + saveSession;
      ((MySaveSession)saveSession).finishSave();
    }
    finally {
      mySession = null;
      save();
    }
  }

  @Override
  public void reset() {
    mySession = null;
  }

  protected class MyExternalizationSession implements ExternalizationSession {
    CompoundExternalizationSession myCompoundExternalizationSession = new CompoundExternalizationSession();

    @Override
    public void setState(@NotNull final Storage[] storageSpecs, @NotNull final Object component, final String componentName, @NotNull final Object state)
      throws StateStorageException {
      assert mySession == this;

      for (Storage storageSpec : storageSpecs) {
        StateStorage stateStorage = getStateStorage(storageSpec);
        if (stateStorage == null) continue;

        final StateStorage.ExternalizationSession extSession = myCompoundExternalizationSession.getExternalizationSession(stateStorage);
        extSession.setState(component, componentName, state, storageSpec);
      }
    }

    @Override
    public void setStateInOldStorage(@NotNull Object component, @NotNull final String componentName, @NotNull Object state) throws StateStorageException {
      assert mySession == this;
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.WRITE);
      if (stateStorage != null) {
        myCompoundExternalizationSession.getExternalizationSession(stateStorage).setState(component, componentName, state, null);
      }
    }
  }

  @Override
  @Nullable
  public StateStorage getOldStorage(Object component, String componentName, StateStorageOperation operation) throws StateStorageException {
    String oldStorageSpec = getOldStorageSpec(component, componentName, operation);
    //noinspection deprecation
    return oldStorageSpec == null ? null : getStateStorage(oldStorageSpec, component instanceof RoamingTypeDisabled ? RoamingType.DISABLED : RoamingType.PER_USER);
  }

  @Nullable
  protected abstract String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation)
    throws StateStorageException;

  protected class MySaveSession implements SaveSession {
    CompoundSaveSession myCompoundSaveSession;

    public MySaveSession(final MyExternalizationSession externalizationSession) {
      myCompoundSaveSession = new CompoundSaveSession(externalizationSession.myCompoundExternalizationSession);
    }

    @Override
    @NotNull
    public List<File> getAllStorageFilesToSave() throws StateStorageException {
      assert mySession == this;
      return myCompoundSaveSession.getAllStorageFilesToSave();
    }

    @Override
    @NotNull
    public List<File> getAllStorageFiles() {
      return myCompoundSaveSession.getAllStorageFiles();
    }

    @Override
    public void save() throws StateStorageException {
      assert mySession == this;
      myCompoundSaveSession.save();
    }

    public void finishSave() {
      try {
        LOG.assertTrue(mySession == this);
      }
      finally {
        myCompoundSaveSession.finishSave();
      }
    }

    //returns set of component which were changed, null if changes are much more than just component state.
    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      Set<String> result = new THashSet<String>();
      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        final StateStorage.SaveSession saveSession = myCompoundSaveSession.getSaveSession(pair.second);
        if (saveSession == null) {
          continue;
        }
        final Set<String> changes = saveSession.analyzeExternalChanges(changedFiles);
        if (changes == null) {
          return null;
        }
        result.addAll(changes);
      }
      return result;
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public void registerStreamProvider(@SuppressWarnings("deprecation") com.intellij.openapi.options.StreamProvider streamProvider, final RoamingType type) {
    synchronized (myOldStreamProvider) {
      myOldStreamProvider.myStreamProviders.add(new OldStreamProviderAdapter(streamProvider, type));
    }
  }

  @Override
  public void setStreamProvider(@Nullable StreamProvider streamProvider) {
    myStreamProvider = streamProvider;
  }

  public void save() {
    if (!isDirty) {
      return;
    }

    String filePath = getNotNullVersionsFilePath();
    if (filePath != null) {
      File file = new File(filePath);
      FileUtilRt.createParentDirs(file);
      try {
        JDOMUtil.writeParent(createComponentVersionsXml(getComponentVersions()), file, "\n");
        isDirty = false;
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  private TObjectLongHashMap<String> getComponentVersions() {
    synchronized (myComponentVersionsLock) {
      if (myComponentVersions == null) {
        myComponentVersions = loadVersions();
      }
      return myComponentVersions;
    }
  }

  public static Element createComponentVersionsXml(TObjectLongHashMap<String> versions) {
    Element root = new Element("versions");
    Object[] componentNames = versions.keys();
    Arrays.sort(componentNames);
    for (Object key : componentNames) {
      String name = (String)key;
      long version = versions.get(name);
      if (version != 0) {
        Element element = new Element("component");
        root.addContent(element);
        element.setAttribute("name", name);
        element.setAttribute("version", String.valueOf(version));
      }
    }
    return root;
  }

  @SuppressWarnings("deprecation")
  private static class OldStreamProviderManager extends StreamProvider implements CurrentUserHolder {
    private final List<OldStreamProviderAdapter> myStreamProviders = new SmartList<OldStreamProviderAdapter>();

    @Override
    public boolean isVersioningRequired() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      for (StreamProvider provider : myStreamProviders) {
        if (provider.isEnabled()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean isApplicable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      for (StreamProvider provider : myStreamProviders) {
        if (provider.isApplicable(fileSpec, roamingType)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType, boolean async) throws IOException {
      for (StreamProvider streamProvider : myStreamProviders) {
        try {
          if (streamProvider.isEnabled() && streamProvider.isApplicable(fileSpec, roamingType)) {
            streamProvider.saveContent(fileSpec, content, size, roamingType, async);
          }
        }
        catch (Exception e) {
          LOG.debug(e);
        }
      }
    }

    @Override
    public InputStream loadContent(@NotNull final String fileSpec, @NotNull final RoamingType roamingType) throws IOException {
      for (StreamProvider streamProvider : myStreamProviders) {
        try {
          if (streamProvider.isEnabled() && streamProvider.isApplicable(fileSpec, roamingType)) {
            InputStream content = streamProvider.loadContent(fileSpec, roamingType);
            if (content != null) {
              return content;
            }
          }
        }
        catch (ConnectException e) {
          LOG.debug("Cannot send user profile o server: " + e.getLocalizedMessage());
        }
        catch (Exception e) {
          LOG.debug(e);
        }
      }

      return null;
    }

    @Override
    public void delete(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      for (StreamProvider streamProvider : myStreamProviders) {
        try {
          if (streamProvider.isEnabled() && streamProvider.isApplicable(fileSpec, roamingType)) {
            streamProvider.delete(fileSpec, roamingType);
          }
        }
        catch (Exception e) {
          LOG.debug(e);
        }
      }
    }

    @Override
    public String getCurrentUserName() {
      for (OldStreamProviderAdapter provider : myStreamProviders) {
        if (!provider.isEnabled()) {
          continue;
        }

        String userName = provider.getCurrentUserName();
        if (userName != null) {
          return userName;
        }
      }
      return null;
    }
  }
}
