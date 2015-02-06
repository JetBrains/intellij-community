/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StateStorageManagerImpl implements StateStorageManager, Disposable {
  private static final Logger LOG = Logger.getInstance(StateStorageManagerImpl.class);

  private static final boolean ourHeadlessEnvironment;
  static {
    final Application app = ApplicationManager.getApplication();
    ourHeadlessEnvironment = app.isHeadlessEnvironment() || app.isUnitTestMode();
  }

  private final Map<String, String> myMacros = new LinkedHashMap<String, String>();
  private final Lock myStorageLock = new ReentrantLock();
  private final Map<String, StateStorage> myStorages = new THashMap<String, StateStorage>();
  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final String myRootTagName;
  private final PicoContainer myPicoContainer;

  private StreamProvider myStreamProvider;

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
  @NotNull
  public StateStorage getStateStorage(@NotNull Storage storageSpec) {
    String key = getStorageSpecId(storageSpec);

    myStorageLock.lock();
    try {
      StateStorage stateStorage = myStorages.get(key);
      if (stateStorage == null) {
        stateStorage = createStateStorage(storageSpec);
        myStorages.put(key, stateStorage);
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
        myStorages.put(fileSpec, stateStorage);
      }
      return stateStorage;
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @NotNull
  @Override
  public Couple<Collection<FileBasedStorage>> getCachedFileStateStorages(@NotNull Collection<String> changed, @NotNull Collection<String> deleted) {
    myStorageLock.lock();
    try {
      return Couple.of(getCachedFileStorages(changed), getCachedFileStorages(deleted));
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @NotNull
  private Collection<FileBasedStorage> getCachedFileStorages(@NotNull Collection<String> fileSpecs) {
    if (fileSpecs.isEmpty()) {
      return Collections.emptyList();
    }

    List<FileBasedStorage> result = null;
    for (String fileSpec : fileSpecs) {
      StateStorage storage = myStorages.get(fileSpec);
      if (storage instanceof FileBasedStorage) {
        if (result == null) {
          result = new SmartList<FileBasedStorage>();
        }
        result.add((FileBasedStorage)storage);
      }
    }
    return result == null ? Collections.<FileBasedStorage>emptyList() : result;
  }

  @NotNull
  @Override
  public Collection<String> getStorageFileNames() {
    myStorageLock.lock();
    try {
      return myStorages.keySet();
    }
    finally {
      myStorageLock.unlock();
    }
  }

  @SuppressWarnings("deprecation")
  @NotNull
  private StateStorage createStateStorage(Storage storageSpec) {
    if (!storageSpec.storageClass().equals(StateStorage.class)) {
      String key = UUID.randomUUID().toString();
      ((MutablePicoContainer)myPicoContainer).registerComponentImplementation(key, storageSpec.storageClass());
      return (StateStorage)myPicoContainer.getComponentInstance(key);
    }
    else if (!storageSpec.stateSplitter().equals(StateSplitter.class) && !storageSpec.stateSplitter().equals(StateSplitterEx.class)) {
      StateSplitter splitter = ReflectionUtil.newInstance(storageSpec.stateSplitter());
      return new DirectoryBasedStorage(myPathMacroSubstitutor, expandMacros(storageSpec.file()), splitter, this, createStorageTopicListener());
    }
    else {
      return createFileStateStorage(storageSpec.file(), storageSpec.roamingType());
    }
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

  @NotNull
  private StateStorage createFileStateStorage(@NotNull String fileSpec, @Nullable RoamingType roamingType) {
    String filePath = expandMacros(fileSpec);

    if (!ourHeadlessEnvironment && PathUtilRt.getFileName(filePath).lastIndexOf('.') < 0) {
      throw new IllegalArgumentException("Extension is missing for storage file: " + filePath);
    }

    if (roamingType == RoamingType.PER_USER && fileSpec.equals(StoragePathMacros.WORKSPACE_FILE)) {
      roamingType = RoamingType.DISABLED;
    }

    beforeFileBasedStorageCreate();
    return new FileBasedStorage(filePath, fileSpec, roamingType, getMacroSubstitutor(fileSpec), myRootTagName, StateStorageManagerImpl.this,
                                createStorageTopicListener(), myStreamProvider) {
      @Override
      @NotNull
      protected StorageData createStorageData() {
        return StateStorageManagerImpl.this.createStorageData(myFileSpec, getFilePath());
      }

      @Override
      protected boolean isUseXmlProlog() {
        return StateStorageManagerImpl.this.isUseXmlProlog();
      }
    };
  }

  @Nullable
  protected StateStorage.Listener createStorageTopicListener() {
    MessageBus messageBus = (MessageBus)myPicoContainer.getComponentInstanceOfType(MessageBus.class);
    return messageBus == null ? null : messageBus.syncPublisher(StateStorage.STORAGE_TOPIC);
  }

  protected boolean isUseXmlProlog() {
    return true;
  }

  protected void beforeFileBasedStorageCreate() {
  }

  @Nullable
  @Override
  public final StreamProvider getStreamProvider() {
    return myStreamProvider;
  }

  protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
    return myPathMacroSubstitutor;
  }

  protected abstract StorageData createStorageData(@NotNull String fileSpec, @NotNull String filePath);

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
  public String collapseMacros(@NotNull String path) {
    String result = path;
    for (String macro : myMacros.keySet()) {
      result = StringUtil.replace(result, myMacros.get(macro), macro);
    }
    return result;
  }

  @NotNull
  @Override
  public ExternalizationSession startExternalization() {
    return new StateStorageManagerExternalizationSession();
  }

  protected class StateStorageManagerExternalizationSession implements ExternalizationSession {
    final Map<StateStorage, StateStorage.ExternalizationSession> mySessions = new LinkedHashMap<StateStorage, StateStorage.ExternalizationSession>();

    @Override
    public void setState(@NotNull Storage[] storageSpecs, @NotNull Object component, @NotNull String componentName, @NotNull Object state) {
      for (Storage storageSpec : storageSpecs) {
        StateStorage stateStorage = getStateStorage(storageSpec);
        StateStorage.ExternalizationSession session = getExternalizationSession(stateStorage);
        if (session != null) {
          // empty element as null state, so, will be deleted
          session.setState(component, componentName, storageSpec.deprecated() ? new Element("empty") : state, storageSpec);
        }
      }
    }

    @Override
    public void setStateInOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull Object state) {
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.WRITE);
      if (stateStorage != null) {
        StateStorage.ExternalizationSession session = getExternalizationSession(stateStorage);
        if (session != null) {
          session.setState(component, componentName, state, null);
        }
      }
    }

    @Nullable
    private StateStorage.ExternalizationSession getExternalizationSession(@NotNull StateStorage stateStorage) {
      StateStorage.ExternalizationSession session = mySessions.get(stateStorage);
      if (session == null) {
        session = stateStorage.startExternalization();
        if (session != null) {
          mySessions.put(stateStorage, session);
        }
      }
      return session;
    }

    @NotNull
    @Override
    public List<SaveSession> createSaveSessions() {
      if (mySessions.isEmpty()) {
        return Collections.emptyList();
      }

      List<SaveSession> saveSessions = null;
      Collection<StateStorage.ExternalizationSession> externalizationSessions = mySessions.values();
      for (StateStorage.ExternalizationSession session : externalizationSessions) {
        SaveSession saveSession = session.createSaveSession();
        if (saveSession != null) {
          if (saveSessions == null) {
            if (externalizationSessions.size() == 1) {
              return Collections.singletonList(saveSession);
            }
            saveSessions = new SmartList<SaveSession>();
          }
          saveSessions.add(saveSession);
        }
      }
      return ContainerUtil.notNullize(saveSessions);
    }
  }

  @Override
  @Nullable
  public StateStorage getOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
    String oldStorageSpec = getOldStorageSpec(component, componentName, operation);
    //noinspection deprecation
    return oldStorageSpec == null ? null : getStateStorage(oldStorageSpec, component instanceof RoamingTypeDisabled ? RoamingType.DISABLED : RoamingType.PER_USER);
  }

  @Nullable
  protected abstract String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation);

  @Override
  public void dispose() {
  }

  @Override
  public void setStreamProvider(@Nullable StreamProvider streamProvider) {
    myStreamProvider = streamProvider;
  }
}
