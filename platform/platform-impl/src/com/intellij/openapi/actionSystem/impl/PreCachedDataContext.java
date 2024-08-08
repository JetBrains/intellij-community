// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.DataValidators;
import com.intellij.ide.impl.GetDataRuleType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.*;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.openapi.actionSystem.CustomizedDataContext.EXPLICIT_NULL;
import static com.intellij.openapi.actionSystem.impl.EdtDataContextKt.wrapUnsafeData;
import static com.intellij.reference.SoftReference.dereference;

/**
 * @author gregsh
 */
class PreCachedDataContext implements AsyncDataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier {

  private static final Logger LOG = Logger.getInstance(PreCachedDataContext.class);

  private static int ourPrevMapEventCount;
  private static final Map<Component, FList<ProviderData>> ourPrevMaps = ContainerUtil.createWeakKeySoftValueMap();
  private static final Set<Component> ourComponents = ContainerUtil.createWeakSet();
  private static final Collection<PreCachedDataContext> ourInstances = new UnsafeWeakList<>();
  private static final Map<String, Integer> ourDataKeysIndices = new ConcurrentHashMap<>();
  private static final AtomicInteger ourDataKeysCount = new AtomicInteger();
  private static final Interner<String> ourEDTWarnsInterner = Interner.createStringInterner();
  private static boolean ourIsCapturingSnapshot;

  private final ComponentRef myComponentRef;
  private final AtomicReference<KeyFMap> myUserData;
  private final FList<ProviderData> myCachedData;
  private final DataManagerImpl myDataManager;
  private final int myDataKeysCount;

