/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.IdeaPicoContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private final Map<Class, Object> myInitializedComponents = new ConcurrentHashMap<Class, Object>(4096);

  private boolean myComponentsCreated = false;

  private MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed = false;
  private volatile boolean myDisposeCompleted = false;

  private MessageBus myMessageBus;

  private final ComponentManagerConfigurator myConfigurator = new ComponentManagerConfigurator(this);
  private final ComponentManager myParentComponentManager;
  private IComponentStore myComponentStore;
  private Boolean myHeadless;
  private ComponentsRegistry myComponentsRegistry = new ComponentsRegistry();
  private boolean myHaveProgressManager = false;

  protected ComponentManagerImpl(ComponentManager parentComponentManager) {
    myParentComponentManager = parentComponentManager;
    boostrapPicoContainer();
  }

  //todo[mike] there are several init* methods. Make it just 1
  public void init() {
    initComponents();
  }


  @NotNull
  public synchronized IComponentStore getStateStore() {
    if (myComponentStore == null) {
      assert myPicoContainer != null;
      myComponentStore = (IComponentStore)myPicoContainer.getComponentInstance(IComponentStore.class);
    }
    return myComponentStore;
  }

  public IComponentStore getComponentStore() {
    return getStateStore();
  }


  public MessageBus getMessageBus() {
    assert !myDisposeCompleted && !myDisposed : "Already disposed";
    assert myMessageBus != null : "Not initialized yet";
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  private void createComponents() {
    try {
      myComponentsRegistry.loadClasses();

      final Class[] componentInterfaces = myComponentsRegistry.getComponentInterfaces();
      for (Class componentInterface : componentInterfaces) {
        if (myHaveProgressManager) {
          ProgressManager.checkCanceled();
        }
        try {
          createComponent(componentInterface);
        }
        catch (StateStorage.StateStorageException e) {
          throw e;
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch(Exception e) {
          LOG.error(e);
        }
      }
    }
    finally {
      myComponentsCreated = true;
    }
  }

  private synchronized Object createComponent(Class componentInterface) {
    final Object component = getPicoContainer().getComponentInstance(componentInterface.getName());
    assert component != null : "Can't instantiate component for: " + componentInterface;
    return component;
  }

  protected void disposeComponents() {
    assert !myDisposeCompleted : "Already disposed!";

    final List<Object> components = myComponentsRegistry.getRegisteredImplementations();
    myDisposed = true;

    for (int i = components.size() - 1; i >= 0; i--) {
      Object component = components.get(i);
      if (component instanceof BaseComponent) {
        try {
          ((BaseComponent)component).disposeComponent();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myComponentsCreated = false;
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  protected <T> T getComponentFromContainer(Class<T> interfaceClass) {
    final T initializedComponent = (T)myInitializedComponents.get(interfaceClass);
    if (initializedComponent != null) return initializedComponent;

    //if (!myComponentsCreated) {
    //  LOG.error("Component requests are not allowed before they are created");
    //}

    synchronized (this) {
      if (!myComponentsRegistry.containsInterface(interfaceClass)) {
        return null;
      }

      Object lock = myComponentsRegistry.getComponentLock(interfaceClass);

      synchronized (lock) {
        T dcl = (T)myInitializedComponents.get(interfaceClass);
        if (dcl != null) return dcl;

        T component = (T)getPicoContainer().getComponentInstance(interfaceClass.getName());
        if (component == null) {
          component = (T)createComponent(interfaceClass);
        }

        if (component == null) {
          throw new IncorrectOperationException("createComponent() returns null for: " + interfaceClass);
        }

        myInitializedComponents.put(interfaceClass, component);

        if (component instanceof com.intellij.openapi.Disposable) {
          Disposer.register(this, (com.intellij.openapi.Disposable)component);
        }

        return component;
      }
    }
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    assert !myDisposeCompleted : "Already disposed";
    return getComponent(interfaceClass, null);
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    final T fromContainer = getComponentFromContainer(interfaceClass);
    if (fromContainer != null) return fromContainer;
    if (defaultImplementation != null) return defaultImplementation;
    return null;
  }

  private void initComponent(Object component) {
    if (myHaveProgressManager) {
      final ProgressManager progressManager = ProgressManager.getInstance();

      final ProgressIndicator indicator = progressManager != null ? progressManager.getProgressIndicator() : null;
      if (indicator != null) {
        String name = component instanceof BaseComponent ? ((BaseComponent)component).getComponentName() : component.getClass().getName();
        indicator.checkCanceled();
        indicator.setText2(name);
        indicator.setIndeterminate(false);
        indicator.setFraction(myComponentsRegistry.getPercentageOfComponentsLoaded());
      }
    }
    if (component instanceof ProgressManager) {
      myHaveProgressManager = true;
    }

    try {
      getStateStore().initComponent(component, false);
      if (component instanceof BaseComponent) {
        ((BaseComponent)component).initComponent();
      }
    }
    catch (StateStorage.StateStorageException e) {
      throw e;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable ex) {
      handleInitComponentError(ex, false, component.getClass().getName());
    }
  }


  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    LOG.error(ex);
  }

  public void registerComponent(Class interfaceClass, Class implementationClass) {
    registerComponent(interfaceClass, implementationClass, null);

  }

  @SuppressWarnings({"unchecked"})
  public void registerComponent(Class interfaceClass, Class implementationClass, Map options) {
    LOG.warn("Deprecated method usage: registerComponent", new Throwable());

    final ComponentConfig config = new ComponentConfig();
    config.implementationClass = implementationClass.getName();
    config.interfaceClass = interfaceClass.getName();
    config.options = options;
    registerComponent(config);
  }

  @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
  public synchronized void registerComponent(final ComponentConfig config, final IdeaPluginDescriptor pluginDescriptor) {
    if (isHeadless()) {
      String headlessImplClass = config.headlessImplementationClass;
      if (headlessImplClass != null) {
        if (headlessImplClass.trim().length() == 0) return;
        config.implementationClass = headlessImplClass;
      }
    }

    config.implementationClass = config.implementationClass.trim();

    if (config.interfaceClass == null) config.interfaceClass = config.implementationClass;
    config.interfaceClass = config.interfaceClass.trim();

    config.pluginDescriptor =  pluginDescriptor;
    myComponentsRegistry.registerComponent(config);
  }

  @NotNull
  public synchronized Class[] getComponentInterfaces() {
    LOG.warn("Deprecated method usage: getComponentInterfaces", new Throwable());
    return myComponentsRegistry.getComponentInterfaces();
  }

  public synchronized boolean hasComponent(@NotNull Class interfaceClass) {
    return myComponentsRegistry.containsInterface(interfaceClass);
  }

  protected synchronized Object[] getComponents() {
    Class[] componentClasses = myComponentsRegistry.getComponentInterfaces();
    ArrayList<Object> components = new ArrayList<Object>(componentClasses.length);
    for (Class<?> interfaceClass : componentClasses) {
      if (myHaveProgressManager) {
        ProgressManager.checkCanceled();
      }
      Object component = getComponent(interfaceClass);
      if (component != null) components.add(component);
    }
    return ArrayUtil.toObjectArray(components);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public synchronized <T> T[] getComponents(Class<T> baseClass) {
    return myComponentsRegistry.getComponentsByType(baseClass);
  }

  @NotNull
  public MutablePicoContainer getPicoContainer() {
    assert !myDisposeCompleted : "Already disposed";
    return myPicoContainer;
  }

  protected MutablePicoContainer createPicoContainer() {
    MutablePicoContainer result;

    if (myParentComponentManager != null) {
      result = new IdeaPicoContainer(myParentComponentManager.getPicoContainer());
    }
    else {
      result = new IdeaPicoContainer();
    }
    
    return result;
  }

  public synchronized BaseComponent getComponent(String name) {
    return myComponentsRegistry.getComponentByName(name);
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    return !isTrue(options, "internal") || ApplicationManagerEx.getApplicationEx().isInternal();
  }

  private static boolean isTrue(Map<String, String> options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option)).booleanValue();
  }

  public synchronized void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposeCompleted = true;

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myInitializedComponents.clear();
    myComponentsRegistry = null;
    myComponentStore = null;
    myPicoContainer = null;
  }

  public boolean isDisposed() {
    return myDisposed;
  }


  public void initComponents() {
    createComponents();
    getComponents();
  }

  protected void loadComponentsConfiguration(ComponentConfig[] components, @Nullable final IdeaPluginDescriptor descriptor, final boolean defaultProject) {
    myConfigurator.loadComponentsConfiguration(components, descriptor, defaultProject);
  }

  protected void boostrapPicoContainer() {
    myPicoContainer = createPicoContainer();

    myMessageBus = MessageBusFactory.newMessageBus(this, myParentComponentManager != null ? myParentComponentManager.getMessageBus() : null);
    final MutablePicoContainer picoContainer = getPicoContainer();
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
    /*
    picoContainer.registerComponentInstance(ExtensionInitializer.class, new ExtensionInitializer() {
      public void initExtension(final Object extension) {
        getComponentStore().initComponent(extension);
      }
    });
    */
  }


  protected ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }

  private boolean isHeadless() {
    if (myHeadless == null) {
      myHeadless = ApplicationManager.getApplication().isHeadlessEnvironment();
    }

    return myHeadless.booleanValue();
  }


  public void registerComponent(final ComponentConfig config) {
    registerComponent(config, null);
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    return myComponentsRegistry.getComponentConfigurations();
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    return getPicoContainer().getComponentInstance(componentConfig.interfaceClass);
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    return myComponentsRegistry.getConfig(componentImplementation);
  }

  private class ComponentsRegistry {
    private final Map<Class, Object> myInterfaceToLockMap = new HashMap<Class, Object>();
    private final Map<Class, Class> myInterfaceToClassMap = new HashMap<Class, Class>();
    private final ArrayList<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration
    private final Map<String, BaseComponent> myNameToComponent = new HashMap<String, BaseComponent>();
    private final List<ComponentConfig> myComponentConfigs = new ArrayList<ComponentConfig>();
    private final List<Object> myImplementations = new ArrayList<Object>();
    private final Map<Class, ComponentConfig> myComponentClassToConfig = new java.util.HashMap<Class, ComponentConfig>();
    private boolean myClassesLoaded = false;

    private void loadClasses() {
      assert !myClassesLoaded;

      for (ComponentConfig config : myComponentConfigs) {
        loadClasses(config);
      }

      myClassesLoaded = true;
    }

    private void loadClasses(final ComponentConfig config) {
      ClassLoader loader = config.getClassLoader();

      try {
        final Class<?> interfaceClass = Class.forName(config.interfaceClass, true, loader);
        final Class<?> implementationClass = Class.forName(config.implementationClass, true, loader);

        if (myInterfaceToClassMap.get(interfaceClass) != null) {
          throw new Error("ComponentSetup for component " + interfaceClass.getName() + " already registered");
        }

        getPicoContainer().registerComponent(new ComponentConfigComponentAdapter(config));
        myInterfaceToClassMap.put(interfaceClass, implementationClass);
        myComponentClassToConfig.put(implementationClass, config);
        myComponentInterfaces.add(interfaceClass);
      }
      catch (Exception e) {
        @NonNls final String message = "Error while registering component: " + config;

        if (config.pluginDescriptor != null) {
          LOG.error(message, new PluginException(e, config.pluginDescriptor.getPluginId()));
        }
        else {
          LOG.error(message, e);
        }
      }
      catch (Error e) {
        if (config.pluginDescriptor != null) {
          LOG.error(new PluginException(e, config.pluginDescriptor.getPluginId()));
        }
        else {
          throw e;
        }
      }
    }

    private Object getComponentLock(final Class componentClass) {
      Object lock = myInterfaceToLockMap.get(componentClass);
      if (lock == null) {
        myInterfaceToLockMap.put(componentClass, lock = new Object());
      }
      return lock;
    }

    private Class[] getComponentInterfaces() {
      assert myClassesLoaded;
      return myComponentInterfaces.toArray(new Class[myComponentInterfaces.size()]);
    }

    private boolean containsInterface(final Class interfaceClass) {
      if (!myClassesLoaded) loadClasses();
      return myInterfaceToClassMap.containsKey(interfaceClass);
    }

    public double getPercentageOfComponentsLoaded() {
      return ((double)myImplementations.size()) / myComponentConfigs.size();
    }

    private void registerComponentInstance(final Object component) {
      myImplementations.add(component);

      if (component instanceof BaseComponent) {
        BaseComponent baseComponent = (BaseComponent)component;
        final String componentName = baseComponent.getComponentName();

        if (myNameToComponent.containsKey(componentName)) {
          BaseComponent loadedComponent = myNameToComponent.get(componentName);
          // component may have been already loaded by PicoContainer, so fire error only if components are really different
          if (!component.equals(loadedComponent)) {
            LOG.error("Component name collision: " + componentName + " " + loadedComponent.getClass() + " and " + component.getClass());
          }
        }
        else {
          myNameToComponent.put(componentName, baseComponent);
        }
      }
    }

    public List<Object> getRegisteredImplementations() {
      return myImplementations;
    }

    private void registerComponent(ComponentConfig config) {
      myComponentConfigs.add(config);

      if (myClassesLoaded) {
        loadClasses(config);
      }
    }

    private BaseComponent getComponentByName(final String name) {
      return myNameToComponent.get(name);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T[] getComponentsByType(final Class<T> baseClass) {
      ArrayList<T> array = new ArrayList<T>();

      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < myComponentInterfaces.size(); i++) {
        Class interfaceClass = myComponentInterfaces.get(i);
        final Class implClass = myInterfaceToClassMap.get(interfaceClass);
        if (ReflectionCache.isAssignable(baseClass, implClass)) {
          array.add((T)getComponent(interfaceClass));
        }
      }

      return array.toArray((T[])Array.newInstance(baseClass, array.size()));
    }

    public ComponentConfig[] getComponentConfigurations() {
        return myComponentConfigs.toArray(new ComponentConfig[myComponentConfigs.size()]);
    }

    public ComponentConfig getConfig(final Class componentImplementation) {
      return myComponentClassToConfig.get(componentImplementation);
    }
  }

  private class ComponentConfigComponentAdapter implements ComponentAdapter {
    private final ComponentConfig myConfig;
    private ComponentAdapter myDelegate;
    private boolean myInitialized = false;
    private boolean myInitializing = false;

    public ComponentConfigComponentAdapter(final ComponentConfig config) {
      myConfig = config;
    }

    public Object getComponentKey() {
      return myConfig.interfaceClass;
    }

    public Class getComponentImplementation() {
      return getDelegate().getComponentImplementation();
    }

    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      return getDelegate().getComponentInstance(container);
    }

    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
      getDelegate().accept(visitor);
    }

    private ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        final Object componentKey = getComponentKey();

        ClassLoader loader = myConfig.getClassLoader();

        Class<?> implementationClass = null;

        try {
          implementationClass = Class.forName(myConfig.implementationClass, true, loader);
        }
        catch (Exception e) {
          @NonNls final String message = "Error while registering component: " + myConfig;

          if (myConfig.pluginDescriptor != null) {
            LOG.error(message, new PluginException(e, myConfig.pluginDescriptor.getPluginId()));
          }
          else {
            LOG.error(message, e);
          }
        }
        catch (Error e) {
          if (myConfig.pluginDescriptor != null) {
            LOG.error(new PluginException(e, myConfig.pluginDescriptor.getPluginId()));
          }
          else {
            throw e;
          }
        }

        assert implementationClass != null;

        myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, implementationClass, null, true)) {
            public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException {
              Object componentInstance = null;
              try {
                long startTime = myInitialized ? 0 : System.nanoTime();
                componentInstance = super.getComponentInstance(picoContainer);

                if (!myInitialized) {
                  if (myInitializing) LOG.error(new Throwable("Cyclic component initialization: " + componentKey));
                  myInitializing = true;
                  myComponentsRegistry.registerComponentInstance(componentInstance);
                  initComponent(componentInstance);
                  long endTime = System.nanoTime();
                  long ms = (endTime - startTime) / 1000000;
                  if (ms > 10) {
                    if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
                      LOG.info(componentInstance.getClass().getName() + " initialized in " + ms + " ms");
                    }
                    else if (LOG.isDebugEnabled()) {
                      LOG.debug(componentInstance.getClass().getName() + " initialized in " + ms + " ms");
                    }
                  }
                  myInitializing = false;
                  myInitialized = true;
                }
              }
              catch (ProcessCanceledException e) {
                throw e;
              }
              catch (StateStorage.StateStorageException e) {
                throw e;
              }
              catch (Throwable t) {
                handleInitComponentError(t, componentInstance == null, componentKey.toString());
              }
              return componentInstance;
            }
          };
      }

      return myDelegate;
    }
  }

  protected void doSave() throws IOException {
    IComponentStore.SaveSession session = null;
    try {
      session = getStateStore().startSave();
      session.save();
    }
    finally {
      if (session != null) {
        session.finishSave();
      }
    }
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public final boolean equals(Object obj) {
    return super.equals(obj);
  }
}
