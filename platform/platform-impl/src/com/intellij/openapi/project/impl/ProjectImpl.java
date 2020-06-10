// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.idea.ApplicationLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.PathUtil;
import com.intellij.util.TimedReference;
import com.intellij.util.messages.impl.MessageBusEx;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ProjectImpl extends ComponentManagerImpl implements ProjectEx, ProjectStoreOwner {
  private static final Logger LOG = Logger.getInstance(ProjectImpl.class);

  public static final Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");

  public static final Key<String> CREATION_TRACE = Key.create("ProjectImpl.CREATION_TRACE");

  @TestOnly
  public static final String LIGHT_PROJECT_NAME = "light_temp";

  private String myName;
  private final boolean myLight;
  static boolean ourClassesAreLoaded;
  private final String creationTrace;
  private ProjectStoreFactory myProjectStoreFactory;

  private final AtomicReference<Disposable> earlyDisposable = new AtomicReference<>(Disposer.newDisposable());
  private volatile boolean temporarilyDisposed;

  private final AtomicNotNullLazyValue<IComponentStore> myComponentStore = AtomicNotNullLazyValue.createValue(() -> {
    ProjectStoreFactory factory = myProjectStoreFactory != null ? myProjectStoreFactory : ServiceManager.getService(ProjectStoreFactory.class);
    return factory.createStore(this);
  });

  protected ProjectImpl(@NotNull Path filePath, @Nullable String projectName) {
    super((ComponentManagerImpl)ApplicationManager.getApplication());

    putUserData(CREATION_TIME, System.nanoTime());
    creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? DebugUtil.currentStackTrace() : null;

    registerServiceInstance(Project.class, this, ComponentManagerImpl.getFakeCorePluginDescriptor());

    myName = projectName;
    // light project may be changed later during test, so we need to remember its initial state
    //noinspection TestOnlyProblems
    myLight = ApplicationManager.getApplication().isUnitTestMode() && filePath.toString().contains(LIGHT_PROJECT_NAME);
  }

  static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  // default project constructor
  ProjectImpl() {
    super((ComponentManagerImpl)ApplicationManager.getApplication());

    putUserData(CREATION_TIME, System.nanoTime());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      putUserData(CREATION_TRACE, DebugUtil.currentStackTrace());
    }

    creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? DebugUtil.currentStackTrace() : null;

    myName = TEMPLATE_PROJECT_NAME;
    myLight = false;
  }

  @Override
  public final boolean isDisposed() {
    return super.isDisposed() || temporarilyDisposed;
  }

  @Override
  @TestOnly
  public boolean isLight() {
    return myLight;
  }

  @TestOnly
  void setTemporarilyDisposed(boolean value) {
    if (temporarilyDisposed == value) {
      return;
    }

    if (value && super.isDisposed()) {
      throw new IllegalStateException("Project was already disposed, flag temporarilyDisposed cannot be set to `true`");
    }

    if (!value) {
      Disposable newDisposable = Disposer.newDisposable();
      if (!earlyDisposable.compareAndSet(null, newDisposable)) {
        throw new IllegalStateException("earlyDisposable must be null on second opening of light project");
      }
    }

    // Must be not only on temporarilyDisposed = true, but also on temporarilyDisposed = false,
    // because events fired for temporarilyDisposed project between project close and project open and it can lead to cache population.
    // Message bus implementation can be complicated to add owner.isDisposed check before getting subscribers, but as bus is a very important subsystem,
    // better to not add any non-production logic

    // light project is not disposed, so, subscriber cache contains handlers that will handle events for a temporarily disposed project,
    // so, we clear subscriber cache. `isDisposed` for project returns `true` if `temporarilyDisposed`, so, handler will be not added.
    ((MessageBusEx)getMessageBus()).clearAllSubscriberCache();

    temporarilyDisposed = value;
  }

  @TestOnly
  boolean isTemporarilyDisposed() {
    return temporarilyDisposed;
  }

  /**
   * This method is temporary introduced to allow overriding project store class for a specific project. Overriding ProjectStoreFactory
   * service won't work because a service may be overridden in a single plugin only.
   */
  @ApiStatus.Internal
  public void setProjectStoreFactory(ProjectStoreFactory projectStoreFactory) {
    myProjectStoreFactory = projectStoreFactory;
  }

  @Override
  public void setProjectName(@NotNull String projectName) {
    if (projectName.equals(myName)) {
      return;
    }

    myName = projectName;

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      StartupManager.getInstance(this).runAfterOpened(() -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          JFrame frame = WindowManager.getInstance().getFrame(this);
          String title = FrameTitleBuilder.getInstance().getProjectTitle(this);
          if (frame != null && title != null) {
            frame.setTitle(title);
          }
        }, ModalityState.NON_MODAL, getDisposed());
      });
    }
  }

  // do not call for default project
  public final @NotNull IProjectStore getStateStore() {
    return (IProjectStore)getComponentStore();
  }

  @Override
  public @NotNull IComponentStore getComponentStore() {
    return myComponentStore.getValue();
  }

  @Override
  public boolean isOpen() {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
    return projectManager != null && projectManager.isProjectOpened(this);
  }

  @Override
  public boolean isInitialized() {
    return getComponentCreated() && !isDisposed() && isOpen() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @Override
  protected @NotNull ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.getProject();
  }

  @Override
  public @Nullable @SystemIndependent String getProjectFilePath() {
    return getStateStore().getProjectFilePath();
  }

  @Override
  public VirtualFile getProjectFile() {
    return LocalFileSystem.getInstance().findFileByPath(getStateStore().getProjectFilePath());
  }

  @Override
  public VirtualFile getBaseDir() {
    return LocalFileSystem.getInstance().findFileByPath(getBasePath());
  }

  @Override
  public @NotNull @SystemIndependent String getBasePath() {
    return getStateStore().getProjectBasePath();
  }

  @Override
  public @NotNull String getName() {
    if (myName == null) {
      return getStateStore().getProjectName();
    }
    return myName;
  }

  @Override
  public @SystemDependent String getPresentableUrl() {
    IProjectStore store = getStateStore();
    return PathUtil.toSystemDependentName(store.getStorageScheme() == StorageScheme.DIRECTORY_BASED ? store.getProjectBasePath() : store.getProjectFilePath());
  }

  @NonNls
  @Override
  public @NotNull String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) {
      str = getName();
    }

    final String prefix = getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED ? "" : getName();
    return prefix + Integer.toHexString(str.hashCode());
  }

  @Override
  public @Nullable VirtualFile getWorkspaceFile() {
    String workspaceFilePath = getStateStore().getWorkspaceFilePath();
    return workspaceFilePath == null ? null : LocalFileSystem.getInstance().findFileByPath(workspaceFilePath);
  }

  public void init(@Nullable ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();

    // before components
    CompletableFuture<?> servicePreloadingFuture;
    //noinspection rawtypes
    List plugins = PluginManagerCore.getLoadedPlugins();
    // for light project preload only services that are essential (await means "project component loading activity is completed only when all such services are completed")
    //noinspection TestOnlyProblems,unchecked
    servicePreloadingFuture = ApplicationLoader
      .preloadServices(plugins, this, /* activityPrefix = */ "project ", /* onlyIfAwait = */ isLight());

    createComponents(indicator);

    servicePreloadingFuture.join();

    if (indicator != null && !application.isHeadlessEnvironment()) {
      distributeProgress(indicator);
    }

    if (myName == null) {
      myName = getStateStore().getProjectName();
    }

    ProjectLoadHelper.notifyThatComponentCreated(this);
  }

  @Override
  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded() / (ourClassesAreLoaded ? 10 : 2));
  }

  private void distributeProgress(@NotNull ProgressIndicator indicator) {
    ModuleManager moduleManager = ModuleManager.getInstance(this);
    if (!(moduleManager instanceof ModuleManagerImpl)) {
      return;
    }

    double toDistribute = 1 - indicator.getFraction();
    int modulesCount = ((ModuleManagerImpl)moduleManager).getModulePathsCount();
    if (modulesCount != 0) {
      ((ModuleManagerImpl)moduleManager).setProgressStep(toDistribute / modulesCount);
    }
  }

  @Override
  public void save() {
    if (!ApplicationManagerEx.getApplicationEx().isSaveAllowed()) {
      // no need to save
      return;
    }

    // ensure that expensive save operation is not performed before startupActivityPassed
    // first save may be quite cost operation, because cache is not warmed up yet
    if (!isInitialized()) {
      LOG.debug("Skip save for " + getName() + ": not initialized");
      return;
    }

    StoreUtil.saveSettings(this, false);
  }

  @Override
  public synchronized void dispose() {
    Application app = ApplicationManager.getApplication();
    // dispose must be under write action
    app.assertWriteAccessAllowed();

    ProjectManagerImpl projectManager = (ProjectManagerImpl)ProjectManager.getInstance();

    // can call dispose only via com.intellij.ide.impl.ProjectUtil.closeAndDispose()
    if (projectManager.isProjectOpened(this)) {
      throw new IllegalStateException("Must call .dispose() for a closed project only. See ProjectManager.closeProject() or ProjectUtil.closeAndDispose().");
    }

    super.dispose();

    if (myComponentStore.isComputed()) {
      myComponentStore.getValue().release();
    }

    if (!app.isDisposed()) {
      //noinspection deprecation
      app.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).afterProjectClosed(this);
    }
    projectManager.updateTheOnlyProjectField();

    TimedReference.disposeTimed();
    LaterInvocator.purgeExpiredItems();
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @NonNls
  @Override
  public String toString() {
    return "Project (name=" + myName + ", containerState=" + getContainerStateName() +
           ", componentStore=" + (myComponentStore.isComputed() ? getPresentableUrl() : "<not initialized>") + ") " +
           (temporarilyDisposed ? " (disposed" + " temporarily)" : "");
  }

  @TestOnly
  public String getCreationTrace() {
    return creationTrace;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable String activityNamePrefix() {
    return "project ";
  }

  @Override
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public final @NotNull Disposable getEarlyDisposable() {
    if (isDisposed()) {
      throw new AlreadyDisposedException(this + " is disposed already");
    }

    // maybe null only if disposed, but this condition is checked above
    Disposable disposable = earlyDisposable.get();
    if (disposable == null) {
      throw new IllegalStateException("earlyDisposable is null for " + this);
    }
    return disposable;
  }

  @ApiStatus.Internal
  public final void disposeEarlyDisposable() {
    Disposable disposable = earlyDisposable.getAndSet(null);
    Disposer.dispose(disposable);
  }
}
