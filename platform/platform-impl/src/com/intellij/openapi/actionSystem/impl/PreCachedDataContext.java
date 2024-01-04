// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.DataValidators;
import com.intellij.ide.impl.FreezingDataContext;
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
import com.intellij.reference.SoftReference;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.*;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.openapi.actionSystem.CustomizedDataContext.EXPLICIT_NULL;
import static com.intellij.openapi.actionSystem.impl.EdtDataContextKt.wrapUnsafeData;

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
  private static final Interner<String> ourEDTWarnsInterner = Interner.createStringInterner();

  private final ComponentRef myComponentRef;
  private final AtomicReference<KeyFMap> myUserData;
  private final FList<ProviderData> myCachedData;
  private final Consumer<? super String> myMissedKeysIfFrozen;
  private final DataManagerImpl myDataManager;
  private final int myDataKeysCount;

  PreCachedDataContext(@Nullable Component component) {
    myComponentRef = new ComponentRef(component);
    myMissedKeysIfFrozen = null;
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
      }
      List<Component> components = FList.createFromReversed(
        JBIterable.generate(component, UIUtil::getParent).takeWhile(o -> ourPrevMaps.get(o) == null));
      Component topParent = components.isEmpty() ? component : UIUtil.getParent(components.get(0));
      FList<ProviderData> initial = topParent == null ? FList.emptyList() : ourPrevMaps.get(topParent);

      if (components.isEmpty()) {
        myCachedData = initial;
        myDataKeysCount = ourDataKeysIndices.size();
      }
      else {
        DataKey<?>[] keys = DataKey.allKeys();
        myDataKeysCount = updateDataKeyIndices(keys);
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.FORCE_ASSERT)) {
          myCachedData = cacheComponentsData(components, initial, myDataManager, keys);
        }
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
                               @NotNull DataManagerImpl dataManager,
                               int dataKeysCount) {
    myComponentRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myMissedKeysIfFrozen = missedKeys;
    myDataManager = dataManager;
    myDataKeysCount = dataKeysCount;
  }

  boolean cachesAllKnownDataKeys() {
    return myDataKeysCount == DataKey.allKeysCount();
  }

  final @NotNull PreCachedDataContext frozenCopy(@Nullable Consumer<? super String> missedKeys) {
    Consumer<? super String> missedKeysNotNull = missedKeys == null ? s -> { } : missedKeys;
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myComponentRef, myCachedData, myUserData, missedKeysNotNull, myDataManager, myDataKeysCount)
           : new PreCachedDataContext(myComponentRef, myCachedData, myUserData, missedKeysNotNull, myDataManager, myDataKeysCount);
  }

  @Override
  public final @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this :
           new InjectedDataContext(myComponentRef, myCachedData, myUserData, myMissedKeysIfFrozen, myDataManager, myDataKeysCount);
  }

  @Override
  public boolean isFrozenDataContext() {
    return myMissedKeysIfFrozen != null;
  }

  @NotNull PreCachedDataContext prependProvider(@NotNull DataProvider dataProvider) {
    DataKey<?>[] keys = DataKey.allKeys();
    Component component = SoftReference.dereference(myComponentRef.ref);
    int dataKeysCount = updateDataKeyIndices(keys);
    ProviderData cachedData = cacheProviderData(dataProvider, component, myDataManager, keys);
    FList<ProviderData> newCachedData = cachedData == null ? myCachedData : myCachedData.prepend(cachedData);
    AtomicReference<KeyFMap> userData = new AtomicReference<>(KeyFMap.EMPTY_MAP);
    return this instanceof InjectedDataContext
           ? new InjectedDataContext(myComponentRef, newCachedData, userData, myMissedKeysIfFrozen, myDataManager, dataKeysCount)
           : new PreCachedDataContext(myComponentRef, newCachedData, userData, myMissedKeysIfFrozen, myDataManager, dataKeysCount);
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) return SoftReference.dereference(myComponentRef.ref);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) return myComponentRef.modalContext;
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) return myComponentRef.modalityState;
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId) && myComponentRef.speedSearchText != null) return myComponentRef.speedSearchText;
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId) && myComponentRef.speedSearchRef != null) return SoftReference.dereference(myComponentRef.speedSearchRef);
    if (myCachedData.isEmpty()) return null;

    boolean isEDT = EDT.isCurrentThreadEdt();
    boolean noRulesSection = isEDT && ActionUpdater.Companion.isNoRulesInEDTSection();
    boolean rulesSuppressed = isEDT && Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt");
    boolean rulesAllowed = myMissedKeysIfFrozen == null && !CommonDataKeys.PROJECT.is(dataId) && !rulesSuppressed && !noRulesSection;
    Object answer = getDataInner(dataId, rulesAllowed, !noRulesSection);

    int keyIndex; // for use with `nullsByContextRules` only, always != -1
    ProviderData map = myCachedData.get(0);
    if (answer == null && rulesAllowed && !map.nullsByContextRules.get(keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1))) {
      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.CONTEXT, id -> {
        return getDataInner(id, !CommonDataKeys.PROJECT.is(id), true);
      });
      if (answer != null) {
        map.put(dataId, answer);
        map.nullsByRules.clear(keyIndex);
        map.valueByRules.set(keyIndex);
        reportValueProvidedByRules(dataId);
      }
      else {
        map.nullsByContextRules.set(keyIndex);
      }
    }
    if (answer == null && myMissedKeysIfFrozen != null) {
      myMissedKeysIfFrozen.accept(dataId);
      return null;
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

  protected @Nullable Object getDataInner(@NotNull String dataId, boolean rulesAllowedBase, boolean ruleValuesAllowed) {
    int keyIndex = ourDataKeysIndices.getOrDefault(dataId, -1);
    if (keyIndex == -1) return EXPLICIT_NULL; // newly created data key => no data provider => no value
    boolean rulesAllowed = rulesAllowedBase && keyIndex < myDataKeysCount;

    Object answer = null;
    for (ProviderData map : myCachedData) {
      ProgressManager.checkCanceled();
      answer = map.get(dataId);
      if (answer == EXPLICIT_NULL) break;
      if (answer != null) {
        if (map.valueByRules.get(keyIndex)) {
          reportValueProvidedByRulesUsage(dataId, !ruleValuesAllowed);
          if (!ruleValuesAllowed) return null;
        }
        answer = DataValidators.validOrNull(answer, dataId, this);
        if (answer != null) break;
        // allow slow data providers and rules to re-calc the value
        map.remove(dataId);
        map.valueByRules.clear(keyIndex);
      }
      if (!rulesAllowed || map.nullsByRules.get(keyIndex)) continue;

      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.PROVIDER, id -> {
        return Objects.equals(id, dataId) ? null : map.get(id);
      });

      if (answer == null) {
        map.nullsByRules.set(keyIndex);
      }
      else {
        map.put(dataId, answer);
        map.valueByRules.set(keyIndex);
        reportValueProvidedByRules(dataId);
        break;
      }
    }
    return answer;
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

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    for (ProviderData map : myCachedData) {
      Object answer = map.get(dataId);
      if (answer != null) {
        return answer == EXPLICIT_NULL ? null : answer;
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

  private static int updateDataKeyIndices(DataKey<?> @NotNull [] keys) {
    if (ourDataKeysIndices.size() >= keys.length) {
      return ourDataKeysIndices.size();
    }
    for (DataKey<?> key : keys) {
      ourDataKeysIndices.computeIfAbsent(key.getName(), __ -> ourDataKeysCount.getAndIncrement());
    }
    return ourDataKeysIndices.size();
  }

  private static @NotNull FList<ProviderData> cacheComponentsData(@NotNull List<? extends Component> components,
                                                                  @NotNull FList<ProviderData> initial,
                                                                  @NotNull DataManagerImpl dataManager,
                                                                  DataKey<?> @NotNull [] keys) {
    FList<ProviderData> result = initial;
    long start = System.currentTimeMillis();
    for (Component comp : components) {
      DataProvider dataProvider = getDataProviderEx(comp);
      if (dataProvider == null && hideEditor(comp)) dataProvider = dataId -> null;
      if (dataProvider == null) continue;
      ProviderData cachedData = cacheProviderData(dataProvider, comp, dataManager, keys);
      result = cachedData == null ? result : result.prepend(cachedData);
      ourPrevMaps.put(comp, result);
    }
    long time = System.currentTimeMillis() - start;
    if (time > 200) {
      // nothing
    }
    return result;
  }

  private static @Nullable ProviderData cacheProviderData(@NotNull DataProvider dataProvider,
                                                          @Nullable Component c,
                                                          @NotNull DataManagerImpl dataManager,
                                                          DataKey<?> @NotNull [] keys) {
    ProviderData cachedData = null;
    boolean hideEditor = hideEditor(c);
    for (DataKey<?> key : keys) {
      if (key == PlatformCoreDataKeys.IS_MODAL_CONTEXT ||
          key == PlatformCoreDataKeys.CONTEXT_COMPONENT ||
          key == PlatformDataKeys.MODALITY_STATE) {
        continue;
      }
      Object data = hideEditor && (key == CommonDataKeys.EDITOR || key == CommonDataKeys.HOST_EDITOR || key == InjectedDataKeys.EDITOR) ?
                    EXPLICIT_NULL : dataManager.getDataFromProviderAndRules(key.getName(), GetDataRuleType.FAST, dataProvider);
      if (data == null) continue;
      if (cachedData == null) cachedData = new ProviderData();
      cachedData.put(key.getName(), data);
    }
    return cachedData;
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

  private static final class InjectedDataContext extends PreCachedDataContext {
    InjectedDataContext(@NotNull ComponentRef compRef,
                        @NotNull FList<ProviderData> cachedData,
                        @NotNull AtomicReference<KeyFMap> userData,
                        @Nullable Consumer<? super String> missedKeys,
                        @NotNull DataManagerImpl dataManager,
                        int dataKeysCount) {
      super(compRef, cachedData, userData, missedKeys, dataManager, dataKeysCount);
    }

    @Override
    protected @Nullable Object getDataInner(@NotNull String dataId, boolean rulesAllowedBase, boolean ruleValuesAllowed) {
      return InjectedDataKeys.getInjectedData(dataId, (key) -> super.getDataInner(key, rulesAllowedBase, ruleValuesAllowed));
    }
  }

  private static final class ProviderData extends ConcurrentHashMap<String, Object> {
    final ConcurrentBitSet nullsByRules = ConcurrentBitSet.create();
    final ConcurrentBitSet nullsByContextRules = ConcurrentBitSet.create();
    final ConcurrentBitSet valueByRules = ConcurrentBitSet.create();
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