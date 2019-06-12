// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.PlatformComponentManagerImpl;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
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
import com.intellij.util.PathUtil;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.*;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.util.List;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx, ProjectStoreOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  public static final String NAME_FILE = ".name";
  public static final Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");
  @Deprecated
  public static final Key<String> CREATION_TRACE = Key.create("ProjectImpl.CREATION_TRACE");
  @TestOnly
  public static final String LIGHT_PROJECT_NAME = "light_temp";

  private String myName;
  private final boolean myLight;
  static boolean ourClassesAreLoaded;
  private final String creationTrace;

  private final AtomicNotNullLazyValue<IComponentStore> myComponentStore = AtomicNotNullLazyValue.createValue(() -> {
    //noinspection CodeBlock2Expr
    return ServiceManager.getService(ProjectStoreFactory.class).createStore(this);
  });

  /**
   * @param filePath System-independent path
   */
  protected ProjectImpl(@NotNull String filePath, @Nullable String projectName) {
    super(ApplicationManager.getApplication(), "Project " + (projectName == null ? filePath : projectName));

    putUserData(CREATION_TIME, System.nanoTime());
    creationTrace = ApplicationManager.getApplication().isUnitTestMode() ? DebugUtil.currentStackTrace() : null;

    getPicoContainer().registerComponentInstance(Project.class, this);

    myName = projectName;
    // light project may be changed later during test, so we need to remember its initial state
    myLight = ApplicationManager.getApplication().isUnitTestMode() && filePath.contains(LIGHT_PROJECT_NAME);
  }

  static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";
  // default project constructor
  ProjectImpl() {
    super(ApplicationManager.getApplication(), TEMPLATE_PROJECT_NAME);

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
    return super.isDisposed() || temporarilyDisposed;
  }

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

  @Override
  protected void bootstrapPicoContainer(@NotNull String name) {
    Extensions.instantiateArea(ExtensionAreas.IDEA_PROJECT, this, null);
    super.bootstrapPicoContainer(name);

    getPicoContainer().registerComponentImplementation(PathMacroManager.class, ProjectPathMacroManager.class);
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
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  @Override
  public boolean isInitialized() {
    return !isDisposed() && isOpen() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @NotNull
  @Override
  public List<ComponentConfig> getMyComponentConfigsFromDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.getProjectComponents();
  }

  @NotNull
  @Override
  protected List<ServiceDescriptor> getServices(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    return ((IdeaPluginDescriptorImpl)pluginDescriptor).getProjectServices();
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

  public void registerComponents() {
    String activityNamePrefix = activityNamePrefix();
    Activity activity = activityNamePrefix == null ? null : StartUpMeasurer.start(activityNamePrefix + Phases.REGISTER_COMPONENTS_SUFFIX);
    //  at this point of time plugins are already loaded by application - no need to pass indicator to getLoadedPlugins call
    registerComponents(PluginManagerCore.getLoadedPlugins());
    if (activity != null) {
      activity.end();
    }

    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode()) {
      app.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsRegistered(this);
    }
  }

  public void init(@Nullable ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();
    createComponents(indicator);
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
    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();  // dispose must be under write action

    // can call dispose only via com.intellij.ide.impl.ProjectUtil.closeAndDispose()
    if (ProjectManagerEx.getInstanceEx().isProjectOpened(this)) {
      throw new IllegalStateException("Must call .dispose() for a closed project only. See ProjectManager.closeProject() or ProjectUtil.closeAndDispose().");
    }

    // we use super here, because temporarilyDisposed will be true if project closed
    LOG.assertTrue(!super.isDisposed(), this + " is disposed already");
    disposeComponents();
    Extensions.disposeArea(this);

    super.dispose();

    if (!application.isDisposed()) {
      application.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).afterProjectClosed(this);
    }
    ((ProjectManagerImpl)ProjectManager.getInstance()).updateTheOnlyProjectField();

    TimedReference.disposeTimed();
    LaterInvocator.purgeExpiredItems();
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }

  @NotNull
  @Override
  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @NonNls
  @Override
  public String toString() {
    return "Project" +
           (isDisposed() ? " (Disposed" + (temporarilyDisposed ? " temporarily" : "") + ")"
                         : " '" + (myComponentStore.isComputed() ? getPresentableUrl() : "<no component store>") + "'") +
           " " + myName;
  }

  @TestOnly
  public String getCreationTrace() {
    return creationTrace;
  }

  @Nullable
  @Override
  protected String activityNamePrefix() {
    return "project ";
  }
}
