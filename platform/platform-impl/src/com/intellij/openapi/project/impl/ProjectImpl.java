/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.*;
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
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.util.TimedReference;
import com.intellij.util.pico.ConstructorInjectionComponentAdapter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  public static final String NAME_FILE = ".name";
  public static Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");

  private ProjectManager myProjectManager;
  private MyProjectManagerListener myProjectManagerListener;
  private final AtomicBoolean mySavingInProgress = new AtomicBoolean(false);
  public boolean myOptimiseTestLoadSpeed;
  private String myName;
  private String myOldName;
  private final boolean myLight;

  protected ProjectImpl(@NotNull ProjectManager projectManager,
                        @NotNull String filePath,
                        boolean optimiseTestLoadSpeed,
                        @Nullable String projectName) {
    super(ApplicationManager.getApplication(), "Project " + (projectName == null ? filePath : projectName));

    putUserData(CREATION_TIME, System.nanoTime());

    getPicoContainer().registerComponentInstance(Project.class, this);

    if (!isDefault()) {
      getStateStore().setProjectFilePath(filePath);
    }

    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
    myProjectManager = projectManager;

    myName = projectName == null ? getStateStore().getProjectName() : projectName;
    if (!isDefault() && projectName != null && getStateStore().getStorageScheme().equals(StorageScheme.DIRECTORY_BASED)) {
      myOldName = "";  // new project
    }
    
    // light project may be changed later during test, so we need to remember its initial state 
    myLight = ApplicationManager.getApplication().isUnitTestMode() && filePath.contains("light_temp_");
  }

  @Override
  public boolean isDisposed() {
    return super.isDisposed() || temporarilyDisposed;
  }

  @TestOnly
  boolean isLight() {
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
      private ComponentAdapter myDelegate;

      private ComponentAdapter getDelegate() {
        if (myDelegate == null) {
          final Class storeClass = projectStoreClassProvider.getProjectStoreClass(isDefault());
          myDelegate = new ConstructorInjectionComponentAdapter(storeClass, storeClass, null, true);
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
    return (IProjectStore)getPicoContainer().getComponentInstance(IComponentStore.class);
  }

  @Override
  public void initializeComponent(@NotNull Object component, boolean service) {
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
    return !isDisposed() && isOpen() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @NotNull
  @Override
  public ComponentConfig[] getMyComponentConfigsFromDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.getProjectComponents();
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

  @Nullable
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
    loadComponents();
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
      myProjectManager.addProjectManagerListener(this, myProjectManagerListener);
    }
  }

  private boolean isToSaveProjectName() {
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
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) {
      // no need to save
      return;
    }

    if (!mySavingInProgress.compareAndSet(false, true)) {
      return;
    }

    try {
      if (isToSaveProjectName()) {
        try {
          VirtualFile baseDir = getStateStore().getProjectBaseDir();
          if (baseDir != null && baseDir.isValid()) {
            VirtualFile ideaDir = baseDir.findChild(DIRECTORY_STORE_FOLDER);
            if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
              File nameFile = new File(ideaDir.getPath(), NAME_FILE);

              FileUtil.writeToFile(nameFile, getName());
              myOldName = null;

              RecentProjectsManager.getInstance().clearNameCache();
            }
          }
        }
        catch (Throwable e) {
          LOG.error("Unable to store project name", e);
        }
      }

      StoreUtil.save(getStateStore(), this);
    }
    finally {
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
      myProjectManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    disposeComponents();
    Extensions.disposeArea(this);
    myProjectManager = null;
    myProjectManagerListener = null;

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

  @NotNull
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
    TrackingPathMacroSubstitutor[] substitutors = stateStore.getSubstitutors();
    Set<String> unknownMacros = new THashSet<String>();
    for (TrackingPathMacroSubstitutor substitutor : substitutors) {
      unknownMacros.addAll(substitutor.getUnknownMacros(null));
    }

    if (unknownMacros.isEmpty() || showDialog && !ProjectMacrosUtil.checkMacros(this, new THashSet<String>(unknownMacros))) {
      return;
    }

    final PathMacros pathMacros = PathMacros.getInstance();
    final Set<String> macrosToInvalidate = new THashSet<String>(unknownMacros);
    for (Iterator<String> it = macrosToInvalidate.iterator(); it.hasNext(); ) {
      String macro = it.next();
      if (StringUtil.isEmptyOrSpaces(pathMacros.getValue(macro)) && !pathMacros.isIgnoredMacroName(macro)) {
        it.remove();
      }
    }

    if (!macrosToInvalidate.isEmpty()) {
      final Set<String> components = new THashSet<String>();
      for (TrackingPathMacroSubstitutor substitutor : substitutors) {
        components.addAll(substitutor.getComponents(macrosToInvalidate));
      }

      if (stateStore.isReloadPossible(components)) {
        for (TrackingPathMacroSubstitutor substitutor : substitutors) {
          substitutor.invalidateUnknownMacros(macrosToInvalidate);
        }

        for (UnknownMacroNotification notification : NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, this)) {
          if (macrosToInvalidate.containsAll(notification.getMacros())) {
            notification.expire();
          }
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
}
