// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Level;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serviceContainer.InstantiatingComponentAdapter;
import com.intellij.serviceContainer.PlatformComponentManagerImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.*;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.Disposable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

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
  private boolean myComponentsCreated;

  private final List<BaseComponent> myBaseComponents = new SmartList<>();

  protected final ComponentManager myParent;

  protected ComponentManagerImpl(@Nullable ComponentManager parent) {
    myParent = parent;
    myPicoContainer = new DefaultPicoContainer(parent == null ? null : parent.getPicoContainer());
    myExtensionArea = new ExtensionsAreaImpl(this);
  }

  @Nullable
  protected String activityNamePrefix() {
    return null;
  }

  protected final void createComponents(@Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!myComponentsCreated);

    if (indicator != null) {
      indicator.setIndeterminate(false);
    }

    String activityNamePrefix = activityNamePrefix();
    Activity activity = activityNamePrefix == null ? null : StartUpMeasurer.start(activityNamePrefix + Phases.CREATE_COMPONENTS_SUFFIX);

    for (ComponentAdapter componentAdapter : myPicoContainer.getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter) {
        ((ComponentConfigComponentAdapter)componentAdapter).getComponentInstance(this, indicator);
      }
    }

    if (activity != null) {
      activity.end();
    }

    myComponentsCreated = true;
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

  public final boolean isComponentsCreated() {
    return myComponentsCreated;
  }

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

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentAdapter adapter = picoContainer.getComponentAdapter(interfaceClass);
    if (adapter == null) {
      return null;
    }

    //noinspection unchecked
    return (T)adapter.getComponentInstance(picoContainer);
  }

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    T component = getComponent(interfaceClass);
    return component == null ? defaultImplementation : component;
  }

  @Nullable
  protected ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
  }

  @ApiStatus.Internal
  public abstract void handleInitComponentError(@NotNull Throwable ex, String componentClassName, PluginId pluginId);

  @TestOnly
  public final void registerComponentImplementation(@NotNull Class<?> componentKey,
                                                    @NotNull Class<?> componentImplementation,
                                                    boolean shouldBeRegistered) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)picoContainer.unregisterComponent(componentKey);
    if (shouldBeRegistered) {
      LOG.assertTrue(adapter != null);
    }
    picoContainer.registerComponent(new ComponentConfigComponentAdapter(componentKey, componentImplementation, null, this, false));
  }

  @TestOnly
  public final synchronized <T> T registerComponentInstance(@NotNull Class<T> componentKey, @NotNull T componentImplementation) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentAdapter adapter = picoContainer.getComponentAdapter(componentKey);
    LOG.assertTrue(adapter instanceof ComponentConfigComponentAdapter);
    ComponentConfigComponentAdapter componentAdapter = (ComponentConfigComponentAdapter)adapter;
    Object oldInstance = componentAdapter.myInitializedComponentInstance;
    // we don't update pluginId - method is test only
    componentAdapter.myInitializedComponentInstance = componentImplementation;
    //noinspection unchecked
    return (T)oldInstance;
  }

  @SuppressWarnings("deprecation")
  @Override
  @NotNull
  public final <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass, boolean createIfNotYet) {
    List<T> result = null;
    // we must use instances only from our adapter (could be service or extension point or something else)
    for (ComponentAdapter componentAdapter : getPicoContainer().getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter &&
          ReflectionUtil.isAssignable(baseClass, componentAdapter.getComponentImplementation())) {
        ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)componentAdapter;
        //noinspection unchecked
        T instance = (T)(createIfNotYet ? adapter.getComponentInstance(myPicoContainer) : (T)adapter.myInitializedComponentInstance);
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
    ComponentConfigComponentAdapter adapter = getComponentAdapter(componentImplementation);
    return adapter != null && adapter.isWorkspaceComponent;
  }

  @Nullable
  private ComponentConfigComponentAdapter getComponentAdapter(@NotNull Class<?> componentImplementation) {
    for (ComponentAdapter componentAdapter : getPicoContainer().getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter && componentAdapter.getComponentImplementation() == componentImplementation) {
        return (ComponentConfigComponentAdapter)componentAdapter;
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

  protected final void registerComponents(@NotNull ComponentConfig config, @NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader loader = pluginDescriptor.getPluginClassLoader();
    try {
      Class<?> interfaceClass = Class.forName(config.getInterfaceClass(), true, loader);
      Class<?> implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass()) ? interfaceClass :
                                     StringUtil.isEmpty(config.getImplementationClass()) ? null :
                                     Class.forName(config.getImplementationClass(), true, loader);
      MutablePicoContainer picoContainer = getPicoContainer();
      if (config.options != null && Boolean.parseBoolean(config.options.get("overrides"))) {
        ComponentAdapter oldAdapter = picoContainer.getComponentAdapterOfType(interfaceClass);
        if (oldAdapter == null) {
          throw new RuntimeException(config + " does not override anything");
        }
        picoContainer.unregisterComponent(oldAdapter.getComponentKey());
      }
      // implementationClass == null means we want to unregister this component
      if (implementationClass != null) {
        boolean ws = config.options != null && Boolean.parseBoolean(config.options.get("workspace"));
        picoContainer.registerComponent(new ComponentConfigComponentAdapter(interfaceClass, implementationClass, pluginDescriptor.getPluginId(), this, ws));
      }
    }
    catch (Throwable t) {
      handleInitComponentError(t, null, pluginDescriptor.getPluginId());
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public final synchronized BaseComponent getComponent(@NotNull String name) {
    return myNameToComponent.get(name);
  }

  protected static final class ComponentConfigComponentAdapter extends InstantiatingComponentAdapter {
    private final PluginId myPluginId;
    private volatile Object myInitializedComponentInstance;

    @NotNull
    private final ComponentManagerImpl componentManager;

    final boolean isWorkspaceComponent;

    public ComponentConfigComponentAdapter(@NotNull Class<?> key,
                                           @NotNull Class<?> implementationClass,
                                           @Nullable PluginId pluginId,
                                           @NotNull ComponentManagerImpl componentManager,
                                           boolean isWorkspaceComponent) {
      super(key, implementationClass);

      myPluginId = pluginId;
      this.componentManager = componentManager;
      this.isWorkspaceComponent = isWorkspaceComponent;
    }

    @NotNull
    @Override
    protected PluginId getPluginId() {
      return myPluginId;
    }

    @Override
    public Object getComponentInstance(@NotNull PicoContainer picoContainer) {
      return getComponentInstance(componentManager, null);
    }

    Object getComponentInstance(@NotNull ComponentManagerImpl componentManager, @Nullable ProgressIndicator indicator) {
      Object instance = myInitializedComponentInstance;
      // getComponent could be called during some component.dispose() call, in this case we don't attempt to instantiate component
      if (instance != null || componentManager.myDisposed) {
        return instance;
      }

      return getInstance(/* createIfNeeded = */ true, (PlatformComponentManagerImpl)componentManager, indicator);
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
          String errorMessage = "Component name collision: " + componentName +
                                ' ' + (loadedComponent == null ? "null" : loadedComponent.getClass()) + " and " + instance.getClass();
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
        componentManager.handleInitComponentError(t, getComponentKey().getName(), myPluginId);
        throw t;
      }
    }

    @Nullable
    private Activity createMeasureActivity(@NotNull PlatformComponentManagerImpl componentManager) {
      Level level = DefaultPicoContainer.getActivityLevel(componentManager.myPicoContainer);
      if (level == Level.APPLICATION || level == Level.PROJECT && componentManager.activityNamePrefix() != null) {
        return ParallelActivity.COMPONENT.start(getComponentImplementation().getName(), level, myPluginId != null ? myPluginId.getIdString() : null);
      }
      return null;
    }

    @Override
    public String toString() {
      return "ComponentConfigAdapter[" + getComponentKey() + "]: implementation=" + getComponentImplementation() + ", plugin=" + myPluginId;
    }
  }
}