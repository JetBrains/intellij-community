/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.PlatformComponentManagerImpl;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.util.Function;
import com.intellij.util.TimedReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");
  private static final String PLUGIN_SETTINGS_ERROR = "Plugin Settings Error";

  public static final String NAME_FILE = ".name";
  public static Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");

  private ProjectManager myManager;
  private volatile IProjectStore myComponentStore;
  private MyProjectManagerListener myProjectManagerListener;
  private final AtomicBoolean mySavingInProgress = new AtomicBoolean(false);
  public boolean myOptimiseTestLoadSpeed;
  private String myName;
  private String myOldName;

  protected ProjectImpl(@NotNull ProjectManager manager, @NotNull String filePath, boolean optimiseTestLoadSpeed, @Nullable String projectName) {
    super(ApplicationManager.getApplication(), "Project " + (projectName == null ? filePath : projectName));

    putUserData(CREATION_TIME, System.nanoTime());

    getPicoContainer().registerComponentInstance(Project.class, this);

    if (!isDefault()) {
      getStateStore().setProjectFilePath(filePath);
    }

    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
    myManager = manager;

    myName = projectName == null ? getStateStore().getProjectName() : projectName;
    if (!isDefault() && projectName != null && getStateStore().getStorageScheme().equals(StorageScheme.DIRECTORY_BASED)) {
      myOldName = "";  // new project
    }
  }

  @Override
  public void setProjectName(@NotNull String projectName) {
    if (!projectName.equals(myName)) {
      myOldName = myName;
      myName = projectName;
      StartupManager.getInstance(this).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          if (isDisposed()) return;

          JFrame frame = WindowManager.getInstance().getFrame(ProjectImpl.this);
          String title = FrameTitleBuilder.getInstance().getProjectTitle(ProjectImpl.this);
          if (frame != null && title != null) {
            frame.setTitle(title);
          }
        }
      });
    }
  }

  @Override
  protected void bootstrapPicoContainer(@NotNull String name) {
    Extensions.instantiateArea(ExtensionAreas.IDEA_PROJECT, this, null);
    super.bootstrapPicoContainer(name);
    final MutablePicoContainer picoContainer = getPicoContainer();

    final ProjectStoreClassProvider projectStoreClassProvider =
      (ProjectStoreClassProvider)picoContainer.getComponentInstanceOfType(ProjectStoreClassProvider.class);

    picoContainer.registerComponentImplementation(ProjectPathMacroManager.class);
    picoContainer.registerComponent(new ComponentAdapter() {
      ComponentAdapter myDelegate;

      public ComponentAdapter getDelegate() {
        if (myDelegate == null) {
          final Class storeClass = projectStoreClassProvider.getProjectStoreClass(isDefault());
          myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(storeClass, storeClass, null, true));
        }

        return myDelegate;
      }

      @Override
      public Object getComponentKey() {
        return IComponentStore.class;
      }

      @Override
      public Class getComponentImplementation() {
        return getDelegate().getComponentImplementation();
      }

      @Override
      public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
        return getDelegate().getComponentInstance(container);
      }

      @Override
      public void verify(final PicoContainer container) throws PicoIntrospectionException {
        getDelegate().verify(container);
      }

      @Override
      public void accept(final PicoVisitor visitor) {
        visitor.visitComponentAdapter(this);
        getDelegate().accept(visitor);
      }
    });
  }

  @NotNull
  @Override
  public IProjectStore getStateStore() {
    IProjectStore componentStore = myComponentStore;
    if (componentStore != null) return componentStore;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      componentStore = myComponentStore;
      if (componentStore == null) {
        myComponentStore = componentStore = (IProjectStore)getPicoContainer().getComponentInstance(IComponentStore.class);
      }
      return componentStore;
    }
  }

  @Override
  public void initializeComponent(Object component, boolean service) {
    if (!service) {
      ProgressIndicator indicator = getProgressIndicator();
      if (indicator != null) {
  //      indicator.setText2(getComponentName(component));
        indicator.setIndeterminate(false);
        indicator.setFraction(getPercentageOfComponentsLoaded());
      }
    }

    getStateStore().initComponent(component, service);
  }

  @Override
  public boolean isOpen() {
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  @Override
  public boolean isInitialized() {
    return isOpen() && !isDisposed() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  public void loadProjectComponents() {
    final IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManagerCore.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getProjectComponents(), plugin, isDefault());
    }
  }

  @Override
  @NotNull
  public String getProjectFilePath() {
    return getStateStore().getProjectFilePath();
  }

  @Override
  public VirtualFile getProjectFile() {
    return getStateStore().getProjectFile();
  }

  @Override
  public VirtualFile getBaseDir() {
    return getStateStore().getProjectBaseDir();
  }

  @Override
  public String getBasePath() {
    return getStateStore().getProjectBasePath();
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NonNls
  @Override
  public String getPresentableUrl() {
    if (myName == null) return null;  // not yet initialized
    return getStateStore().getPresentableUrl();
  }

  @NotNull
  @NonNls
  @Override
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) str = getName();

    final String prefix = getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED ? "" : getName();
    return prefix + Integer.toHexString(str.hashCode());
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    return getStateStore().getWorkspaceFile();
  }

  @Override
  public boolean isOptimiseTestLoadSpeed() {
    return myOptimiseTestLoadSpeed;
  }

  @Override
  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
  }

  @Override
  public void init() {
    long start = System.currentTimeMillis();

    final ProgressIndicator progressIndicator = isDefault() ? null : ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.pushState();
    }
    super.init();
    if (progressIndicator != null) {
      progressIndicator.popState();
    }

    long time = System.currentTimeMillis() - start;
    LOG.info(getComponentConfigurations().length + " project components initialized in " + time + " ms");

    getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsInitialized(this);

    //noinspection SynchronizeOnThis
    synchronized (this) {
      myProjectManagerListener = new MyProjectManagerListener();
      myManager.addProjectManagerListener(this, myProjectManagerListener);
    }
  }

  public boolean isToSaveProjectName() {
    if (!isDefault()) {
      final IProjectStore stateStore = getStateStore();
      if (stateStore.getStorageScheme().equals(StorageScheme.DIRECTORY_BASED)) {
        final VirtualFile baseDir = stateStore.getProjectBaseDir();
        if (baseDir != null && baseDir.isValid()) {
          return myOldName != null && !myOldName.equals(getName());
        }
      }
    }

    return false;
  }

  @Override
  public void save() {
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) return; //no need to save

    if (!mySavingInProgress.compareAndSet(false, true)) {
      return;
    }
    try {
      if (isToSaveProjectName()) {
        final IProjectStore stateStore = getStateStore();
        final VirtualFile baseDir = stateStore.getProjectBaseDir();
        if (baseDir != null && baseDir.isValid()) {
          final VirtualFile ideaDir = baseDir.findChild(DIRECTORY_STORE_FOLDER);
          if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
            final File nameFile = new File(ideaDir.getPath(), NAME_FILE);
            try {
              FileUtil.writeToFile(nameFile, getName().getBytes("UTF-8"), false);
              myOldName = null;
            }
            catch (IOException e) {
              LOG.info("Unable to store project name to: " + nameFile.getPath());
            }
            RecentProjectsManagerBase.getInstance().clearNameCache();
          }
        }
      }

      StoreUtil.doSave(getStateStore());
    }
    catch (IComponentStore.SaveCancelledException e) {
      LOG.info(e);
    }
    catch (PluginException e) {
      PluginManagerCore.disablePlugin(e.getPluginId().getIdString());
      Notification notification = new Notification(
        PLUGIN_SETTINGS_ERROR,
        "Unable to save plugin settings!",
        "<p>The plugin <i>" + e.getPluginId() + "</i> failed to save settings and has been disabled. Please restart " +
        ApplicationNamesInfo.getInstance().getFullProductName() + "</p>" +
        (ApplicationManagerEx.getApplicationEx().isInternal() ? "<p>" + StringUtil.getThrowableText(e) + "</p>" : ""),
        NotificationType.ERROR);
      Notifications.Bus.notify(notification, this);
      LOG.info("Unable to save plugin settings",e);
    }
    catch (IOException e) {
      MessagesEx.error(this, ProjectBundle.message("project.save.error", e.getMessage())).showLater();
      LOG.info("Error saving project", e);
    } finally {
      mySavingInProgress.set(false);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectSaved.TOPIC).saved(this);
    }
  }

  @Override
  public synchronized void dispose() {
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    assert application.isWriteAccessAllowed();  // dispose must be under write action

    // can call dispose only via com.intellij.ide.impl.ProjectUtil.closeAndDispose()
    LOG.assertTrue(application.isUnitTestMode() || !ProjectManagerEx.getInstanceEx().isProjectOpened(this));

    LOG.assertTrue(!isDisposed());
    if (myProjectManagerListener != null) {
      myManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    disposeComponents();
    Extensions.disposeArea(this);
    myManager = null;
    myProjectManagerListener = null;

    myComponentStore = null;

    super.dispose();

    if (!application.isDisposed()) {
      application.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).afterProjectClosed(this);
    }
    TimedReference.disposeTimed();
  }

  private void projectOpened() {
    final ProjectComponent[] components = getComponents(ProjectComponent.class);
    for (ProjectComponent component : components) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  private void projectClosed() {
    List<ProjectComponent> components = new ArrayList<ProjectComponent>(Arrays.asList(getComponents(ProjectComponent.class)));
    Collections.reverse(components);
    for (ProjectComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    return Extensions.getArea(this).getExtensionPoint(extensionPointName).getExtensions();
  }

  public String getDefaultName() {
    return isDefault() ? myName : getStateStore().getProjectName();
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    @Override
    public void projectOpened(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectOpened();
    }

    @Override
    public void projectClosed(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectClosed();
    }
  }

  @Override
  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getArea(this).getPicoContainer();
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public void checkUnknownMacros(final boolean showDialog) {
    final IProjectStore stateStore = getStateStore();

    final TrackingPathMacroSubstitutor[] substitutors = stateStore.getSubstitutors();
    final Set<String> unknownMacros = new HashSet<String>();
    for (final TrackingPathMacroSubstitutor substitutor : substitutors) {
      unknownMacros.addAll(substitutor.getUnknownMacros(null));
    }

    if (!unknownMacros.isEmpty()) {
      if (!showDialog || ProjectMacrosUtil.checkMacros(this, new HashSet<String>(unknownMacros))) {
        final PathMacros pathMacros = PathMacros.getInstance();
        final Set<String> macros2invalidate = new HashSet<String>(unknownMacros);
        for (Iterator it = macros2invalidate.iterator(); it.hasNext();) {
          final String macro = (String)it.next();
          final String value = pathMacros.getValue(macro);
          if ((value == null || value.trim().isEmpty()) && !pathMacros.isIgnoredMacroName(macro)) {
            it.remove();
          }
        }

        if (!macros2invalidate.isEmpty()) {
          final Set<String> components = new HashSet<String>();
          for (TrackingPathMacroSubstitutor substitutor : substitutors) {
            components.addAll(substitutor.getComponents(macros2invalidate));
          }

          if (stateStore.isReloadPossible(components)) {
            for (final TrackingPathMacroSubstitutor substitutor : substitutors) {
              substitutor.invalidateUnknownMacros(macros2invalidate);
            }

            final UnknownMacroNotification[] notifications =
              NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, this);
            for (final UnknownMacroNotification notification : notifications) {
              if (macros2invalidate.containsAll(notification.getMacros())) notification.expire();
            }

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                stateStore.reinitComponents(components, true);
              }
            });
          }
          else {
            if (Messages.showYesNoDialog(this, "Component could not be reloaded. Reload project?", "Configuration Changed",
                                         Messages.getQuestionIcon()) == Messages.YES) {
              ProjectManagerEx.getInstanceEx().reloadProject(this);
            }
          }
        }
      }
    }
  }

  @NonNls
  @Override
  public String toString() {
    return "Project" +
           (isDisposed() ? " (Disposed" + (temporarilyDisposed ? " temporarily" : "") + ")"
                         : isDefault() ? "" : " '" + getPresentableUrl() + "'") +
           (isDefault() ? " (Default)" : "") +
           " " + myName;
  }

  @Override
  protected boolean logSlowComponents() {
    return super.logSlowComponents() || ApplicationInfoImpl.getShadowInstance().isEAP();
  }

  public static void dropUnableToSaveProjectNotification(@NotNull final Project project, final VirtualFile[] readOnlyFiles) {
    final UnableToSaveProjectNotification[] notifications =
      NotificationsManager.getNotificationsManager().getNotificationsOfType(UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) {
      Notifications.Bus.notify(new UnableToSaveProjectNotification(project, readOnlyFiles), project);
    }
  }

  public static class UnableToSaveProjectNotification extends Notification {
    private Project myProject;
    private final String[] myFileNames;

    private UnableToSaveProjectNotification(@NotNull final Project project, final VirtualFile[] readOnlyFiles) {
      super("Project Settings", "Could not save project!", buildMessage(), NotificationType.ERROR, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          final UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
          final Project _project = unableToSaveProjectNotification.getProject();
          notification.expire();

          if (_project != null && !_project.isDisposed()) {
            _project.save();
          }
        }
      });

      myProject = project;
      myFileNames = ContainerUtil.map(readOnlyFiles, new Function<VirtualFile, String>() {
        @Override
        public String fun(VirtualFile file) {
          return file.getPresentableUrl();
        }
      }, new String[readOnlyFiles.length]);
    }

    public String[] getFileNames() {
      return myFileNames;
    }

    private static String buildMessage() {
      return "<p>Unable to save project files. Please ensure project files are writable and you have permissions to modify them." +
             " <a href=\"\">Try to save project again</a>.</p>";
    }

    public Project getProject() {
      return myProject;
    }

    @Override
    public void expire() {
      myProject = null;
      super.expire();
    }
  }
}
