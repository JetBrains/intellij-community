// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.wm.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.mvc.projectView.MvcToolWindowDescriptor;

import java.util.*;

@Service
public final class MvcModuleStructureSynchronizer implements Disposable {
  private final Set<Pair<Object, SyncAction>> myOrders = new LinkedHashSet<>();
  private final Project myProject;

  private Set<VirtualFile> myPluginRoots = Collections.emptySet();

  private boolean myOutOfModuleDirectoryCreatedActionAdded;

  @SuppressWarnings("StaticNonFinalField") public static boolean ourGrailsTestFlag;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public MvcModuleStructureSynchronizer(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    // noop
  }

  @NotNull
  public static MvcModuleStructureSynchronizer getInstance(@NotNull Project project) {
    return project.getService(MvcModuleStructureSynchronizer.class);
  }

  static final class MyPostStartUpActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      GuiUtils.invokeLaterIfNeeded(() -> getInstance(project).projectOpened(), ModalityState.NON_MODAL, project.getDisposed());
    }
  }

  private void projectOpened() {
    synchronized (myOrders) {
      Project project = myProject;
      myOrders.add(new Pair<>(project, SyncAction.UpdateProjectStructure));
      myOrders.add(new Pair<>(project, SyncAction.EnsureRunConfigurationExists));
      myOrders.add(new Pair<>(project, SyncAction.UpgradeFramework));
      myOrders.add(new Pair<>(project, SyncAction.CreateAppStructureIfNeeded));
    }
    scheduleRunActions();

    addListeners();
  }

  @NotNull
  public SimpleModificationTracker getFileAndRootsModificationTracker() {
    return myModificationTracker;
  }

  private void addListeners() {
    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    for (String rootPath : MvcWatchedRootProvider.getRootsToWatch(myProject)) {
      VirtualFilePointerManager.getInstance().createDirectoryPointer(VfsUtilCore.pathToUrl(rootPath), true, this, new VirtualFilePointerListener() {
        @Override
        public void validityChanged(@NotNull VirtualFilePointer @NotNull [] pointers) {
          myModificationTracker.incModificationCount();
          synchronized (myOrders) {
            myOrders.add(new Pair<>(myProject, SyncAction.SyncLibrariesInPluginsModule));
          }
          scheduleRunActions();
        }
      });
    }

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        myModificationTracker.incModificationCount();
        synchronized (myOrders) {
          Project project = myProject;
          myOrders.add(new Pair<>(project, SyncAction.UpgradeFramework));
          myOrders.add(new Pair<>(project, SyncAction.CreateAppStructureIfNeeded));
          myOrders.add(new Pair<>(project, SyncAction.UpdateProjectStructure));
          myOrders.add(new Pair<>(project, SyncAction.EnsureRunConfigurationExists));
        }
        scheduleRunActions();
        updateProjectViewVisibility();
      }
    });

    connection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleAdded(@NotNull Project project, @NotNull Module module) {
        synchronized (myOrders) {
          myOrders.add(new Pair<>(project, SyncAction.UpdateProjectStructure));
          myOrders.add(new Pair<>(project, SyncAction.CreateAppStructureIfNeeded));
        }
        scheduleRunActions();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
      final ProjectFileIndex myFileIndex = ProjectFileIndex.getInstance(myProject);

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!myFileIndex.isInContent(file)) {
          return;
        }

        myModificationTracker.incModificationCount();

        String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName) || isApplicationDirectoryName(fileName)) {
          queue(SyncAction.UpdateProjectStructure, file);
          queue(SyncAction.EnsureRunConfigurationExists, file);
        }
        else if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
        else {
          if (!myProject.isInitialized()) return;

          Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);

          if (module == null) { // Maybe it is creation of a plugin in plugin directory.
            if (file.isDirectory()) {
              if (myPluginRoots.contains(file.getParent())) {
                queue(SyncAction.UpdateProjectStructure, myProject);
                return;
              }

              if (!myOutOfModuleDirectoryCreatedActionAdded) {
                queue(SyncAction.OutOfModuleDirectoryCreated, myProject);
                myOutOfModuleDirectoryCreatedActionAdded = true;
              }
            }
            return;
          }

          if (!MvcConsole.isUpdatingVfsByConsoleProcess(module)) return;

          MvcFramework framework = MvcFramework.getInstance(module);
          if (framework == null) return;

          if (framework.isToReformatOnCreation(file) || file.isDirectory()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              if (!file.isValid()) return;
              if (!framework.hasSupport(module)) return;

              List<VirtualFile> files = new ArrayList<>();

              if (file.isDirectory()) {
                ModuleRootManager.getInstance(module).getFileIndex().iterateContentUnderDirectory(file, fileOrDir -> {
                  if (!fileOrDir.isDirectory() && framework.isToReformatOnCreation(fileOrDir)) {
                    files.add(file);
                  }
                  return true;
                });
              }
              else {
                files.add(file);
              }

              PsiManager manager = PsiManager.getInstance(myProject);

              for (VirtualFile virtualFile : files) {
                PsiFile psiFile = manager.findFile(virtualFile);
                if (psiFile != null) {
                  new ReformatCodeProcessor(myProject, psiFile, null, false).run();
                }
              }
            }, module.getDisposed());
          }
        }
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();

        myModificationTracker.incModificationCount();

        if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        VirtualFile file = event.getFile();
        if (!myFileIndex.isInContent(file)) return;

        String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName)) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        if (!myFileIndex.isInContent(event.getFile())) return;
        myModificationTracker.incModificationCount();
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (!myFileIndex.isInContent(event.getFile())) return;
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          myModificationTracker.incModificationCount();
        }
      }
    }));
  }

  private static boolean isApplicationDirectoryName(@NotNull String fileName) {
    for (MvcFramework framework : MvcFramework.EP_NAME.getExtensions()) {
      if (framework.getApplicationDirectoryName().equals(fileName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLibDirectory(@Nullable VirtualFile file) {
    return file != null && "lib".equals(file.getName());
  }

  public void queue(@NotNull SyncAction action, @NotNull Object on) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) {
      return;
    }

    synchronized (myOrders) {
      myOrders.add(Pair.create(on, action));
    }
    StartupManager.getInstance(myProject).runAfterOpened(this::scheduleRunActions);
  }

  private void scheduleRunActions() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (ourGrailsTestFlag && !myProject.isInitialized()) {
        runActions(computeRawActions());
      }
      return;
    }

    ReadAction
      .nonBlocking(() -> computeRawActions())
      .expireWith(this)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.NON_MODAL, this::runActions)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private Set<Pair<Object, SyncAction>> takeOrderSnapshot() {
    synchronized (myOrders) {
      return new LinkedHashSet<>(myOrders);
    }
  }

  @NotNull
  private List<Module> determineModuleBySyncActionObject(@NotNull Object o) {
    if (o instanceof Module) {
      return Collections.singletonList((Module)o);
    }
    if (o instanceof Project) {
      return Arrays.asList(ModuleManager.getInstance((Project)o).getModules());
    }
    if (o instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)o;
      if (file.isValid()) {
        Module module = ModuleUtilCore.findModuleForFile(file, myProject);
        if (module == null) {
          return Collections.emptyList();
        }

        return Collections.singletonList(module);
      }
    }
    return Collections.emptyList();
  }

  @TestOnly
  public static void forceUpdateProject(@NotNull Project project) {
    MvcModuleStructureSynchronizer instance = getInstance(project);
    instance.getFileAndRootsModificationTracker().incModificationCount();
    instance.runActions(instance.computeRawActions());
  }

  private void runActions(@NotNull Set<? extends Trinity<Module, SyncAction, MvcFramework>> actions) {
    try {
      boolean isProjectStructureUpdated = false;

      for (Trinity<Module, SyncAction, MvcFramework> rawAction : actions) {
        Module module = rawAction.first;
        if (module.isDisposed()) {
          continue;
        }

        if (rawAction.second == SyncAction.UpdateProjectStructure && rawAction.third.updatesWholeProject()) {
          if (isProjectStructureUpdated) continue;
          isProjectStructureUpdated = true;
        }

        rawAction.second.doAction(module, rawAction.third);
      }
    }
    finally {
      // if there were any actions added during performSyncAction, clear them too
      // all needed actions are already added to buffer and have thus been performed
      // otherwise you may get repetitive 'run create-app?' questions
      synchronized (myOrders) {
        myOrders.clear();
      }
    }
  }

  @NotNull
  private Set<Trinity<Module, SyncAction, MvcFramework>> computeRawActions() {
    Set<Pair<Object, SyncAction>> actions = takeOrderSnapshot();
    //get module by object and kill duplicates
    Set<Trinity<Module, SyncAction, MvcFramework>> rawActions = new LinkedHashSet<>();
    for (Pair<Object, SyncAction> pair : actions) {
      for (Module module : determineModuleBySyncActionObject(pair.first)) {
        if (!module.isDisposed()) {
          MvcFramework framework = (pair.second == SyncAction.CreateAppStructureIfNeeded)
                                         ? MvcFramework.getInstanceBySdk(module)
                                         : MvcFramework.getInstance(module);

          if (framework != null && !framework.isAuxModule(module)) {
            rawActions.add(Trinity.create(module, pair.second, framework));
          }
        }
      }
    }
    return rawActions;
  }

  public enum SyncAction {
    SyncLibrariesInPluginsModule {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        if (MvcModuleStructureUtil.isEnabledStructureUpdate()) {
          framework.syncSdkAndLibrariesInPluginsModule(module);
        }
      }
    },

    UpgradeFramework {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        framework.upgradeFramework(module);
      }
    },

    CreateAppStructureIfNeeded {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        framework.createApplicationIfNeeded(module);
      }
    },

    UpdateProjectStructure {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        framework.updateProjectStructure(module);
      }
    },

    EnsureRunConfigurationExists {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        framework.ensureRunConfigurationExists(module);
      }
    },

    OutOfModuleDirectoryCreated {
      @Override
      void doAction(@NotNull Module module, @NotNull MvcFramework framework) {
        Project project = module.getProject();
        MvcModuleStructureSynchronizer mvcModuleStructureSynchronizer = getInstance(project);

        if (mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded) {
          mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded = false;

          Set<VirtualFile> roots = new HashSet<>();

          for (String rootPath : MvcWatchedRootProvider.getRootsToWatch(project)) {
            ContainerUtil.addIfNotNull(roots, LocalFileSystem.getInstance().findFileByPath(rootPath));
          }

          if (!roots.equals(mvcModuleStructureSynchronizer.myPluginRoots)) {
            mvcModuleStructureSynchronizer.myPluginRoots = roots;
            ApplicationManager.getApplication().invokeLater(() -> mvcModuleStructureSynchronizer.queue(UpdateProjectStructure, project));
          }
        }
      }
    };

    abstract void doAction(@NotNull Module module, @NotNull MvcFramework framework);
  }

  private void updateProjectViewVisibility() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    StartupManager.getInstance(myProject).runAfterOpened(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        for (ToolWindowEP ep : ToolWindowEP.EP_NAME.getExtensionList()) {
          Class<? extends ToolWindowFactory> factoryClass = ep.getFactoryClass(ep.getPluginDescriptor());
          if (factoryClass == null || !MvcToolWindowDescriptor.class.isAssignableFrom(factoryClass)) {
            continue;
          }

          MvcToolWindowDescriptor descriptor = (MvcToolWindowDescriptor)ep.getToolWindowFactory(ep.getPluginDescriptor());
          String id = descriptor.getToolWindowId();
          boolean shouldShow = descriptor.value(myProject);

          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
          ToolWindow toolWindow = toolWindowManager.getToolWindow(id);

          if (shouldShow && toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow(id, true, ToolWindowAnchor.LEFT, this, true);
            toolWindow.setIcon(descriptor.getFramework().getToolWindowIcon());
            descriptor.createToolWindowContent(myProject, toolWindow);
          }
          else if (!shouldShow && toolWindow != null) {
            toolWindowManager.unregisterToolWindow(id);
            Disposer.dispose(toolWindow.getContentManager());
          }
        }
      }, myProject.getDisposed());
    });
  }
}
