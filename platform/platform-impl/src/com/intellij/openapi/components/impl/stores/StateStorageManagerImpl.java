/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.StateStorageChooserEx.Resolution;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StateStorageManagerImpl implements StateStorageManager, Disposable {
  private static final Logger LOG = Logger.getInstance(StateStorageManagerImpl.class);

  private final Map<String, String> myMacros = new LinkedHashMap<String, String>();
  private final Lock myStorageLock = new ReentrantLock();
  private final Map<String, StateStorage> myStorages = new THashMap<String, StateStorage>();
  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  private final String myRootTagName;
  private final PicoContainer myPicoContainer;

  private StreamProvider myStreamProvider;

  public StateStorageManagerImpl(@NotNull TrackingPathMacroSubstitutor pathMacroSubstitutor,
                                 @NotNull String rootTagName,
                                 @NotNull Disposable parentDisposable,
                                 @NotNull PicoContainer picoContainer) {
    myPicoContainer = picoContainer;
    myRootTagName = rootTagName;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    Disposer.register(parentDisposable, this);
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
    String key = storageSpec.storageClass().equals(StateStorage.class) ? storageSpec.file() : storageSpec.storageClass().getName();

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
        stateStorage = createStateStorage(StateStorage.class, fileSpec, roamingType, StateSplitterEx.class);
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
    Class<? extends StateStorage> storageClass = storageSpec.storageClass();
    String fileSpec = storageSpec.file();
    RoamingType roamingType = storageSpec.roamingType();
    Class<? extends StateSplitter> stateSplitter = storageSpec.stateSplitter();
    return createStateStorage(storageClass, fileSpec, roamingType, stateSplitter);
  }

  // overridden in upsource
  protected StateStorage createStateStorage(@NotNull Class<? extends StateStorage> storageClass,
                                            @NotNull String fileSpec,
                                            @NotNull RoamingType roamingType,
                                            @NotNull Class<? extends StateSplitter> stateSplitter) {
    if (!storageClass.equals(StateStorage.class)) {
      String key = UUID.randomUUID().toString();
      ((MutablePicoContainer)myPicoContainer).registerComponentImplementation(key, storageClass);
      return (StateStorage)myPicoContainer.getComponentInstance(key);
    }
    final String filePath = expandMacros(fileSpec);
    File file = new File(filePath).getAbsoluteFile();

    if (!stateSplitter.equals(StateSplitter.class) && !stateSplitter.equals(StateSplitterEx.class)) {
      StateSplitter splitter = ReflectionUtil.newInstance(stateSplitter);
      return new DirectoryBasedStorage(myPathMacroSubstitutor, file, splitter, this, createStorageTopicListener());
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && PathUtilRt.getFileName(filePath).lastIndexOf('.') < 0) {
      throw new IllegalArgumentException("Extension is missing for storage file: " + filePath);
    }

    if (roamingType == RoamingType.PER_USER && fileSpec.equals(StoragePathMacros.WORKSPACE_FILE)) {
      roamingType = RoamingType.DISABLED;
    }

    beforeFileBasedStorageCreate();
    return new FileBasedStorage(file, fileSpec,
                                roamingType, getMacroSubstitutor(fileSpec), myRootTagName, StateStorageManagerImpl.this,
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

  @NotNull
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
    for (Map.Entry<String, String> entry : myMacros.entrySet()) {
      expanded = StringUtil.replace(expanded, entry.getKey(), entry.getValue());
    }
    return expanded;
  }

  @NotNull
  @Override
  public String collapseMacros(@NotNull String path) {
    String result = path;
    for (Map.Entry<String, String> entry : myMacros.entrySet()) {
      result = StringUtil.replace(result, entry.getValue(), entry.getKey());
    }
    return result;
  }

  @NotNull
  @Override
  public ExternalizationSession startExternalization() {
    return new StateStorageManagerExternalizationSession();
  }

  protected class StateStorageManagerExternalizationSession implements ExternalizationSession {
    private final Map<StateStorage, StateStorage.ExternalizationSession> mySessions = new LinkedHashMap<StateStorage, StateStorage.ExternalizationSession>();

    @Override
    public void setState(@NotNull Storage[] storageSpecs, @NotNull Object component, @NotNull String componentName, @NotNull Object state) {
      StateStorageChooserEx stateStorageChooser = component instanceof StateStorageChooserEx ? (StateStorageChooserEx)component : null;
      for (Storage storageSpec : storageSpecs) {
        Resolution resolution = stateStorageChooser == null ? Resolution.DO : stateStorageChooser.getResolution(storageSpec, StateStorageOperation.WRITE);
        if (resolution == Resolution.SKIP) {
          continue;
        }

        StateStorage stateStorage = getStateStorage(storageSpec);
        StateStorage.ExternalizationSession session = getExternalizationSession(stateStorage);
        if (session != null) {
          // empty element as null state, so, will be deleted
          session.setState(component, componentName, storageSpec.deprecated() || resolution == Resolution.CLEAR ? new Element("empty") : state, storageSpec);
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
    protected final StateStorage.ExternalizationSession getExternalizationSession(@NotNull StateStorage stateStorage) {
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
