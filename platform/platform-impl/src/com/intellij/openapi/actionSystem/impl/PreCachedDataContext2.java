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
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.ide.impl.DataManagerImpl.validateEditor;

/**
 * @author gregsh
 */
class PreCachedDataContext2 implements AsyncDataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier, FreezingDataContext {

  private static int ourPrevMapEventCount;
  private static final Map<Component, FList<Map<String, Object>>> ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap();
  private static final Object ourNull = ObjectUtils.sentinel("ourNull");

  private final ComponentRef myComponentRef;
  private final AtomicReference<KeyFMap> myUserData;
  private final FList<Map<String, Object>> myCachedData;
  private final Consumer<? super String> myMissedKeysIfFrozen;

  PreCachedDataContext2(@Nullable Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myComponentRef = new ComponentRef(component);
    myMissedKeysIfFrozen = null;
    myUserData = new AtomicReference<>(KeyFMap.EMPTY_MAP);
    if (component == null) {
      myCachedData = FList.emptyList();
      return;
    }

    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      int count = IdeEventQueue.getInstance().getEventCount();
      if (ourPrevMapEventCount != count) {
        ourPrevMaps.clear();
      }
      myCachedData = preGetAllData(component);

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourPrevMapEventCount = count;
    }
  }

  private PreCachedDataContext2(@NotNull ComponentRef compRef,
                               @NotNull FList<Map<String, Object>> cachedData,
                               @NotNull AtomicReference<KeyFMap> userData,
                               @Nullable Consumer<? super String> missedKeys) {
    myComponentRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myMissedKeysIfFrozen = missedKeys;
  }

  final @NotNull PreCachedDataContext2 frozenCopy(@Nullable Consumer<? super String> missedKeys) {
    Consumer<? super String> missedKeysNotNull = missedKeys == null ? s -> {
    } : missedKeys;
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myComponentRef, myCachedData, myUserData, missedKeysNotNull)
           : new PreCachedDataContext2(myComponentRef, myCachedData, myUserData, missedKeysNotNull);
  }

  @Override
  public final @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext
           ? this
           : new InjectedDataContext(myComponentRef, myCachedData, myUserData, myMissedKeysIfFrozen);
  }

  @Override
  public boolean isFrozenDataContext() {
    return myMissedKeysIfFrozen != null;
  }

  @NotNull PreCachedDataContext2 prependProvider(@NotNull DataProvider dataProvider) {
    DataKey<?>[] keys = DataKey.allKeys();
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    ConcurrentHashMap<String, Object> cachedData = new ConcurrentHashMap<>();
    Component component = myComponentRef.ref.get();
    doPreGetAllData(dataProvider, cachedData, component, dataManager, keys, myCachedData.getHead());
    return new PreCachedDataContext2(myComponentRef, myCachedData.prepend(cachedData),
                                    new AtomicReference<>(KeyFMap.EMPTY_MAP), myMissedKeysIfFrozen);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) return myComponentRef.ref.get();
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) return myComponentRef.modalContext;
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) return myComponentRef.modalityState;

    boolean rulesAllowed = myMissedKeysIfFrozen == null && !CommonDataKeys.PROJECT.is(dataId) &&
                           (!EDT.isCurrentThreadEdt() || SlowOperations.isInsideActivity(SlowOperations.ACTION_PERFORM));
    DataManagerImpl dataManager = null;
    GetDataRule rule = null;
    Object answer = null;
    for (Map<String, Object> map : myCachedData) {
      ProgressManager.checkCanceled();
      answer = map.get(dataId);
      if (answer == ourNull) continue;
      if (answer != null) {
        answer = DataValidators.validOrNull(answer, dataId, this);
        if (answer != null) break;
        // allow slow data providers and rules to re-calc the value
      }
      if (!rulesAllowed) continue;

      if (dataManager == null) {
        dataManager = (DataManagerImpl)DataManager.getInstance();
        rule = dataManager.getDataRule(dataId);
      }
      answer = rule == null ? null : dataManager.getDataFromProvider(dataId2 -> {
        Object o = dataId2 == dataId ? null : map.get(dataId2);
        return o == ourNull ? null : o;
      }, dataId, null, rule);

      map.put(dataId, answer == null ? ourNull : answer);
      if (answer != null) break;
    }
    if (myMissedKeysIfFrozen != null && answer == null) {
      myMissedKeysIfFrozen.accept(dataId);
      return null;
    }
    return answer == null || answer == ourNull ? null : answer;
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    for (Map<String, Object> map : myCachedData) {
      Object answer = map.get(dataId);
      if (answer != null && answer != ourNull) {
        return answer;
      }
    }
    return null;
  }

  static {
    for (KeyedLazyInstance<GetDataRule> instance : GetDataRule.EP_NAME.getExtensionList()) {
      DataKey.create(instance.getKey()); // initialize data keys with rules
    }
  }

  static void clearAllCaches() {
    for (FList<Map<String, Object>> list : ourPrevMaps.values()) {
      for (Map<String, Object> map : list) {
        map.clear();
      }
    }
    ourPrevMaps.clear();
  }

  private static @NotNull FList<Map<String, Object>> preGetAllData(@NotNull Component component) {
    long start = System.currentTimeMillis();
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();

    List<Component> components = ContainerUtil.reverse(
      UIUtil.uiParents(component, false).takeWhile(o -> ourPrevMaps.get(o) == null).toList());
    Component topParent = components.isEmpty() ? component : components.get(0).getParent();
    FList<Map<String, Object>> result = topParent == null ? FList.emptyList() : ourPrevMaps.get(topParent);

    if (components.isEmpty()) return result;

    DataKey<?>[] keys = DataKey.allKeys();
    for (Component c : components) {
      DataProvider dataProvider = getDataProviderEx(c);
      if (dataProvider == null) continue;
      ConcurrentMap<String, Object> cachedData = new ConcurrentHashMap<>();
      doPreGetAllData(dataProvider, cachedData, c, dataManager, keys, result.getHead());
      result = result.prepend(cachedData);
      ourPrevMaps.put(component, result);
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
    return result;
  }

  private static void doPreGetAllData(@NotNull DataProvider dataProvider,
                                      @NotNull ConcurrentMap<String, Object> cachedData,
                                      @Nullable Component c,
                                      @NotNull DataManagerImpl dataManager,
                                      DataKey<?> @NotNull [] keys,
                                      @Nullable Map<String, Object> parentMap) {
    for (DataKey<?> key : keys) {
      if (key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformDataKeys.MODALITY_STATE ||
          key == PlatformCoreDataKeys.SLOW_DATA_PROVIDERS) {
        continue;
      }
      Object data = dataManager.getDataFromProvider(dataProvider, key.getName(), null, getFastDataRule(key));
      if (key == CommonDataKeys.EDITOR || key == CommonDataKeys.HOST_EDITOR) data = validateEditor((Editor)data, c);
      if (data == null) continue;
      cachedData.put(key.getName(), data);
    }
    String slowProvidersKeyName = PlatformCoreDataKeys.SLOW_DATA_PROVIDERS.getName();
    Object slowProviders = dataManager.getDataFromProvider(dataProvider, slowProvidersKeyName, null, null);
    if (slowProviders != null) {
      Object parentProviders = parentMap == null ? null : parentMap.get(slowProvidersKeyName);
      cachedData.put(slowProvidersKeyName, parentProviders == null ? slowProviders :
                                           ContainerUtil.concat((Iterable<?>)slowProviders, (Iterable<?>)parentProviders));
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

  private static class InjectedDataContext extends PreCachedDataContext2 {
    InjectedDataContext(@NotNull ComponentRef compRef,
                        @NotNull FList<Map<String, Object>> cachedData,
                        @NotNull AtomicReference<KeyFMap> userData,
                        @Nullable Consumer<? super String> missedKeys) {
      super(compRef, cachedData, userData, missedKeys);
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      Object injected = injectedId != null ? super.getData(injectedId) : null;
      return injected != null ? injected : super.getData(dataId);
    }
  }

  private static class ComponentRef {
    final Reference<Component> ref;
    final ModalityState modalityState;
    final Boolean modalContext;

    ComponentRef(@Nullable Component component) {
      ref = component == null ? null : new WeakReference<>(component);
      modalityState = component == null ? ModalityState.NON_MODAL : ModalityState.stateForComponent(component);
      modalContext = component == null ? null : IdeKeyEventDispatcher.isModalContext(component);
    }
  }
}