  /** @noinspection AssignmentToStaticFieldFromInstanceMethod*/
  PreCachedDataContext(@Nullable Component component) {
    myComponentRef = new ComponentRef(component);
    myUserData = new AtomicReference<>(KeyFMap.EMPTY_MAP);
    myDataManager = (DataManagerImpl)DataManager.getInstance();
    if (component == null) {
      myCachedData = FList.emptyList();
      myDataKeysCount = DataKey.allKeysCount();
      return;
    }

    ThreadingAssertions.assertEventDispatchThread();
    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      int count = ActivityTracker.getInstance().getCount();
      if (ourPrevMapEventCount != count ||
          ourDataKeysIndices.size() != DataKey.allKeysCount() ||
          ApplicationManager.getApplication().isUnitTestMode()) {
        ourPrevMaps.clear();
        ourComponents.clear();
      }
      List<Component> components = FList.createFromReversed(
        JBIterable.generate(component, UIUtil::getParent).takeWhile(o -> {
          FList<ProviderData> list = ourPrevMaps.get(o);
          if (list == null) return true;
          // make sure we run edt rules on the current component
          return o == component && !ourComponents.contains(o);
        }));
      Component topParent = components.isEmpty() ? component : UIUtil.getParent(components.get(0));
      FList<ProviderData> initial = topParent == null ? FList.emptyList() : ourPrevMaps.get(topParent);

      if (components.isEmpty()) {
        myCachedData = initial;
        myDataKeysCount = ourDataKeysIndices.size();
      }
      else {
        int keyCount;
        FList<ProviderData> cachedData;
        MySink sink = new MySink();
        ourIsCapturingSnapshot = true;
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.FORCE_ASSERT)) {
          while (true) {
            sink.keys = null;
            cachedData = cacheComponentsData(sink, components, initial, myDataManager);
            cachedData = runSnapshotRules(sink, component, cachedData);
            keyCount = sink.keys == null ? DataKey.allKeysCount() : sink.keys.length;
            // retry if providers add new keys
            if (keyCount == DataKey.allKeysCount()) break;
          }
        }
        finally {
          ourIsCapturingSnapshot = false;
        }
        myDataKeysCount = keyCount;
        myCachedData = cachedData;
        ourInstances.add(this);
        ourComponents.add(component);
      }
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourPrevMapEventCount = count;
    }
  }

  private PreCachedDataContext(@NotNull ComponentRef compRef,
                               @NotNull FList<ProviderData> cachedData,
                               @NotNull AtomicReference<KeyFMap> userData,
                               @NotNull DataManagerImpl dataManager,
                               int dataKeysCount) {
    myComponentRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myDataManager = dataManager;
    myDataKeysCount = dataKeysCount;
  }

  boolean cachesAllKnownDataKeys() {
    return myDataKeysCount == DataKey.allKeysCount();
  }

  @Override
  public final @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this :
           new InjectedDataContext(myComponentRef, myCachedData, myUserData, myDataManager, myDataKeysCount);
  }

  @NotNull PreCachedDataContext prependProvider(@Nullable Object dataProvider) {
    if (dataProvider == null) return this;
    boolean isEDT = EDT.isCurrentThreadEdt();
    int keyCount;
    FList<ProviderData> cachedData;
    MySink sink = new MySink();
    while (true) {
      Component component = dereference(myComponentRef.ref);
      sink.keys = null;
      sink.hideEditor = hideEditor(component);
      cacheProviderData(sink, dataProvider, myDataManager);
      cachedData = sink.map == null ? myCachedData : myCachedData.prepend(sink.map);
      // do not provide CONTEXT_COMPONENT in BGT
      cachedData = runSnapshotRules(sink, isEDT ? component : null, cachedData);
      keyCount = sink.keys == null ? DataKey.allKeysCount() : sink.keys.length;
      // retry if the provider adds new keys
      if (keyCount == DataKey.allKeysCount()) break;
    }
    AtomicReference<KeyFMap> userData = new AtomicReference<>(KeyFMap.EMPTY_MAP);
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myComponentRef, cachedData, userData, myDataManager, keyCount)
           : new PreCachedDataContext(myComponentRef, cachedData, userData, myDataManager, keyCount);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    //noinspection DuplicatedCode
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) return dereference(myComponentRef.ref);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) return myComponentRef.modalContext;
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) return myComponentRef.modalityState;
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId) && myComponentRef.speedSearchText != null) return myComponentRef.speedSearchText;
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId) && myComponentRef.speedSearchRef != null) return myComponentRef.speedSearchRef.get();
    if (myCachedData.isEmpty()) return null;

    boolean isEDT = EDT.isCurrentThreadEdt();
    if (isEDT && ourIsCapturingSnapshot) {
      reportGetDataInsideCapturingSnapshot();
    }
    boolean noRulesSection = isEDT && ActionUpdater.Companion.isNoRulesInEDTSection();
    boolean rulesSuppressed = isEDT && LoadingState.COMPONENTS_LOADED.isOccurred() && Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt");
    boolean rulesAllowed = !CommonDataKeys.PROJECT.is(dataId) && !rulesSuppressed && !noRulesSection;
    Object answer = getDataInner(dataId, rulesAllowed, !noRulesSection);

    int keyIndex; // for use with `nullsByContextRules` only, always != -1
    ProviderData map = myCachedData.get(0);
    if (answer == null && rulesAllowed && !map.nullsByContextRules.get(keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1))) {
      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.CONTEXT, id -> {
        return getDataInner(id, !CommonDataKeys.PROJECT.is(id), true);
      });
      if (answer != null) {
        map.computedData.put(dataId, answer);
        map.nullsByRules.clear(keyIndex);
        reportValueProvidedByRules(dataId);
      }
      else {
        map.nullsByContextRules.set(keyIndex);
      }
    }
    if (answer == null && rulesSuppressed) {
      Throwable throwable = new Throwable();
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        if (ReadAction.compute(() -> getData(dataId)) != null) {
          LOG.warn(dataId + " is not available on EDT. " +
                   "Code that depends on data rules and slow data providers must be run in background. " +
                   "For example, an action must use `ActionUpdateThread.BGT`.", throwable);
        }
      });
    }
    answer = wrapUnsafeData(answer);
    return answer == EXPLICIT_NULL ? null : answer;
  }

  protected @Nullable Object getDataInner(@NotNull String dataId, boolean rulesAllowed, boolean ruleValuesAllowed) {
    int keyIndex = getDataKeyIndex(dataId);
    if (keyIndex == -1) return EXPLICIT_NULL; // DataKey not found

    Object answer = null;
    for (ProviderData map : myCachedData) {
      ProgressManager.checkCanceled();
      boolean isComputed = false;
      answer = map.uiSnapshot.get(dataId);
      if (answer == null) {
        answer = map.computedData.get(dataId);
        isComputed = true;
      }
      if (answer == EXPLICIT_NULL) break;
      if (answer != null) {
        if (isComputed) {
          reportValueProvidedByRulesUsage(dataId, !ruleValuesAllowed);
          if (!ruleValuesAllowed) return null;
        }
        answer = DataValidators.validOrNull(answer, dataId, this);
        if (answer != null) break;
        if (!isComputed) return null;
        // allow slow data providers and rules to re-calc the value
        map.computedData.remove(dataId);
      }
      if (!rulesAllowed || map.nullsByRules.get(keyIndex)) continue;

      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.PROVIDER, id -> {
        if (Objects.equals(id, dataId)) return null;
        Object o = map.uiSnapshot.get(id);
        return o != null ? o : map.computedData.get(id);
      });

      if (answer == null) {
        map.nullsByRules.set(keyIndex);
      }
      else {
        map.computedData.put(dataId, answer);
        reportValueProvidedByRules(dataId);
        break;
      }
    }
    return answer;
  }

  private static void reportGetDataInsideCapturingSnapshot() {
    LOG.error("DataContext must not be queried during another DataContext creation");
  }

  private static void reportValueProvidedByRulesUsage(@NotNull String dataId, boolean error) {
    if (!Registry.is("actionSystem.update.actions.warn.dataRules.on.edt")) return;
    if (EDT.isCurrentThreadEdt() && SlowOperations.isInSection(SlowOperations.ACTION_UPDATE) &&
        ActionUpdater.Companion.currentInEDTOperationName() != null && !SlowOperations.isAlwaysAllowed()) {
      String message = "'" + dataId + "' is requested on EDT by " + ActionUpdater.Companion.currentInEDTOperationName() + ". See ActionUpdateThread javadoc.";
      if (!Strings.areSameInstance(message, ourEDTWarnsInterner.intern(message))) return;
      Throwable th = error ? new Throwable(message) : null;
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        if (error) {
          LOG.error(th);
        }
        else {
          LOG.warn(message);
        }
      });
    }
  }

  private static void reportValueProvidedByRules(@NotNull String dataId) {
    if (!Registry.is("actionSystem.update.actions.warn.dataRules.on.edt")) return;
    if ("History".equals(dataId) || "treeExpanderHideActions".equals(dataId)) {
      LOG.error("'" + dataId + "' is provided by a rule"); // EA-648179
    }
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId, boolean uiOnly) {
    //noinspection DuplicatedCode
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) return dereference(myComponentRef.ref);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) return myComponentRef.modalContext;
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) return myComponentRef.modalityState;
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId) && myComponentRef.speedSearchText != null) return myComponentRef.speedSearchText;
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId) && myComponentRef.speedSearchRef != null) return myComponentRef.speedSearchRef.get();
    if (myCachedData.isEmpty()) return null;

    for (ProviderData map : myCachedData) {
      Object answer = map.uiSnapshot.get(dataId);
      if (answer == null && !uiOnly) answer = map.computedData.get(dataId);
      if (answer != null) {
        return answer == EXPLICIT_NULL ? null : answer;
      }
    }
    return null;
  }

  static void clearAllCaches() {
    for (FList<ProviderData> list : ourPrevMaps.values()) {
      for (ProviderData map : list) {
        map.uiSnapshot.clear();
        map.computedData.clear();
      }
    }
    ourPrevMaps.clear();
    ourComponents.clear();
    for (PreCachedDataContext context : ourInstances) {
      for (ProviderData map : context.myCachedData) {
        map.uiSnapshot.clear();
        map.computedData.clear();
      }
    }
    ourInstances.clear();
  }

  static @NotNull AsyncDataContext customize(@NotNull AsyncDataContext context, @Nullable Object provider) {
    if (provider == null) return context;
    MySink sink = new MySink();
    cacheProviderData(sink, provider, (DataManagerImpl)DataManager.getInstance());
    Map<String, Object> snapshot = sink.map == null ? null : sink.map.uiSnapshot;
    if (snapshot == null) return context;
    return dataId -> DataManager.getInstance().getCustomizedData(dataId, context, snapshot::get);
  }

  private static int getDataKeyIndex(@NotNull String dataId) {
    int keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1);
    if (keyIndex == -1 && ourDataKeysIndices.size() < DataKey.allKeysCount()) {
      for (DataKey<?> key : DataKey.allKeys()) {
        ourDataKeysIndices.computeIfAbsent(key.getName(), __ -> ourDataKeysCount.getAndIncrement());
      }
      keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1);
    }
    return keyIndex;
  }

  private static @NotNull FList<ProviderData> cacheComponentsData(@NotNull MySink sink,
                                                                  @NotNull List<? extends Component> components,
                                                                  @NotNull FList<ProviderData> initial,
                                                                  @NotNull DataManagerImpl dataManager) {
    FList<ProviderData> cachedData = initial;
    long start = System.currentTimeMillis();
    for (Component comp : components) {
      sink.map = null;
      sink.hideEditor = hideEditor(comp);
      Object dataProvider = comp instanceof UiDataProvider ? comp : getDataProviderEx(comp);
      cacheProviderData(sink, dataProvider, dataManager);
      cachedData = sink.map == null ? cachedData : cachedData.prepend(sink.map);
      ourPrevMaps.put(comp, cachedData);
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
    return cachedData;
  }

  private static void cacheProviderData(@NotNull MySink sink,
                                        @Nullable Object dataProvider,
                                        @NotNull DataManagerImpl dataManager) {
    DataSink.uiDataSnapshot(sink, dataProvider);
    if (sink.map != null) { // no data - no rules
      for (DataKey<?> key : dataManager.keysForRuleType(GetDataRuleType.FAST)) {
        Object data = dataManager.getDataFromRules(key.getName(), GetDataRuleType.FAST, sink.map.uiSnapshot::get);
        if (data == null) continue;
        sink.map.uiSnapshot.putIfAbsent(key.getName(), data);
      }
    }
    if (sink.hideEditor) {
      if (sink.map == null) sink.map = new ProviderData();
      sink.map.uiSnapshot.put(CommonDataKeys.EDITOR.getName(), EXPLICIT_NULL);
      sink.map.uiSnapshot.put(CommonDataKeys.HOST_EDITOR.getName(), EXPLICIT_NULL);
      sink.map.uiSnapshot.put(InjectedDataKeys.EDITOR.getName(), EXPLICIT_NULL);
    }
  }

  private static @NotNull FList<ProviderData> runSnapshotRules(@NotNull MySink sink,
                                                               @Nullable Component component,
                                                               @NotNull FList<ProviderData> cachedData) {
    boolean noMap = sink.map == null;
    DataSnapshot snapshot = new DataSnapshot() {
      /** @noinspection unchecked */
      @Override
      public <T> @Nullable T get(@NotNull DataKey<T> key) {
        if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT) return (T)component;
        for (ProviderData map : cachedData) {
          Object answer = map.uiSnapshot.get(key.getName());
          if (answer != null) {
            return answer == EXPLICIT_NULL ? null : (T)answer;
          }
        }
        return null;
      }
    };
    UiDataRule.forEachRule(o -> {
      Object prev = sink.source;
      sink.source = o;
      sink.cachedDataForRules = cachedData;
      try {
        o.uiDataSnapshot(sink, snapshot);
      }
      finally {
        sink.cachedDataForRules = null;
        sink.source = prev;
      }
    });
    return noMap && sink.map != null ? cachedData.prepend(sink.map) : cachedData;
  }

  private static class MySink implements DataSink {
    ProviderData map;
    Object source;
    DataKey<?>[] keys;
    @Deprecated
    boolean hideEditor;
    FList<ProviderData> cachedDataForRules;

    @Override
    public <T> void set(@NotNull DataKey<T> key, @Nullable T data) {
      if (data == null) return;
      if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformDataKeys.MODALITY_STATE) {
        return;
      }
      T validated;
      if (data == EXPLICIT_NULL) {
        validated = data;
      }
      else if (key == PlatformCoreDataKeys.BGT_DATA_PROVIDER) {
        DataProvider existing = map == null ? null : (DataProvider)map.uiSnapshot.get(key.getName());
        //noinspection unchecked
        validated = existing == null ? data :
                    cachedDataForRules == null ?
                    (T)CompositeDataProvider.compose((DataProvider)data, existing) :
                    (T)CompositeDataProvider.compose(existing, (DataProvider)data);
      }
      else {
        //noinspection unchecked
        validated = (T)DataValidators.validOrNull(data, key.getName(), source);
      }
      if (validated == null) return;
      if (map == null) map = new ProviderData();
      if (cachedDataForRules != null && key != PlatformCoreDataKeys.BGT_DATA_PROVIDER) {
        for (ProviderData map : cachedDataForRules) {
          if (map.uiSnapshot.get(key.getName()) != null) {
            return;
          }
        }
      }
      map.uiSnapshot.put(key.getName(), validated);
      if (key == CommonDataKeys.EDITOR && validated != EXPLICIT_NULL) {
        map.uiSnapshot.put(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getName(), validated);
      }
    }

    @Override
    public <T> void setNull(@NotNull DataKey<T> key) {
      if (key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformDataKeys.MODALITY_STATE) {
        return;
      }
      if (map == null) map = new ProviderData();
      map.uiSnapshot.put(key.getName(), EXPLICIT_NULL);
    }

    @Override
    public <T> void lazy(@NotNull DataKey<T> key, @NotNull Function0<? extends @Nullable T> data) {
      set(PlatformCoreDataKeys.BGT_DATA_PROVIDER, new MyLazy<>(key, data));
    }

    @Override
    public <T> void lazyNull(@NotNull DataKey<T> key) {
      set(PlatformCoreDataKeys.BGT_DATA_PROVIDER, dataId -> key.is(dataId) ? EXPLICIT_NULL : null);
    }

    @Override
    public void uiDataSnapshot(@NotNull UiDataProvider provider) {
      Object prev = source;
      source = provider;
      try {
        provider.uiDataSnapshot(this);
      }
      finally {
        source = prev;
      }
    }

    @Override
    public void dataSnapshot(@NotNull DataSnapshotProvider provider) {
      Object prev = source;
      source = provider;
      try {
        provider.dataSnapshot(this);
      }
      finally {
        source = prev;
      }
    }

    @Override
    public void uiDataSnapshot(@NotNull DataProvider provider) {
      Object prev = source;
      source = provider;
      try {
        if (keys == null) {
          keys = DataKey.allKeys();
        }
        for (DataKey<?> key : keys) {
          Object data = key.getData(provider);
          if (data != null) {
            //noinspection unchecked
            set((DataKey<Object>)key, data);
          }
        }
      }
      finally {
        source = prev;
      }
    }
  }

  private static class MyLazy<T> implements DataProvider, DataValidators.SourceWrapper {
    final DataKey<T> key;
    final Function0<? extends @Nullable T> supplier;

    MyLazy(@NotNull DataKey<T> key, @NotNull Function0<? extends @Nullable T> supplier) {
      this.key = key;
      this.supplier = supplier;
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return key.is(dataId) ? supplier.invoke() : null;
    }

    @Override
    public @NotNull Object unwrapSource() {
      return supplier;
    }
  }

  private static boolean hideEditor(@Nullable Component component) {
    return component instanceof JComponent &&
           ((JComponent)component).getClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY) != null;
  }

  @Override
  public String toString() {
    return (this instanceof InjectedDataContext ? "injected:" : "") +
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

  private static final class InjectedDataContext extends PreCachedDataContext {
    InjectedDataContext(@NotNull ComponentRef compRef,
                        @NotNull FList<ProviderData> cachedData,
                        @NotNull AtomicReference<KeyFMap> userData,
                        @NotNull DataManagerImpl dataManager,
                        int dataKeysCount) {
      super(compRef, cachedData, userData, dataManager, dataKeysCount);
    }

    @Override
    protected @Nullable Object getDataInner(@NotNull String dataId, boolean rulesAllowedBase, boolean ruleValuesAllowed) {
      return InjectedDataKeys.getInjectedData(dataId, (key) -> super.getDataInner(key, rulesAllowedBase, ruleValuesAllowed));
    }
  }

  private static final class ProviderData {
    final Map<String, Object> uiSnapshot = new ConcurrentHashMap<>();
    final Map<String, Object> computedData = new ConcurrentHashMap<>();
    // to avoid lots of nulls in maps
    final ConcurrentBitSet nullsByRules = ConcurrentBitSet.create();
    final ConcurrentBitSet nullsByContextRules = ConcurrentBitSet.create();
  }

  private static final class ComponentRef {
    final Reference<Component> ref;
    final ModalityState modalityState;
    final Boolean modalContext;

    final String speedSearchText;
    final Reference<Component> speedSearchRef;

    ComponentRef(@Nullable Component component) {
      ref = component == null ? null : new WeakReference<>(component);
      modalityState = component == null ? ModalityState.nonModal() : ModalityState.stateForComponent(component);
      modalContext = component == null ? null : Utils.isModalContext(component);

      SpeedSearchSupply supply = component instanceof JComponent ? SpeedSearchSupply.getSupply((JComponent)component) : null;
      speedSearchText = supply != null ? supply.getEnteredPrefix() : null;
      JTextField field = supply instanceof SpeedSearchBase<?> ? ((SpeedSearchBase<?>)supply).getSearchField() : null;
      speedSearchRef = field != null ? new WeakReference<>(field) : null;
    }
  }
}