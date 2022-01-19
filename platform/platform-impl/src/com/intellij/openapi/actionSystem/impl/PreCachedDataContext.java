// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.UnsafeWeakList;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;

/**
 * @author gregsh
 */
class PreCachedDataContext implements AsyncDataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier, FreezingDataContext {

  private static final Logger LOG = Logger.getInstance(PreCachedDataContext.class);

  private static int ourPrevMapEventCount;
  private static final Map<Component, FList<ProviderData>> ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap();
  private static final Collection<PreCachedDataContext> ourInstances = new UnsafeWeakList<>();
  private static final Map<String, Integer> ourDataKeysIndices = new ConcurrentHashMap<>();
  private static final AtomicInteger ourDataKeysCount = new AtomicInteger();
  private static final Object ourExplicitNull = ObjectUtils.sentinel("explicit.null");

  private final ComponentRef myComponentRef;
  private final AtomicReference<KeyFMap> myUserData;
  private final FList<ProviderData> myCachedData;
  private final Consumer<? super String> myMissedKeysIfFrozen;
  private final int myDataKeysCount;

  PreCachedDataContext(@Nullable Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myComponentRef = new ComponentRef(component);
    myMissedKeysIfFrozen = null;
    myUserData = new AtomicReference<>(KeyFMap.EMPTY_MAP);
    if (component == null) {
      myCachedData = FList.emptyList();
      myDataKeysCount = 0;
      return;
    }

    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      int count = ActivityTracker.getInstance().getCount();
      if (ourPrevMapEventCount != count) {
        ourPrevMaps.clear();
      }
      List<Component> components = ContainerUtil.reverse(
        UIUtil.uiParents(component, false).takeWhile(o -> ourPrevMaps.get(o) == null).toList());
      Component topParent = components.isEmpty() ? component : components.get(0).getParent();
      FList<ProviderData> initial = topParent == null ? FList.emptyList() : ourPrevMaps.get(topParent);

      if (components.isEmpty()) {
        myCachedData = initial;
        myDataKeysCount = ourDataKeysIndices.size();
      }
      else {
        DataKey<?>[] keys = DataKey.allKeys();
        myDataKeysCount = keys.length;
        if (ourDataKeysIndices.size() < myDataKeysCount) {
          for (DataKey<?> key : keys) {
            ourDataKeysIndices.computeIfAbsent(key.getName(), __ -> ourDataKeysCount.getAndIncrement());
          }
        }
        myCachedData = preGetAllData(components, initial, keys);
        ourInstances.add(this);
      }
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourPrevMapEventCount = count;
    }
  }

  private PreCachedDataContext(@NotNull ComponentRef compRef,
                               @NotNull FList<ProviderData> cachedData,
                               @NotNull AtomicReference<KeyFMap> userData,
                               @Nullable Consumer<? super String> missedKeys,
                               int dataKeysCount) {
    myComponentRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myMissedKeysIfFrozen = missedKeys;
    myDataKeysCount = dataKeysCount;
  }

  final @NotNull PreCachedDataContext frozenCopy(@Nullable Consumer<? super String> missedKeys) {
    Consumer<? super String> missedKeysNotNull = missedKeys == null ? s -> { } : missedKeys;
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myComponentRef, myCachedData, myUserData, missedKeysNotNull, myDataKeysCount)
           : new PreCachedDataContext(myComponentRef, myCachedData, myUserData, missedKeysNotNull, myDataKeysCount);
  }

  @Override
  public final @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this :
           new InjectedDataContext(myComponentRef, myCachedData, myUserData, myMissedKeysIfFrozen, myDataKeysCount);
  }

  @Override
  public boolean isFrozenDataContext() {
    return myMissedKeysIfFrozen != null;
  }

  @NotNull PreCachedDataContext prependProvider(@NotNull DataProvider dataProvider) {
    DataKey<?>[] keys = DataKey.allKeys();
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    ProviderData cachedData = new ProviderData();
    Component component = SoftReference.dereference(myComponentRef.ref);
    doPreGetAllData(dataProvider, cachedData, component, dataManager, keys, myCachedData.getHead());
    return new PreCachedDataContext(myComponentRef, myCachedData.prepend(cachedData),
                                    new AtomicReference<>(KeyFMap.EMPTY_MAP), myMissedKeysIfFrozen, myDataKeysCount);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) return SoftReference.dereference(myComponentRef.ref);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) return myComponentRef.modalContext;
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) return myComponentRef.modalityState;
    if (myCachedData.isEmpty()) return null;

    int keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1);
    if (keyIndex == -1) return null; // a newly created data key => no data provider => no value

    boolean rulesSuppressed = EDT.isCurrentThreadEdt() && Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt");
    boolean rulesAllowed = myMissedKeysIfFrozen == null && !CommonDataKeys.PROJECT.is(dataId) && keyIndex < myDataKeysCount && !rulesSuppressed;
    DataManagerImpl dataManager = null;
    GetDataRule rule = null;
    Object answer = null;
    for (ProviderData map : myCachedData) {
      ProgressManager.checkCanceled();
      answer = map.get(dataId);
      if (answer == ourExplicitNull) break;
      if (answer != null) {
        answer = DataValidators.validOrNull(answer, dataId, this);
        if (answer != null) break;
        // allow slow data providers and rules to re-calc the value
        map.remove(dataId);
      }
      if (!rulesAllowed || map.nullsByRules.get(keyIndex)) continue;

      if (dataManager == null) {
        dataManager = (DataManagerImpl)DataManager.getInstance();
        rule = dataManager.getDataRule(dataId);
      }
      answer = rule == null ? null : dataManager.getDataFromProvider(dataId2 -> {
        Object o = dataId2 == dataId ? null : map.get(dataId2);
        return o == ourExplicitNull ? null : o;
      }, dataId, null, rule);

      if (answer == null) map.nullsByRules.set(keyIndex);
      else map.put(dataId, answer);
      if (answer != null) break;
    }
    if (myMissedKeysIfFrozen != null && answer == null) {
      myMissedKeysIfFrozen.accept(dataId);
      return null;
    }
    if (answer == null && rulesSuppressed) {
      Throwable throwable = new Throwable();
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        if (ReadAction.compute(() -> getData(dataId)) != null) {
          LOG.warn(dataId + " is not available on EDT. " +
                   "Code that depends on data rules and slow data providers must be run in background. " +
                   "For example, an action must be `UpdateInBackground`.", throwable);
        }
      });
    }
    return answer == ourExplicitNull ? null : answer;
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    for (ProviderData map : myCachedData) {
      Object answer = map.get(dataId);
      if (answer != null) {
        return answer;
      }
    }
    return null;
  }

  static void clearAllCaches() {
    for (FList<ProviderData> list : ourPrevMaps.values()) {
      for (ProviderData map : list) map.clear();
    }
    ourPrevMaps.clear();
    for (PreCachedDataContext context : ourInstances) {
      for (ProviderData map : context.myCachedData) map.clear();
    }
    ourInstances.clear();
  }

  private static @NotNull FList<ProviderData> preGetAllData(@NotNull List<Component> components,
                                                            @NotNull FList<ProviderData> initial,
                                                            DataKey<?> @NotNull [] keys) {
    DataManagerImpl dataManager = (DataManagerImpl)DataManager.getInstance();
    FList<ProviderData> result = initial;

    long start = System.currentTimeMillis();
    for (Component comp : components) {
      DataProvider dataProvider = getDataProviderEx(comp);
      if (dataProvider == null && hideEditor(comp)) dataProvider = dataId -> null;
      if (dataProvider == null) continue;
      ProviderData cachedData = new ProviderData();
      doPreGetAllData(dataProvider, cachedData, comp, dataManager, keys, result.getHead());
      result = result.prepend(cachedData);
      ourPrevMaps.put(comp, result);
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
    return result;
  }

  private static void doPreGetAllData(@NotNull DataProvider dataProvider,
                                      @NotNull ProviderData cachedData,
                                      @Nullable Component c,
                                      @NotNull DataManagerImpl dataManager,
                                      DataKey<?> @NotNull [] keys,
                                      @Nullable Map<String, Object> parentMap) {
    boolean hideEditor = hideEditor(c);
    for (DataKey<?> key : keys) {
      if (key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformDataKeys.MODALITY_STATE ||
          key == PlatformCoreDataKeys.SLOW_DATA_PROVIDERS) {
        continue;
      }
      Object data = hideEditor && (key == CommonDataKeys.EDITOR || key == CommonDataKeys.HOST_EDITOR) ? ourExplicitNull :
                    dataManager.getDataFromProvider(dataProvider, key.getName(), null, getFastDataRule(key));
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

  private static boolean hideEditor(@Nullable Component component) {
    return component instanceof JComponent &&
           ((JComponent)component).getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null;
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

  private static class InjectedDataContext extends PreCachedDataContext {
    InjectedDataContext(@NotNull ComponentRef compRef,
                        @NotNull FList<ProviderData> cachedData,
                        @NotNull AtomicReference<KeyFMap> userData,
                        @Nullable Consumer<? super String> missedKeys,
                        int dataKeysCount) {
      super(compRef, cachedData, userData, missedKeys, dataKeysCount);
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      Object injected = injectedId != null ? super.getData(injectedId) : null;
      return injected != null ? injected : super.getData(dataId);
    }
  }

  private static class ProviderData extends ConcurrentHashMap<String, Object> {
    final ConcurrentBitSet nullsByRules = ConcurrentBitSet.create();
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