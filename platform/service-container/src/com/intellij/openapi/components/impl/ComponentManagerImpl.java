// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer.Level;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.serviceContainer.InstantiatingComponentAdapter;
import com.intellij.serviceContainer.PlatformComponentManagerImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManager, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  protected final DefaultPicoContainer myPicoContainer;
  protected final ExtensionsAreaImpl myExtensionArea;

  private volatile boolean myDisposed;
  private volatile boolean myDisposeCompleted;

  private volatile MessageBus myMessageBus;

  // contents guarded by this
  private final Map<String, BaseComponent> myNameToComponent = new THashMap<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  protected int myComponentConfigCount;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myInstantiatedComponentCount;

  private final List<BaseComponent> myBaseComponents = new SmartList<>();

  protected ComponentManagerImpl(@Nullable ComponentManager parent) {
    myPicoContainer = new DefaultPicoContainer(parent == null ? null : parent.getPicoContainer());
    myExtensionArea = new ExtensionsAreaImpl(this);
  }

  @Nullable
  protected String activityNamePrefix() {
    return null;
  }

  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded());
  }

  protected final double getPercentageOfComponentsLoaded() {
    return (double)myInstantiatedComponentCount / myComponentConfigCount;
  }

  @NotNull
  @Override
  public final MessageBus getMessageBus() {
    if (myDisposed) {
      throwAlreadyDisposed();
    }

    MessageBus messageBus = myMessageBus;
    if (messageBus == null) {
      //noinspection SynchronizeOnThis
      synchronized (this) {
        messageBus = myMessageBus;
        if (messageBus == null) {
          messageBus = createMessageBus();
          myMessageBus = messageBus;
        }
      }
    }
    return messageBus;
  }

  @NotNull
  protected abstract MessageBus createMessageBus();

  protected final synchronized void disposeComponents() {
    assert !myDisposeCompleted : "Already disposed!";
    myDisposed = true;

    // we cannot use list of component adapters because we must dispose in reverse order of creation
    List<BaseComponent> components = myBaseComponents;
    for (int i = components.size() - 1; i >= 0; i--) {
      try {
        components.get(i).disposeComponent();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    myBaseComponents.clear();

    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    myComponentConfigCount = -1;
  }

  @Nullable
  protected ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
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
        T instance = ((MyComponentAdapter)componentAdapter).getInstance((PlatformComponentManagerImpl)this, createIfNeeded, null);
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
    if (container == null || myDisposeCompleted) {
      throwAlreadyDisposed();
    }
    return container;
  }

  @NotNull
  @Override
  public final ExtensionsArea getExtensionArea() {
    return myExtensionArea;
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
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposeCompleted = true;

    if (myMessageBus != null) {
      Disposer.dispose(myMessageBus);
      myMessageBus = null;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      myNameToComponent.clear();
    }
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  public final boolean isWorkspaceComponent(@NotNull Class<?> componentImplementation) {
    MyComponentAdapter adapter = getComponentAdapter(componentImplementation);
    return adapter != null && adapter.isWorkspaceComponent;
  }

  @Nullable
  private MyComponentAdapter getComponentAdapter(@NotNull Class<?> componentImplementation) {
    for (ComponentAdapter componentAdapter : getPicoContainer().getComponentAdapters()) {
      if (componentAdapter instanceof MyComponentAdapter && componentAdapter.getComponentImplementation() == componentImplementation) {
        return (MyComponentAdapter)componentAdapter;
      }
    }
    return null;
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

  @SuppressWarnings("deprecation")
  @Override
  public final synchronized BaseComponent getComponent(@NotNull String name) {
    return myNameToComponent.get(name);
  }

  protected static final class MyComponentAdapter extends InstantiatingComponentAdapter {
    final boolean isWorkspaceComponent;

    public MyComponentAdapter(@NotNull Class<?> key,
                              @NotNull Class<?> implementationClass,
                              @NotNull PluginId pluginId,
                              @NotNull PlatformComponentManagerImpl componentManager,
                              boolean isWorkspaceComponent) {
      super(key, implementationClass, pluginId, componentManager);

      this.isWorkspaceComponent = isWorkspaceComponent;
    }

    private static void registerComponentInstance(@NotNull Object instance, @NotNull ComponentManagerImpl componentManager) {
      componentManager.myInstantiatedComponentCount++;

      if (instance instanceof com.intellij.openapi.Disposable) {
        Disposer.register(componentManager, (com.intellij.openapi.Disposable)instance);
      }

      if (!(instance instanceof BaseComponent)) {
        return;
      }

      BaseComponent baseComponent = (BaseComponent)instance;
      String componentName = baseComponent.getComponentName();
      if (componentManager.myNameToComponent.containsKey(componentName)) {
        BaseComponent loadedComponent = componentManager.myNameToComponent.get(componentName);
        // component may have been already loaded by PicoContainer, so fire error only if components are really different
        if (!instance.equals(loadedComponent)) {
          String errorMessage = "Component name collision: " + componentName + ' ' +
                                (loadedComponent == null ? "null" : loadedComponent.getClass()) + " and " + instance.getClass();
          PluginException.logPluginError(LOG, errorMessage, null, instance.getClass());
        }
      }
      else {
        componentManager.myNameToComponent.put(componentName, baseComponent);
      }

      componentManager.myBaseComponents.add(baseComponent);
    }

    @Override
    @NotNull
    protected <T> T doCreateInstance(@NotNull PlatformComponentManagerImpl componentManager, @Nullable ProgressIndicator indicator) {
      try {
        Activity activity = createMeasureActivity(componentManager);
        T instance = createComponentInstance(componentManager);
        registerComponentInstance(instance, componentManager);

        if (indicator != null) {
          indicator.checkCanceled();
          componentManager.setProgressDuringInit(indicator);
        }
        componentManager.initializeComponent(instance, null);
        if (instance instanceof BaseComponent) {
          ((BaseComponent)instance).initComponent();
        }

        if (activity != null) {
          activity.end();
        }

        return instance;
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable t) {
        componentManager.handleInitComponentError(t, getComponentKey().getName(), getPluginId());
        throw t;
      }
    }

    @Nullable
    private Activity createMeasureActivity(@NotNull PlatformComponentManagerImpl componentManager) {
      if (componentManager.activityNamePrefix() == null) {
        return null;
      }

      Level level = componentManager.getActivityLevel();
      return ParallelActivity.COMPONENT.start(getComponentImplementation().getName(), level, getPluginId().getIdString());
    }
  }
}