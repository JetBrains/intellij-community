// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.conversion.CannotConvertException;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.IdeUICustomization;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ProjectManagerImpl extends ProjectManagerEx implements Disposable {
  protected static final Logger LOG = Logger.getInstance(ProjectManagerImpl.class);

  private static final Key<List<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");

  private static final ExtensionPointName<ProjectCloseHandler> CLOSE_HANDLER_EP = new ExtensionPointName<>("com.intellij.projectCloseHandler");

  private Project @NotNull [] myOpenProjects = {}; // guarded by lock
  private final Map<String, Project> myOpenProjectByHash = new ConcurrentHashMap<>();
  private final Object lock = new Object();

  // we cannot use the same approach to migrate to message bus as CompilerManagerImpl because of method canCloseProject
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final DefaultProject myDefaultProject = new DefaultProject();
  private final ExcludeRootsCache myExcludeRootsCache;

  private static @NotNull List<ProjectManagerListener> getListeners(@NotNull Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return Collections.emptyList();
    return array;
  }

  public ProjectManagerImpl() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectOpened(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosed(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosing(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosing(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosingBeforeSave(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosingBeforeSave(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }
    });
    myExcludeRootsCache = new ExcludeRootsCache(connection);
  }

  private static @NotNull ProjectManagerListener getPublisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC);
  }

  private static void handleListenerError(@NotNull Throwable e, @NotNull ProjectManagerListener listener) {
    if (e instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)e;
    }
    else {
      LOG.error("From the listener " + listener + " (" + listener.getClass() + ")", e);
    }
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    // dispose manually, because TimedReference.dispose() can already be called (in Timed.disposeTimed()) and then default project resurrected
    Disposer.dispose(myDefaultProject);
  }

  protected static void initProject(@NotNull Path file,
                                    @NotNull ProjectImpl project,
                                    boolean isRefreshVfsNeeded,
                                    boolean preloadServices,
                                    @Nullable Project template,
                                    @Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!project.isDefault());
    try {
      if (indicator != null) {
        indicator.setIndeterminate(false);
        // getting project name is not cheap and not possible at this moment
        indicator.setText(ProjectBundle.message("project.loading.components"));
      }

      Activity activity = StartUpMeasurer.startActivity("project before loaded callbacks");
      //noinspection deprecation
      ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(file, project);
      activity.end();

      ProjectLoadHelper.registerComponents(project);
      project.getStateStore().setPath(file, isRefreshVfsNeeded, template);
      project.init(preloadServices, indicator);
    }
    catch (Throwable initThrowable) {
      try {
        WriteAction.runAndWait(() -> Disposer.dispose(project));
      }
      catch (Throwable disposeThrowable) {
        initThrowable.addSuppressed(disposeThrowable);
      }
      throw initThrowable;
    }
  }

  @Override
  public @NotNull Project loadProject(@NotNull Path file) {
    ProjectImpl project = new ProjectExImpl(file, null);
    initProject(file, project, /* isRefreshVfsNeeded = */ true, true, null, ProgressManager.getInstance().getProgressIndicator());
    return project;
  }

  @Override
  public boolean isDefaultProjectInitialized() {
    return myDefaultProject.isCached();
  }

  @Override
  public @NotNull Project getDefaultProject() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDisposed(), "Application has already been disposed!");
    // call instance method to reset timeout
    MessageBus bus = myDefaultProject.getMessageBus(); // re-instantiate if needed
    LOG.assertTrue(!bus.isDisposed());
    LOG.assertTrue(myDefaultProject.isCached());
    return myDefaultProject;
  }

  @TestOnly
  @ApiStatus.Internal
  public void disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests() {
    myDefaultProject.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests();
  }

  @Override
  public Project @NotNull [] getOpenProjects() {
    synchronized (lock) {
      return myOpenProjects;
    }
  }

  @Override
  public boolean isProjectOpened(@NotNull Project project) {
    synchronized (lock) {
      return ArrayUtil.contains(project, myOpenProjects);
    }
  }

  protected final boolean addToOpened(@NotNull Project project) {
    assert !project.isDisposed() : "Must not open already disposed project";
    synchronized (lock) {
      if (isProjectOpened(project)) {
        return false;
      }
      myOpenProjects = ArrayUtil.append(myOpenProjects, project);
    }
    updateTheOnlyProjectField();
    myOpenProjectByHash.put(project.getLocationHash(), project);
    return true;
  }

  void updateTheOnlyProjectField() {
    boolean isDefaultInitialized = isDefaultProjectInitialized();
    boolean isLightEditActive = LightEditService.getInstance().getProject() != null;
    synchronized (lock) {
      ProjectCoreUtil.updateInternalTheOnlyProjectFieldTemporarily(myOpenProjects.length == 1 && !isDefaultInitialized && !isLightEditActive ? myOpenProjects[0] : null);
    }
  }

  private void removeFromOpened(@NotNull Project project) {
    synchronized (lock) {
      myOpenProjects = ArrayUtil.remove(myOpenProjects, project);
      myOpenProjectByHash.values().remove(project); // remove by value and not by key!
    }
  }

  @Override
  public @Nullable Project findOpenProjectByHash(@Nullable String locationHash) {
    return myOpenProjectByHash.get(locationHash);
  }

  public static void showCannotConvertMessage(@NotNull CannotConvertException e, @Nullable Component component) {
    AppUIUtil.invokeOnEdt(() -> Messages.showErrorDialog(component, IdeBundle.message("error.cannot.convert.project", e.getMessage()),
                                                       IdeBundle.message("title.cannot.convert.project")));
  }

  @Override
  public void reloadProject(@NotNull Project project) {
    StoreReloadManager.getInstance().reloadProject(project);
  }

  @Override
  public final boolean closeProject(@NotNull Project project) {
    return closeProject(project, /* isSaveProject = */ true, /* dispose = */ false, /* checkCanClose = */ true);
  }

  @Override
  public boolean forceCloseProject(@NotNull Project project) {
    return closeProject(project, /* isSaveProject = */ false, /* dispose = */ true, /* checkCanClose = */ false);
  }

  @Override
  public boolean saveAndForceCloseProject(@NotNull Project project) {
    return closeProject(project, /* isSaveProject = */ true, /* dispose = */ true, /* checkCanClose = */ false);
  }

  // return true if successful
  @Override
  public boolean closeAndDisposeAllProjects(boolean checkCanClose) {
    Project[] projects = getOpenProjects();
    Project lightEditProject = LightEditUtil.getProjectIfCreated();
    if (lightEditProject != null) {
      projects = ArrayUtil.append(projects, lightEditProject);
    }
    for (Project project : projects) {
      if (!closeProject(project, /* isSaveProject = */ true, /* dispose = */ true, checkCanClose)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("TestOnlyProblems")
  protected boolean closeProject(@NotNull Project project, boolean saveProject, boolean dispose, boolean checkCanClose) {
    Application app = ApplicationManager.getApplication();
    if (app.isWriteAccessAllowed()) {
      throw new IllegalStateException(
        "Must not call closeProject() from under write action because fireProjectClosing() listeners must have a chance to do something useful");
    }
    app.assertIsWriteThread();

    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed;
      // if we are shutting down the entire test framework, proceed to full dispose
      ProjectExImpl projectImpl = (ProjectExImpl)project;
      if (!projectImpl.isTemporarilyDisposed()) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          projectImpl.disposeEarlyDisposable();
          projectImpl.setTemporarilyDisposed(true);
          removeFromOpened(project);
        });
        updateTheOnlyProjectField();
        return true;
      }
      projectImpl.setTemporarilyDisposed(false);
    }
    else if (!isProjectOpened(project) && !LightEdit.owns(project)) {
      if (dispose) {
        if (project instanceof ComponentManagerImpl) {
          ((ComponentManagerImpl)project).stopServicePreloading();
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (project instanceof ProjectExImpl) {
            ProjectExImpl projectImpl = (ProjectExImpl)project;
            projectImpl.disposeEarlyDisposable();
            projectImpl.startDispose();
          }
          Disposer.dispose(project);
        });
      }
      return true;
    }

    if (checkCanClose && !canClose(project)) {
      return false;
    }

    AtomicBoolean result = new AtomicBoolean();
    ShutDownTracker.getInstance().executeWithStopperThread(Thread.currentThread(), ()->{
      if (project instanceof ComponentManagerImpl) {
        ((ComponentManagerImpl)project).stopServicePreloading();
      }

      getPublisher().projectClosingBeforeSave(project);

      if (saveProject) {
        FileDocumentManager.getInstance().saveAllDocuments();
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(project);
      }

      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return;
      }

      // somebody can start progress here, do not wrap in write action
      fireProjectClosing(project);

      app.runWriteAction(() -> {
        removeFromOpened(project);

        if (project instanceof ProjectExImpl) {
          // ignore dispose flag (dispose is passed only via deprecated API that used only by some 3d-party plugins)
          ((ProjectExImpl)project).disposeEarlyDisposable();
          if (dispose) {
            ((ProjectExImpl)project).startDispose();
          }
        }

        fireProjectClosed(project);

        ZipHandler.clearFileAccessorCache();
        LaterInvocator.purgeExpiredItems();

        if (dispose) {
          Disposer.dispose(project);
        }
      });
      result.set(true);
    });

    return result.get();
  }

  @TestOnly
  public static boolean isLight(@NotNull Project project) {
    return project instanceof ProjectEx && ((ProjectEx)project).isLight();
  }

  @Override
  public boolean closeAndDispose(@NotNull Project project) {
    return closeProject(project, true /* save project */, true /* dispose project */, true /* checkCanClose */);
  }

  private static void fireProjectClosing(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }

    getPublisher().projectClosing(project);
  }

  @Override
  public void addProjectManagerListener(@NotNull ProjectManagerListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addProjectManagerListener(@NotNull VetoableProjectManagerListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeProjectManagerListener(@NotNull ProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  @Override
  public void removeProjectManagerListener(@NotNull VetoableProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  @Override
  public void addProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    if (project.isDefault()) return; // nothing happens with default project
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = ((UserDataHolderEx)project)
        .putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY, ContainerUtil.createLockFreeCopyOnWriteList());
    }
    listeners.add(listener);
  }

  @Override
  public void removeProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    if (project.isDefault()) return;  // nothing happens with default project
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    LOG.assertTrue(listeners != null);
    boolean removed = listeners.remove(listener);
    LOG.assertTrue(removed);
  }

  private static void fireProjectClosed(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectClosed");
    }

    LifecycleUsageTriggerCollector.onProjectClosed(project);

    getPublisher().projectClosed(project);
    //noinspection deprecation
    List<ProjectComponent> projectComponents = new ArrayList<>();
    //noinspection deprecation
    ((ComponentManagerEx)project).processInitializedComponents(ProjectComponent.class, (component, __) -> {
      projectComponents.add(component);
      return Unit.INSTANCE;
    });
    // see "why is called after message bus" in the fireProjectOpened
    for (int i = projectComponents.size() - 1; i >= 0; i--) {
      //noinspection deprecation
      ProjectComponent component = projectComponents.get(i);
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  @Override
  public boolean canClose(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }

    for (ProjectCloseHandler handler : CLOSE_HANDLER_EP.getIterable()) {
      if (handler == null) {
        break;
      }

      try {
        if (!handler.canClose(project)) {
          LOG.debug("close canceled by " + handler);
          return false;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    for (ProjectManagerListener listener : getAllListeners(project)) {
      try {
        //noinspection deprecation
        boolean canClose = listener instanceof VetoableProjectManagerListener ? ((VetoableProjectManagerListener)listener).canClose(project) : listener.canCloseProject(project);
        if (!canClose) {
          LOG.debug("close canceled by " + listener);
          return false;
        }
      }
      catch (Throwable e) {
        handleListenerError(e, listener);
      }
    }

    return true;
  }

  private @NotNull List<ProjectManagerListener> getAllListeners(@NotNull Project project) {
    List<ProjectManagerListener> projectLevelListeners = getListeners(project);
    if (projectLevelListeners.isEmpty()) {
      return myListeners;
    }
    if (myListeners.isEmpty()) {
      return projectLevelListeners;
    }

    List<ProjectManagerListener> result = new ArrayList<>(projectLevelListeners.size() + myListeners.size());
    // order is critically important due to backward compatibility - project level listeners must be first
    result.addAll(projectLevelListeners);
    result.addAll(myListeners);
    return result;
  }

  private static boolean ensureCouldCloseIfUnableToSave(@NotNull Project project) {
    NotificationsManager notificationManager = ApplicationManager.getApplication().getServiceIfCreated(NotificationsManager.class);
    if (notificationManager == null) {
      return true;
    }

    UnableToSaveProjectNotification[] notifications = notificationManager.getNotificationsOfType(UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) {
      return true;
    }

    @NlsContexts.DialogMessage StringBuilder message = new StringBuilder();
    message.append(String.format("%s was unable to save some project files,\nare you sure you want to close this project anyway?",
                                 ApplicationNamesInfo.getInstance().getProductName()));

    message.append("\n\nRead-only files:\n");
    int count = 0;
    List<VirtualFile> files = notifications[0].myFiles;
    for (VirtualFile file : files) {
      if (count == 10) {
        message.append('\n').append("and ").append(files.size() - count).append(" more").append('\n');
      }
      else {
        message.append(file.getPath()).append('\n');
        count++;
      }
    }
    return Messages.showYesNoDialog(project, message.toString(), IdeUICustomization.getInstance().projectMessage("dialog.title.unsaved.project"), Messages.getWarningIcon()) == Messages.YES;
  }

  public static class UnableToSaveProjectNotification extends Notification {
    private Project myProject;

    private List<VirtualFile> myFiles;

    public void setFiles(@NotNull List<VirtualFile> files) {
      myFiles = files;
    }

    public UnableToSaveProjectNotification(@NotNull Project project, @NotNull List<VirtualFile> readOnlyFiles) {
      super("Project Settings",
            IdeUICustomization.getInstance().projectMessage("notification.title.cannot.save.project"),
            IdeBundle.message("notification.content.unable.to.save.project.files"), NotificationType.ERROR);
      setListener((notification, event) -> {
        UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
        Project _project = unableToSaveProjectNotification.myProject;
        notification.expire();
        if (_project != null && !_project.isDisposed()) {
          _project.save();
        }
      });

      myProject = project;
      myFiles = readOnlyFiles;
    }

    @Override
    public void expire() {
      myProject = null;
      super.expire();
    }
  }

  private Runnable myGetAllExcludedUrlsCallback;
  @TestOnly
  public void testOnlyGetExcludedUrlsCallback(@NotNull Disposable parentDisposable, @NotNull Runnable callback) {
    if (myGetAllExcludedUrlsCallback != null) {
      throw new IllegalStateException("This method is not reentrant. Expected null but got " + myGetAllExcludedUrlsCallback);
    }
    myGetAllExcludedUrlsCallback = callback;
    Disposer.register(parentDisposable, () -> myGetAllExcludedUrlsCallback = null);
  }
  @Override
  public @NotNull List<String> getAllExcludedUrls() {
    Runnable callback = myGetAllExcludedUrlsCallback;
    if (callback != null) callback.run();
    return myExcludeRootsCache.getExcludedUrls();
  }
}
