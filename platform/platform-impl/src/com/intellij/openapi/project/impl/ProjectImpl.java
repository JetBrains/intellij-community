// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.idea.ApplicationLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectServiceContainerCustomizer;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.serviceContainer.PlatformComponentManagerImpl;
import com.intellij.util.PathUtil;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx, ProjectStoreOwner {
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

  private final AtomicNotNullLazyValue<IComponentStore> myComponentStore = AtomicNotNullLazyValue.createValue(() -> {
    ProjectStoreFactory factory = myProjectStoreFactory != null ? myProjectStoreFactory : ServiceManager.getService(ProjectStoreFactory.class);
    return factory.createStore(this);
  });

  protected ProjectImpl(@NotNull Path filePath, @Nullable String projectName) {
    super((PlatformComponentManagerImpl)ApplicationManager.getApplication());

    putUserData(CREATION_TIME, System.nanoTime());
    creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? DebugUtil.currentStackTrace() : null;

    getPicoContainer().registerComponentInstance(Project.class, this);

    myName = projectName;
    // light project may be changed later during test, so we need to remember its initial state
    //noinspection TestOnlyProblems
    myLight = ApplicationManager.getApplication().isUnitTestMode() && filePath.toString().contains(LIGHT_PROJECT_NAME);
  }

  static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  // default project constructor
  ProjectImpl() {
    super((PlatformComponentManagerImpl)ApplicationManager.getApplication());

    putUserData(CREATION_TIME, System.nanoTime());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      putUserData(CREATION_TRACE, DebugUtil.currentStackTrace());
    }

    creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? DebugUtil.currentStackTrace() : null;

    myName = TEMPLATE_PROJECT_NAME;
    myLight = false;
  }

  @Override
  public boolean isDisposed() {
    return super.isDisposedOrDisposeInProgress() || temporarilyDisposed;
  }

  @Override
  public boolean isDisposedOrDisposeInProgress() {
    return super.isDisposedOrDisposeInProgress() || temporarilyDisposed;
  }

  @Override
  @TestOnly
  public boolean isLight() {
    return myLight;
  }

  private volatile boolean temporarilyDisposed;
  @TestOnly
  void setTemporarilyDisposed(boolean disposed) {
    temporarilyDisposed = disposed;
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
    if (!projectName.equals(myName)) {
      myName = projectName;

      StartupManager.getInstance(this).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
        if (isDisposed()) return;

        JFrame frame = WindowManager.getInstance().getFrame(this);
        String title = FrameTitleBuilder.getInstance().getProjectTitle(this);
        if (frame != null && title != null) {
          frame.setTitle(title);
        }
      });
    }
  }

  // do not call for default project
  @NotNull
  public final IProjectStore getStateStore() {
    return (IProjectStore)getComponentStore();
  }

  @Override
  @NotNull
  public IComponentStore getComponentStore() {
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

  @NotNull
  @Override
  protected ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.getProject();
  }

  @Nullable
  @Override
  public @SystemIndependent String getProjectFilePath() {
    return getStateStore().getProjectFilePath();
  }

  @Override
  public VirtualFile getProjectFile() {
    return LocalFileSystem.getInstance().findFileByPath(getStateStore().getProjectFilePath());
  }

  @Override
  public VirtualFile getBaseDir() {
    return LocalFileSystem.getInstance().findFileByPath(getStateStore().getProjectBasePath());
  }

  @Override
  @Nullable
  public @SystemIndependent String getBasePath() {
    return getStateStore().getProjectBasePath();
  }

  @NotNull
  @Override
  public String getName() {
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

  @NotNull
  @NonNls
  @Override
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) {
      str = getName();
    }

    final String prefix = getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED ? "" : getName();
    return prefix + Integer.toHexString(str.hashCode());
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    String workspaceFilePath = getStateStore().getWorkspaceFilePath();
    return workspaceFilePath == null ? null : LocalFileSystem.getInstance().findFileByPath(workspaceFilePath);
  }

  public final void registerComponents() {
    String activityNamePrefix = activityNamePrefix();
    Activity activity = (activityNamePrefix == null || !StartUpMeasurer.isEnabled()) ? null : StartUpMeasurer.startMainActivity(activityNamePrefix + Phases.REGISTER_COMPONENTS_SUFFIX);
    //  at this point of time plugins are already loaded by application - no need to pass indicator to getLoadedPlugins call
    //noinspection unchecked
    registerComponents((List<IdeaPluginDescriptorImpl>)PluginManagerCore.getLoadedPlugins(), false);
    if (activity != null) {
      activity = activity.endAndStart("projectComponentRegistered");
    }

    ProjectServiceContainerCustomizer.getEp().processWithPluginDescriptor((customizer, pluginDescriptor) -> {
      if (pluginDescriptor.getPluginId() != PluginManagerCore.CORE_ID) {
        LOG.error("Plugin " + pluginDescriptor + " is not approved to add ProjectServiceContainerCustomizer");
      }

      try {
        customizer.serviceContainerInitialized(this);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    });

    if (activity != null) {
      activity.end();
    }
  }

  public void init(@Nullable ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();

    // before components
    CompletableFuture<?> servicePreloadingFuture;
    //noinspection TestOnlyProblems
    if (isLight()) {
      servicePreloadingFuture = CompletableFuture.completedFuture(null);
    }
    else {
      //noinspection rawtypes
      List plugins = PluginManagerCore.getLoadedPlugins();
      Executor executor = ApplicationLoader.createExecutorToPreloadServices();
      //noinspection unchecked
      servicePreloadingFuture = ApplicationLoader.preloadServices(plugins, this, executor, /* activityPrefix = */ "project ");
    }

    createComponents(indicator);

    servicePreloadingFuture.join();

    if (indicator != null && !application.isHeadlessEnvironment()) {
      distributeProgress(indicator);
    }

    if (myName == null) {
      myName = getStateStore().getProjectName();
    }
    application.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsInitialized(this);
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
    EditorsSplitters splitters = ((FileEditorManagerImpl)FileEditorManager.getInstance(this)).getMainSplitters();
    int editors = splitters.getEditorsCount();

    double modulesPart = ourClassesAreLoaded || editors == 0 ? toDistribute : toDistribute * 0.5;
    if (modulesCount != 0) {

      double step = modulesPart / modulesCount;
      ((ModuleManagerImpl)moduleManager).setProgressStep(step);
    }

    if (editors != 0) {
      splitters.setProgressStep(toDistribute - modulesPart / editors);
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
    setDisposeInProgress();

    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();  // dispose must be under write action

    // can call dispose only via com.intellij.ide.impl.ProjectUtil.closeAndDispose()
    if (ProjectManagerEx.getInstanceEx().isProjectOpened(this)) {
      throw new IllegalStateException("Must call .dispose() for a closed project only. See ProjectManager.closeProject() or ProjectUtil.closeAndDispose().");
    }

    LOG.assertTrue(myContainerState.ordinal() <= ContainerState.DISPOSED.ordinal(), this + " is disposed already");
    disposeComponents();

    super.dispose();

    if (!application.isDisposed()) {
      application.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).afterProjectClosed(this);
    }
    ((ProjectManagerImpl)ProjectManager.getInstance()).updateTheOnlyProjectField();

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
    return "Project (name=" + myName + ", containerState=" + myContainerState.name() +
           ", componentStore=" + (myComponentStore.isComputed() ? getPresentableUrl() : "<not initialized>") + ") " +
           (temporarilyDisposed ? " (disposed" + " temporarily)" : "");
  }

  @TestOnly
  public String getCreationTrace() {
    return creationTrace;
  }

  @Nullable
  @ApiStatus.Internal
  @Override
  public String activityNamePrefix() {
    return "project ";
  }

  @ApiStatus.Internal
  public final void setDisposeInProgress() {
    myContainerState = ContainerState.DISPOSE_IN_PROGRESS;
  }
}
