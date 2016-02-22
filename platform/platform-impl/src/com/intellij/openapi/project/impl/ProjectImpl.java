/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.PlatformComponentManagerImpl;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StoreUtil;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.TimedReference;
import com.intellij.util.pico.ConstructorInjectionComponentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  public static final String NAME_FILE = ".name";
  public static final Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");
  public static final Key<String> CREATION_TRACE = Key.create("ProjectImpl.CREATION_TRACE");
  @TestOnly
  public static final String LIGHT_PROJECT_NAME = "light_temp";

  private ProjectManager myProjectManager;
  private MyProjectManagerListener myProjectManagerListener;
  private final AtomicBoolean mySavingInProgress = new AtomicBoolean(false);
  private String myName;
  private final boolean myLight;

  /**
   * @param filePath System-independent path
   */
  protected ProjectImpl(@NotNull ProjectManager projectManager,
                        @NotNull String filePath,
                        @Nullable String projectName) {
    super(ApplicationManager.getApplication(), "Project " + (projectName == null ? filePath : projectName));

    putUserData(CREATION_TIME, System.nanoTime());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      putUserData(CREATION_TRACE, DebugUtil.currentStackTrace());
    }

    getPicoContainer().registerComponentInstance(Project.class, this);

    if (!isDefault()) {
      getStateStore().setPath(filePath);
    }

    myProjectManager = projectManager;

    myName = projectName == null ? getStateStore().getProjectName() : projectName;
    // light project may be changed later during test, so we need to remember its initial state 
    myLight = ApplicationManager.getApplication().isUnitTestMode() && filePath.contains(LIGHT_PROJECT_NAME);
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

    picoContainer.registerComponentImplementation(PathMacroManager.class, ProjectPathMacroManager.class);
    picoContainer.registerComponent(new ComponentAdapter() {
      private ComponentAdapter myDelegate;

      @NotNull
      private ComponentAdapter getDelegate() {
        if (myDelegate == null) {
          Class storeClass = projectStoreClassProvider.getProjectStoreClass(isDefault());
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
  IProjectStore getStateStore() {
    return (IProjectStore)ServiceKt.getStateStore(this);
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
  @Nullable
  public String getProjectFilePath() {
    return isDefault() ? null : getStateStore().getProjectFilePath();
  }

  @Override
  public VirtualFile getProjectFile() {
    return isDefault() ? null : LocalFileSystem.getInstance().findFileByPath(getStateStore().getProjectFilePath());
  }

  @Override
  public VirtualFile getBaseDir() {
    String path = isDefault() ? null : getStateStore().getProjectBasePath();
    return path == null ? null : LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Nullable
  @Override
  public String getBasePath() {
    return isDefault() ? null : getStateStore().getProjectBasePath();
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NonNls
  @Override
  public String getPresentableUrl() {
    if (myName == null || isDefault()) {
      // not yet initialized
      return null;
    }

    IProjectStore store = getStateStore();
    String path = store.getStorageScheme() == StorageScheme.DIRECTORY_BASED ? store.getProjectBasePath() : store.getProjectFilePath();
    return path == null ? null : FileUtil.toSystemDependentName(path);
  }

  @NotNull
  @NonNls
  @Override
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) str = getName();

    final String prefix = !isDefault() && getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED ? "" : getName();
    return prefix + Integer.toHexString(str.hashCode());
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    String workspaceFilePath = isDefault() ? null : getStateStore().getWorkspaceFilePath();
    return workspaceFilePath == null ? null : LocalFileSystem.getInstance().findFileByPath(workspaceFilePath);
  }

  @Override
  public void init() {
    long start = System.currentTimeMillis();

    final ProgressIndicator progressIndicator = isDefault() ? null : ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.pushState();
    }
    try {
      init(progressIndicator);
    }
    finally {
      if (progressIndicator != null) {
        progressIndicator.popState();
      }
    }

    long time = System.currentTimeMillis() - start;
    LOG.info(getComponentConfigCount() + " project components initialized in " + time + " ms");

    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsInitialized(this);

    //noinspection SynchronizeOnThis
    synchronized (this) {
      myProjectManagerListener = new MyProjectManagerListener();
      myProjectManager.addProjectManagerListener(this, myProjectManagerListener);
    }
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
      StoreUtil.save(ServiceKt.getStateStore(this), this);
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

    // we use super here, because temporarilyDisposed will be true if project closed
    LOG.assertTrue(!super.isDisposed());
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
    for (ProjectComponent component : getComponentInstancesOfType(ProjectComponent.class)) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  private void projectClosed() {
    List<ProjectComponent> components = getComponentInstancesOfType(ProjectComponent.class);
    for (int i = components.size() - 1; i >= 0; i--) {
      try {
        components.get(i).projectClosed();
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
