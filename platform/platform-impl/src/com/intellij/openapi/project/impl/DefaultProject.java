// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.impl.MessageBusImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

import java.util.List;

/**
 * @author peter
 */
final class DefaultProject extends UserDataHolderBase implements Project, ProjectStoreOwner {
  private static final Logger LOG = Logger.getInstance(DefaultProject.class);

  private final DefaultProjectTimed myDelegate = new DefaultProjectTimed(this) {
    @NotNull
    @Override
    ProjectEx compute() {
      LOG.assertTrue(!ApplicationManager.getApplication().isDisposed(), "Application is being disposed!");
      return new ProjectImpl() {
        @Override
        public boolean isDefault() {
          return true;
        }

        @Override
        public boolean isInitialized() {
          return true; // no startup activities, never opened
        }

        @Override
        protected @NotNull ComponentManager getActualContainerInstance() {
          return DefaultProject.this;
        }

        @Nullable
        @Override
        public String activityNamePrefix() {
          // exclude from measurement because default project initialization is not a sequential activity
          // (so, complicates timeline because not applicable)
          // for now we don't measure default project initialization at all, because it takes only ~10 ms
          return null;
        }

        @Override
        protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
          return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
        }

        @Override
        public void init(@Nullable ProgressIndicator indicator) {
          // do not leak internal delegate, use DefaultProject everywhere instead
          registerServiceInstance(Project.class, DefaultProject.this, ComponentManagerImpl.getFakeCorePluginDescriptor());

          //noinspection unchecked
          registerComponents((List<IdeaPluginDescriptorImpl>)PluginManagerCore.getLoadedPlugins(), false);
          createComponents(null);
          Disposer.register(DefaultProject.this, this);
        }

        @Override
        public String toString() {
          return "Project" +
                 (isDisposed() ? " (Disposed)" : "") +
                 TEMPLATE_PROJECT_NAME;
        }

        @Override
        public boolean equals(Object o) {
          return o instanceof Project && ((Project)o).isDefault();
        }

        @Override
        public int hashCode() {
          return DEFAULT_HASH_CODE;
        }

        @NotNull
        @Override
        protected synchronized MessageBusImpl getOrCreateMessageBusUnderLock() {
          MessageBusImpl messageBus = super.getOrCreateMessageBusUnderLock();
          messageBus.setIgnoreParentLazyListeners(true);
          return messageBus;
        }
      };
    }

    @Override
    void init(Project project) {
      ((ProjectImpl)project).init(null);
    }
  };
  private static final int DEFAULT_HASH_CODE = 4; // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.

  DefaultProject() {
  }

  @Override
  public <T> @NotNull T instantiateExtensionWithPicoContainerOnlyIfNeeded(@Nullable String name,
                                                                          @Nullable PluginDescriptor pluginDescriptor) {
    return getDelegate().instantiateExtensionWithPicoContainerOnlyIfNeeded(name, pluginDescriptor);
  }

  @Override
  public <T> T instantiateClass(@NotNull Class<T> aClass, @Nullable PluginId pluginId) {
    return getDelegate().instantiateClass(aClass, pluginId);
  }

  @Override
  public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @NotNull PluginId pluginId) {
    return getDelegate().instantiateClassWithConstructorInjection(aClass, key, pluginId);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull String message, @NotNull PluginId pluginId) {
    return getDelegate().createError(message, pluginId);
  }

  @Override
  public void logError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    getDelegate().logError(error, pluginId);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    return getDelegate().createError(error, pluginId);
  }

  @Override
  public boolean hasComponent(@NotNull Class<?> interfaceClass) {
    return getDelegate().hasComponent(interfaceClass);
  }

  // make default project facade equal to any other default project facade
  // to enable Map<Project, T>
  @Override
  public boolean equals(Object o) {
    return o instanceof Project && ((Project)o).isDefault();
  }

  @Override
  public int hashCode() {
    return DEFAULT_HASH_CODE;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
  }

  @NotNull
  private ProjectEx getDelegate() {
    return myDelegate.get();
  }

  public boolean isCached() {
    return myDelegate.isCached();
  }

  // delegates
  @Override
  @NotNull
  public String getName() {
    return ProjectImpl.TEMPLATE_PROJECT_NAME;
  }

  @Override
  @Deprecated
  public VirtualFile getBaseDir() {
    return null;
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getBasePath() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return getName();
  }

  @Override
  public void save() {
    getDelegate().save();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @SuppressWarnings("deprecation")
  @Override
  @Deprecated
  public BaseComponent getComponent(@NotNull String name) {
    return getDelegate().getComponent(name);
  }

  @SuppressWarnings("deprecation")
  @NotNull
  @Override
  public <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass, boolean createIfNotYet) {
    return getDelegate().getComponentInstancesOfType(baseClass, createIfNotYet);
  }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    return getDelegate().getService(serviceClass);
  }

  @Nullable
  @Override
  public <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return getDelegate().getServiceIfCreated(serviceClass);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return getDelegate().getComponent(interfaceClass);
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    return getDelegate().getPicoContainer();
  }

  @NotNull
  @Override
  public ExtensionsArea getExtensionArea() {
    return getDelegate().getExtensionArea();
  }

  @Override
  @NotNull
  public MessageBus getMessageBus() {
    return getDelegate().getMessageBus();
  }

  @Override
  public boolean isDisposed() {
    return ApplicationManager.getApplication().isDisposed();
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return ApplicationManager.getApplication().getDisposed();
  }

  @NotNull
  @Override
  public IComponentStore getComponentStore() {
    return ((ProjectStoreOwner)getDelegate()).getComponentStore();
  }
}
