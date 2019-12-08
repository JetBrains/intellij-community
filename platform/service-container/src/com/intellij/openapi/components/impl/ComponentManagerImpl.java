// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.serviceContainer.MyComponentAdapter;
import com.intellij.serviceContainer.PlatformComponentManagerImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManager {
  protected final DefaultPicoContainer myPicoContainer;

  protected enum ContainerState {
    ACTIVE, DISPOSE_IN_PROGRESS, DISPOSED, DISPOSE_COMPLETED
  }

  protected final AtomicReference<ContainerState> containerState = new AtomicReference<>(ContainerState.ACTIVE);

  protected ComponentManagerImpl(@Nullable ComponentManager parent) {
    myPicoContainer = new DefaultPicoContainer(parent == null ? null : parent.getPicoContainer());
  }

  @SuppressWarnings("deprecation")
  @Override
  @NotNull
  public final <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass, boolean createIfNeeded) {
    List<T> result = null;
    // we must use instances only from our adapter (could be service or something else)
    for (ComponentAdapter componentAdapter : getPicoContainer().getComponentAdapters()) {
      if (componentAdapter instanceof MyComponentAdapter &&
          ReflectionUtil.isAssignable(baseClass, componentAdapter.getComponentImplementation())) {
        T instance = ((MyComponentAdapter)componentAdapter).getInstance((PlatformComponentManagerImpl)this, null, createIfNeeded, null);
        if (instance != null) {
          if (result == null) {
            result = new ArrayList<>();
          }
          result.add(instance);
        }
      }
    }
    return ContainerUtil.notNullize(result);
  }

  @Override
  @NotNull
  public final DefaultPicoContainer getPicoContainer() {
    DefaultPicoContainer container = myPicoContainer;
    if (container == null || containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      throwAlreadyDisposed();
    }
    return container;
  }

  @Contract("->fail")
  private void throwAlreadyDisposed() {
    ReadAction.run(() -> {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: " + this);
    });
  }

  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    Map<String, String> options = componentConfig.options;
    return options == null || !Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal();
  }

  @Override
  @NotNull
  public final Condition<?> getDisposed() {
    return __ -> isDisposed();
  }

  @NotNull
  public static String getComponentName(@NotNull Object component) {
    if (component instanceof NamedComponent) {
      return ((NamedComponent)component).getComponentName();
    }
    return component.getClass().getName();
  }
}