// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.ide.impl.DataManagerImpl.validateEditor;

/**
 * @author gregsh
 */
class PreCachedDataContext implements DataContext, UserDataHolder {

  private static int ourPrevMapEventCount;
  private static final Map<Component, Map<String, Object>> ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap();

  private Map<Key<?>, Object> myUserData;
  private final Map<String, Object> myCachedData;
  private final Consumer<? super String> myMissedKeysIfFrozen;

  PreCachedDataContext(@NotNull DataContext original) {
    if (!(original instanceof DataManagerImpl.MyDataContext)) {
      throw new AssertionError(original.getClass().getName());
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    Component component = original.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    myMissedKeysIfFrozen = null;

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
                               @NotNull Map<Key<?>, Object> userData,
                               @NotNull Consumer<? super String> missedKeys) {
    myCachedData = cachedData;
    myUserData = userData;
    myMissedKeysIfFrozen = missedKeys;
  }

  @NotNull
  PreCachedDataContext frozenCopy(@Nullable Consumer<? super String> missedKeys) {
    return new PreCachedDataContext(myCachedData, getOrCreateMap(), missedKeys == null ? s -> {} : missedKeys);
  }

  @Override
  public Object getData(@NotNull String dataId) {
    ProgressManager.checkCanceled();
    Object answer = myCachedData.get(dataId);
    if (answer != null && answer != NullResult.Initial) {
      return answer == NullResult.Final ? null : answer;
    }
    if (myMissedKeysIfFrozen != null) {
      myMissedKeysIfFrozen.accept(dataId);
      return null;
    }

    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    GetDataRule rule = dataManager.getDataRule(dataId);
    answer = rule == null ? null : dataManager.getDataFromProvider(dataId2 -> {
      Object o = myCachedData.get(dataId2);
      return o == NullResult.Initial || o == NullResult.Final ? null : o;
    }, dataId, null, rule);

    myCachedData.put(dataId, answer == null || answer == NullResult.Initial ? NullResult.Final : answer);
    return answer;
  }

  private static void preGetAllData(@NotNull Component component, @NotNull Map<String, Object> cachedData) {
    long start = System.currentTimeMillis();
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();

    ArrayList<Object> slowProviders = new ArrayList<>();
    cachedData.put(PlatformDataKeys.CONTEXT_COMPONENT.getName(), component);
    cachedData.put(PlatformDataKeys.MODALITY_STATE.getName(), ModalityState.stateForComponent(component));
    cachedData.put(PlatformDataKeys.IS_MODAL_CONTEXT.getName(), IdeKeyEventDispatcher.isModalContext(component));
    cachedData.put(PlatformDataKeys.SLOW_DATA_PROVIDERS.getName(), slowProviders);

    // ignore injected data keys, injections are slow,
    // and slow parts must be in a slow provider anyway
    DataKey<?>[] keys = DataKey.allKeys();
    BitSet computed = new BitSet(keys.length);
    for (Component c = component; c != null; c = c.getParent()) {
      DataProvider dataProvider = getDataProviderEx(c);
      if (dataProvider == null) continue;
      for (int i = 0; i < keys.length; i++) {
        DataKey<?> key = keys[i];
        boolean alreadyComputed = computed.get(i);
        if (key == PlatformDataKeys.IS_MODAL_CONTEXT ||
            key == PlatformDataKeys.CONTEXT_COMPONENT ||
            key == PlatformDataKeys.MODALITY_STATE) {
          if (!alreadyComputed) computed.set(i, true);
          continue;
        }
        Object data = !alreadyComputed || key == PlatformDataKeys.SLOW_DATA_PROVIDERS ?
                      dataManager.getDataFromProvider(dataProvider, key.getName(), null, null) : null;
        if (data instanceof Editor) data = validateEditor((Editor)data, component);
        if (data == null) continue;

        computed.set(i, true);
        if (key == PlatformDataKeys.SLOW_DATA_PROVIDERS) {
          ContainerUtil.addAll(slowProviders, (Iterable<?>)data);
          continue;
        }
        cachedData.put(key.getName(), data);
      }
    }
    for (int i = 0; i < keys.length; i++) {
      DataKey<?> key = keys[i];
      if (!computed.get(i)) {
        cachedData.put(key.getName(), NullResult.Initial);
      }
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
  }

  @Override
  public String toString() {
    return "component=" + getData(PlatformDataKeys.CONTEXT_COMPONENT);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)getOrCreateMap().get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    getOrCreateMap().put(key, value);
  }

  private @NotNull Map<Key<?>, Object> getOrCreateMap() {
    Map<Key<?>, Object> userData = myUserData;
    if (userData == null) {
      myUserData = userData = ContainerUtil.createWeakValueMap();
    }
    return userData;
  }

  private enum NullResult {
    Initial, Final
  }
}
