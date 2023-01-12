// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.GetDataRuleType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.reference.SoftReference;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.ide.impl.DataManagerImpl.validateEditor;
import static com.intellij.openapi.actionSystem.CustomizedDataContext.EXPLICIT_NULL;

/**
 * This is an internal API. Do not use it.
 * <ul>
 *   <li>Do not create directly, use {@link DataManager#getDataContext(Component)} instead.</li>
 *   <li>Do not cast to {@link UserDataHolder}, use
 *     {@link DataManager#saveInDataContext(DataContext, Key, Object)} and
 *     {@link DataManager#loadFromDataContext(DataContext, Key)} instead.</li>
 *   <li>Do not override.</li>
 * </ul>
 */
@ApiStatus.Internal
public class EdtDataContext implements DataContext, UserDataHolder, AnActionEvent.InjectedDataContextSupplier {
  private static final Logger LOG = Logger.getInstance(EdtDataContext.class);

  private int myEventCount;
  // To prevent memory leak we have to wrap passed component into
  // the weak reference. For example, Swing often remembers menu items
  // that have DataContext as a field.
  private final Reference<Component> myRef;
  private final Ref<KeyFMap> myUserData;

  private final DataManagerImpl myDataManager;
  private final Map<String, Object> myCachedData;

  public EdtDataContext(@Nullable Component component) {
    myEventCount = -1;
    myRef = component == null ? null : new WeakReference<>(component);
    myCachedData = ContainerUtil.createWeakValueMap();
    myUserData = Ref.create(KeyFMap.EMPTY_MAP);
    myDataManager = (DataManagerImpl)DataManager.getInstance();
  }

  private EdtDataContext(@Nullable Reference<Component> compRef,
                         @NotNull Map<String, Object> cachedData,
                         @NotNull Ref<KeyFMap> userData,
                         @NotNull DataManagerImpl dataManager,
                         int eventCount) {
    myRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myDataManager = dataManager;
    myEventCount = eventCount;
  }

  @Override
  public @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this : new InjectedDataContext(myRef, myCachedData, myUserData, myDataManager, myEventCount);
  }

  public void setEventCount(int eventCount) {
    if (ReflectionUtil.getCallerClass(3) != IdeKeyEventDispatcher.class) {
      throw new AssertionError("This method might be accessible from " + IdeKeyEventDispatcher.class.getName() + " only");
    }
    myCachedData.clear();
    myEventCount = eventCount;
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    ProgressManager.checkCanceled();
    boolean cacheable;
    int currentEventCount = EDT.isCurrentThreadEdt() ? IdeEventQueue.getInstance().getEventCount() : -1;
    if (myEventCount == -1 || myEventCount == currentEventCount) {
      cacheable = true;
    }
    else {
      cacheable = false;
      LOG.error("cannot share data context between Swing events; initial event count = " + myEventCount +
                "; current event count = " + currentEventCount);
    }
    Object answer = getDataInner(dataId, cacheable);
    if (answer == null) {
      answer = myDataManager.getDataFromRules(dataId, GetDataRuleType.CONTEXT, id -> {
        return getDataInner(id, cacheable);
      });
      if (cacheable) {
        myCachedData.put(dataId, answer == null ? EXPLICIT_NULL : answer);
      }
    }
    return answer == EXPLICIT_NULL ? null : answer;
  }

  private @Nullable Object getDataInner(@NotNull String dataId, boolean cacheable) {
    Component component = SoftReference.dereference(myRef);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) {
      return component == null ? null : Utils.isModalContext(component);
    }
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) {
      return component;
    }
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) {
      return component != null ? ModalityState.stateForComponent(component) : ModalityState.NON_MODAL;
    }
    if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId) ||
        PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId)) {
      SpeedSearchSupply supply = component instanceof JComponent ? SpeedSearchSupply.getSupply((JComponent)component) : null;
      Object result = supply == null ? null :
                      PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId) ? supply.getEnteredPrefix() :
                      supply instanceof SpeedSearchBase ? ((SpeedSearchBase<?>)supply).getSearchField() : null;
      if (result != null) return result;
    }
    Object answer = cacheable ? myCachedData.get(dataId) : null;
    if (answer != null) {
      return answer;
    }
    answer = calcData(dataId, component);
    if (CommonDataKeys.EDITOR.is(dataId) || CommonDataKeys.HOST_EDITOR.is(dataId) || InjectedDataKeys.EDITOR.is(dataId)) {
      answer = validateEditor((Editor)answer, component);
    }
    if (cacheable) {
      myCachedData.put(dataId, answer == null ? EXPLICIT_NULL : answer);
    }
    return answer;
  }

  private @Nullable Object calcData(@NotNull String dataId, @Nullable Component component) {
    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      for (Component c = component; c != null; c = UIUtil.getParent(c)) {
        DataProvider dataProvider = getDataProviderEx(c);
        if (dataProvider == null) continue;
        Object data = myDataManager.getDataFromProviderAndRules(dataId, GetDataRuleType.PROVIDER, dataProvider);
        if (data != null) return data;
      }
    }
    return null;
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    Object data = myCachedData.get(dataId);
    return data == EXPLICIT_NULL ? null : data;
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myUserData.get().get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    KeyFMap map = myUserData.get();
    myUserData.set(value == null ? map.minus(key) : map.plus(key, value));
  }

  @Override
  @NonNls
  public String toString() {
    return (this instanceof InjectedDataContext ? "injected:" : "") +
           "component=" + SoftReference.dereference(myRef);
  }

  private static class InjectedDataContext extends EdtDataContext {
    InjectedDataContext(@Nullable Reference<Component> compRef,
                        @NotNull Map<String, Object> cachedData,
                        @NotNull Ref<KeyFMap> userData,
                        @NotNull DataManagerImpl manager,
                        int eventCount) {
      super(compRef, cachedData, userData, manager, eventCount);
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      Object injected = injectedId != null ? super.getData(injectedId) : null;
      return injected != null ? injected : super.getData(dataId);
    }
  }
}