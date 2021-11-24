// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.DataValidators;
import com.intellij.ide.impl.FreezingDataContext;
import com.intellij.ide.impl.dataRules.FileEditorRule;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.ide.impl.DataManagerImpl.validateEditor;

/**
 * @author gregsh
 */
class PreCachedDataContext implements AsyncDataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier, FreezingDataContext {

  private static int ourPrevMapEventCount;
  private static final Map<Component, Map<String, Object>> ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap();

  private final AtomicReference<KeyFMap> myUserData;
  private final Map<String, Object> myCachedData;
  private final Consumer<? super String> myMissedKeysIfFrozen;

  PreCachedDataContext(@Nullable Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myMissedKeysIfFrozen = null;
    myUserData = new AtomicReference<>(KeyFMap.EMPTY_MAP);

    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      int count = IdeEventQueue.getInstance().getEventCount();
      if (ourPrevMapEventCount != count) {
        ourPrevMaps.clear();
      }
      if (component == null) {
        myCachedData = ContainerUtil.createConcurrentWeakValueMap();
        myCachedData.put(PlatformDataKeys.MODALITY_STATE.getName(), ModalityState.NON_MODAL);
        return;
      }
      Map<String, Object> prevMap = ourPrevMaps.get(component);
      if (prevMap != null) {
        myCachedData = prevMap;
        return;
      }

      myCachedData = new ConcurrentHashMap<>();

      preGetAllData(component, myCachedData);

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourPrevMapEventCount = count;
      ourPrevMaps.put(component, myCachedData);
    }
  }

  private PreCachedDataContext(@NotNull Map<String, Object> cachedData,
                               @NotNull AtomicReference<KeyFMap> userData,
                               @Nullable Consumer<? super String> missedKeys) {
    myCachedData = cachedData;
    myUserData = userData;
    myMissedKeysIfFrozen = missedKeys;
  }

  final @NotNull PreCachedDataContext frozenCopy(@Nullable Consumer<? super String> missedKeys) {
    Consumer<? super String> missedKeysNotNull = missedKeys == null ? s -> { } : missedKeys;
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myCachedData, myUserData, missedKeysNotNull)
           : new PreCachedDataContext(myCachedData, myUserData, missedKeysNotNull);
  }

  @Override
  public final @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this : new InjectedDataContext(myCachedData, myUserData, myMissedKeysIfFrozen);
  }
  
  @Override
  public boolean isFrozenDataContext() {
    return myMissedKeysIfFrozen != null;
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    ProgressManager.checkCanceled();
    Object answer = myCachedData.get(dataId);
    if (answer != null && answer != NullResult.Initial) {
      if (answer == NullResult.Final) return null;
      answer = DataValidators.validOrNull(answer, dataId, this);
      if (answer != null) return answer;
      // allow slow data providers and rules to re-calc the value
    }
    else if (answer == null) {
      // a newly created data key => no data provider => no value
      return null;
    }

    if (myMissedKeysIfFrozen != null) {
      myMissedKeysIfFrozen.accept(dataId);
      return null;
    }

    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    GetDataRule rule = dataManager.getDataRule(dataId);
    answer = rule == null ? null : dataManager.getDataFromProvider(dataId2 -> {
      Object o = dataId2 == dataId ? null : myCachedData.get(dataId2);
      return o == NullResult.Initial || o == NullResult.Final ? null : o;
    }, dataId, null, rule);

    myCachedData.put(dataId, answer == null || answer == NullResult.Initial ? NullResult.Final : answer);
    return answer;
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    Object data = myCachedData.get(dataId);
    return data == NullResult.Initial || data == NullResult.Final ? null : data;
  }

  static void clearAllCaches() {
    for (Map<String, Object> map : ourPrevMaps.values()) {
      map.clear();
    }
    ourPrevMaps.clear();
  }

  private static void preGetAllData(@NotNull Component component, @NotNull Map<String, Object> cachedData) {
    long start = System.currentTimeMillis();
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();

    ArrayList<Object> slowProviders = new ArrayList<>();
    cachedData.put(PlatformCoreDataKeys.CONTEXT_COMPONENT.getName(), component);
    cachedData.put(PlatformDataKeys.MODALITY_STATE.getName(), ModalityState.stateForComponent(component));
    cachedData.put(PlatformCoreDataKeys.IS_MODAL_CONTEXT.getName(), IdeKeyEventDispatcher.isModalContext(component));
    cachedData.put(PlatformCoreDataKeys.SLOW_DATA_PROVIDERS.getName(), slowProviders);

    DataKey<?>[] keys = DataKey.allKeys();
    BitSet computed = new BitSet(keys.length);
    for (Component c = component; c != null; c = c.getParent()) {
      DataProvider dataProvider = getDataProviderEx(c);
      if (dataProvider == null) continue;
      for (int i = 0; i < keys.length; i++) {
        DataKey<?> key = keys[i];
        if (key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
            key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
            key == PlatformDataKeys.MODALITY_STATE) {
          continue;
        }
        boolean alreadyComputed = computed.get(i);
        Object data = !alreadyComputed || key == PlatformCoreDataKeys.SLOW_DATA_PROVIDERS ?
                      dataManager.getDataFromProvider(dataProvider, key.getName(), null, getFastDataRule(key)) : null;
        if (key == CommonDataKeys.EDITOR || key == CommonDataKeys.HOST_EDITOR) data = validateEditor((Editor)data, component);
        if (data == null) continue;

        computed.set(i, true);
        if (key == PlatformCoreDataKeys.SLOW_DATA_PROVIDERS) {
          ContainerUtil.addAll(slowProviders, (Iterable<?>)data);
          continue;
        }
        cachedData.put(key.getName(), data);
      }
    }
    for (int i = 0; i < keys.length; i++) {
      DataKey<?> key = keys[i];
      if (computed.get(i) ||
          key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformDataKeys.MODALITY_STATE ||
          key == PlatformCoreDataKeys.SLOW_DATA_PROVIDERS) {
        continue;
      }
      cachedData.put(key.getName(), NullResult.Initial);
    }
    if (cachedData.get(CommonDataKeys.PROJECT.getName()) == NullResult.Initial) {
      cachedData.put(CommonDataKeys.PROJECT.getName(), NullResult.Final);
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
  }

  private static final GetDataRule ourFileEditorRule = new FileEditorRule();

  private static @Nullable GetDataRule getFastDataRule(@NotNull DataKey<?> key) {
    return key == PlatformCoreDataKeys.FILE_EDITOR ? ourFileEditorRule : null;
  }

  @Override
  public String toString() {
    return (this instanceof InjectedDataContext ? "injected:" : "") +
           (myMissedKeysIfFrozen != null ? "frozen:" : "") +
           "component=" + getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserData.get().get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    while (true) {
      KeyFMap map = myUserData.get();
      KeyFMap newMap = value == null ? map.minus(key) : map.plus(key, value);
      if (newMap == map || myUserData.compareAndSet(map, newMap)) {
        break;
      }
    }
  }

  /**
   * {@link #myCachedData} contains
   * - {@code null} for data keys for which the corresponding {@link #getData(String)} was never called (E.g. for {@link DataKey}s created dynamically during other {@link #getData(String)} execution);
   * - {@link NullResult#Initial} for data keys which returned {@code null} from the corresponding {@link #getData(String)} during {@link #PreCachedDataContext(Component)} execution;
   * - {@link NullResult#Final} for data keys which returned {@code null} from both {@link #getData(String)} invocations: in constructor and after all data rules execution
   */
  private enum NullResult {
    Initial, Final
  }

  private static class InjectedDataContext extends PreCachedDataContext {
    InjectedDataContext(@NotNull Map<String, Object> cachedData,
                        @NotNull AtomicReference<KeyFMap> userData,
                        @Nullable Consumer<? super String> missedKeys) {
      super(cachedData, userData, missedKeys);
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      Object injected = injectedId != null ? super.getData(injectedId) : null;
      return injected != null ? injected : super.getData(dataId);
    }
  }
}