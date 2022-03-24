// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;
import static com.intellij.ide.impl.DataManagerImpl.validateEditor;

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

  private final DataManagerImpl myManager;
  private final Map<String, Object> myCachedData;

  public EdtDataContext(@Nullable Component component) {
    myEventCount = -1;
    myRef = component == null ? null : new WeakReference<>(component);
    myCachedData = ContainerUtil.createWeakValueMap();
    myUserData = Ref.create(KeyFMap.EMPTY_MAP);
    myManager = (DataManagerImpl)DataManager.getInstance();
  }

  private EdtDataContext(@Nullable Reference<Component> compRef,
                         @NotNull Map<String, Object> cachedData,
                         @NotNull Ref<KeyFMap> userData,
                         @NotNull DataManagerImpl manager,
                         int eventCount) {
    myRef = compRef;
    myCachedData = cachedData;
    myUserData = userData;
    myManager = manager;
    myEventCount = eventCount;
  }

  @Override
  public @NotNull DataContext getInjectedDataContext() {
    return this instanceof InjectedDataContext ? this : new InjectedDataContext(myRef, myCachedData, myUserData, myManager, myEventCount);
  }

  public void setEventCount(int eventCount) {
    assert ReflectionUtil.getCallerClass(3) == IdeKeyEventDispatcher.class :
      "This method might be accessible from " + IdeKeyEventDispatcher.class.getName() + " only";
    myCachedData.clear();
    myEventCount = eventCount;
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    ProgressManager.checkCanceled();
    boolean cacheable = Registry.is("actionSystem.cache.data") || ourSafeKeys.contains(dataId);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      int currentEventCount = IdeEventQueue.getInstance().getEventCount();
      if (myEventCount != -1 && myEventCount != currentEventCount) {
        LOG.error("cannot share data context between Swing events; initial event count = " + myEventCount +
                  "; current event count = " + currentEventCount);
        cacheable = false;
      }
    }

    Object answer = cacheable ? myCachedData.get(dataId) : null;
    if (answer != null) {
      return answer != NullResult.INSTANCE ? answer : null;
    }

    answer = doGetData(dataId);
    if (cacheable && !(answer instanceof Stream)) {
      myCachedData.put(dataId, answer == null ? NullResult.INSTANCE : answer);
    }
    return answer;
  }

  private @Nullable Object doGetData(@NotNull String dataId) {
    Component component = SoftReference.dereference(myRef);
    if (PlatformCoreDataKeys.IS_MODAL_CONTEXT.is(dataId)) {
      if (component == null) {
        return null;
      }
      return IdeKeyEventDispatcher.isModalContext(component);
    }
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.is(dataId)) {
      return component;
    }
    if (PlatformDataKeys.MODALITY_STATE.is(dataId)) {
      return component != null ? ModalityState.stateForComponent(component) : ModalityState.NON_MODAL;
    }
    Object data = calcData(dataId, component);
    if (CommonDataKeys.EDITOR.is(dataId) || CommonDataKeys.HOST_EDITOR.is(dataId) || InjectedDataKeys.EDITOR.is(dataId)) {
      return validateEditor((Editor)data, component);
    }
    return data;
  }

  protected @Nullable Object calcData(@NotNull String dataId, @Nullable Component component) {
    GetDataRule rule = myManager.getDataRule(dataId);
    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      for (Component c = component; c != null; c = c.getParent()) {
        DataProvider dataProvider = getDataProviderEx(c);
        if (dataProvider == null) continue;
        Object data = myManager.getDataFromProvider(dataProvider, dataId, null, rule);
        if (data != null) return data;
      }
    }
    return null;
  }

  @Nullable Object getRawDataIfCached(@NotNull String dataId) {
    Object data = myCachedData.get(dataId);
    return data == NullResult.INSTANCE ? null : data;
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

  private static final Set<String> ourSafeKeys = ContainerUtil.set(
    CommonDataKeys.PROJECT.getName(),
    CommonDataKeys.EDITOR.getName(),
    PlatformCoreDataKeys.IS_MODAL_CONTEXT.getName(),
    PlatformCoreDataKeys.CONTEXT_COMPONENT.getName(),
    PlatformDataKeys.MODALITY_STATE.getName()
  );

  enum NullResult {INSTANCE}

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