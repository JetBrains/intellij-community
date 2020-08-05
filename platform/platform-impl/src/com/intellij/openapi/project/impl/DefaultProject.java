// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

import java.util.List;

/**
 * @author peter
 */
final class DefaultProject extends UserDataHolderBase implements Project {
  private static final Logger LOG = Logger.getInstance(DefaultProject.class);

  private final DefaultProjectTimed myDelegate = new DefaultProjectTimed(this) {
    @Override
    @NotNull Project compute() {
      LOG.assertTrue(!ApplicationManager.getApplication().isDisposed(), "Application is being disposed!");
      DefaultProjectImpl project = new DefaultProjectImpl(DefaultProject.this);
      ProjectStoreFactory componentStoreFactory = ApplicationManager.getApplication().getService(ProjectStoreFactory.class);
      project.registerServiceInstance(IComponentStore.class, componentStoreFactory.createDefaultProjectStore(project), ComponentManagerImpl.getFakeCorePluginDescriptor());
      return project;
    }

    @Override
    void init(@NotNull Project project) {
      ((DefaultProjectImpl)project).init();
    }
  };

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
    return DefaultProjectImpl.DEFAULT_HASH_CODE;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
  }

  private @NotNull Project getDelegate() {
    return myDelegate.get();
  }

  public boolean isCached() {
    return myDelegate.isCached();
  }

  // delegates
  @Override
  public @NotNull String getName() {
    return DefaultProjectImpl.TEMPLATE_PROJECT_NAME;
  }

  @Override
  @Deprecated
  public VirtualFile getBaseDir() {
    return null;
  }

  @Override
  public @Nullable @SystemIndependent String getBasePath() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getProjectFile() {
    return null;
  }

  @Override
  public @Nullable @SystemIndependent String getProjectFilePath() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  public @NotNull String getLocationHash() {
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
  @Override
  public @NotNull <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass, boolean createIfNotYet) {
    return getDelegate().getComponentInstancesOfType(baseClass, createIfNotYet);
  }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    return getDelegate().getService(serviceClass);
  }

  @Override
  public @Nullable <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return getDelegate().getServiceIfCreated(serviceClass);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return getDelegate().getComponent(interfaceClass);
  }

  @Override
  public @NotNull PicoContainer getPicoContainer() {
    return getDelegate().getPicoContainer();
  }

  @Override
  public @NotNull ExtensionsArea getExtensionArea() {
    return getDelegate().getExtensionArea();
  }

  @Override
  public @NotNull MessageBus getMessageBus() {
    return getDelegate().getMessageBus();
  }

  @Override
  public boolean isDisposed() {
    return ApplicationManager.getApplication().isDisposed();
  }

  @Override
  public @NotNull Condition<?> getDisposed() {
    return ApplicationManager.getApplication().getDisposed();
  }
}

final class DefaultProjectImpl extends ComponentManagerImpl implements Project {
  private static final Logger LOG = Logger.getInstance(DefaultProjectImpl.class);
  static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.
  static final int DEFAULT_HASH_CODE = 4;

  private final Project actualContainerInstance;

  DefaultProjectImpl(@NotNull Project actualContainerInstance) {
    super((ComponentManagerImpl)ApplicationManager.getApplication());

    this.actualContainerInstance = actualContainerInstance;
  }

  @Override
  public boolean isParentLazyListenersIgnored() {
    return true;
  }

  @Override
  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
  }

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
    return actualContainerInstance;
  }

  @Override
  public @Nullable String activityNamePrefix() {
    // exclude from measurement because default project initialization is not a sequential activity
    // (so, complicates timeline because not applicable)
    // for now we don't measure default project initialization at all, because it takes only ~10 ms
    return null;
  }

  @Override
  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
  }

  public void init() {
    // do not leak internal delegate, use DefaultProject everywhere instead
    registerServiceInstance(Project.class, actualContainerInstance, ComponentManagerImpl.getFakeCorePluginDescriptor());

    //noinspection unchecked
    registerComponents((List<IdeaPluginDescriptorImpl>)PluginManagerCore.getLoadedPlugins());
    createComponents(null);
    Disposer.register(actualContainerInstance, this);
  }

  @Override
  public String toString() {
    return "Project" + (isDisposed() ? " (Disposed)" : "") + TEMPLATE_PROJECT_NAME;
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
  protected ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.getProject();
  }

  @Override
  public @NotNull String getName() {
    return TEMPLATE_PROJECT_NAME;
  }

  @Override
  public VirtualFile getBaseDir() {
    return null;
  }

  @Override
  public @Nullable @SystemIndependent String getBasePath() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getProjectFile() {
    return null;
  }

  @Override
  public @Nullable @SystemIndependent String getProjectFilePath() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  public @NotNull String getLocationHash() {
    return Integer.toHexString(TEMPLATE_PROJECT_NAME.hashCode());
  }

  @Override
  public void save() {
    LOG.error("Do not call save for default project");
    if (ApplicationManagerEx.getApplicationEx().isSaveAllowed()) {
      // no need to save
      StoreUtil.saveSettings(this, false);
    }
  }

  @Override
  public boolean isOpen() {
    return false;
  }
}
