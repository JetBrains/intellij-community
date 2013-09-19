/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.groovy.mvc.projectView.MvcToolWindowDescriptor;

import java.util.*;

/**
 * @author peter
 */
public class MvcModuleStructureSynchronizer extends AbstractProjectComponent {
  private final Set<Pair<Object, SyncAction>> myActions = new LinkedHashSet<Pair<Object, SyncAction>>();

  private Set<VirtualFile> myPluginRoots = Collections.emptySet();

  private long myModificationCount = 0;

  private boolean myOutOfModuleDirectoryCreatedActionAdded;

  public static boolean ourGrailsTestFlag;

  private final ModificationTracker myModificationTracker = new ModificationTracker() {
    @Override
    public long getModificationCount() {
      return myModificationCount;
    }
  };

  public MvcModuleStructureSynchronizer(Project project) {
    super(project);
  }

  public ModificationTracker getFileAndRootsModificationTracker() {
    return myModificationTracker;
  }

  @Override
  public void initComponent() {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        queue(SyncAction.SyncLibrariesInPluginsModule, myProject);
        queue(SyncAction.UpgradeFramework, myProject);
        queue(SyncAction.CreateAppStructureIfNeeded, myProject);
        queue(SyncAction.UpdateProjectStructure, myProject);
        queue(SyncAction.EnsureRunConfigurationExists, myProject);
        myModificationCount++;

        updateProjectViewVisibility();
      }
    });

    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(Project project, Module module) {
        queue(SyncAction.UpdateProjectStructure, module);
        queue(SyncAction.CreateAppStructureIfNeeded, module);
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
      @Override
      public void fileCreated(final VirtualFileEvent event) {
        myModificationCount++;

        final VirtualFile file = event.getFile();
        final String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName) || isApplicationDirectoryName(fileName)) {
          queue(SyncAction.UpdateProjectStructure, file);
          queue(SyncAction.EnsureRunConfigurationExists, file);
        }
        else if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
        else {
          if (!myProject.isInitialized()) return;

          final Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);

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

          final MvcFramework framework = MvcFramework.getInstance(module);
          if (framework == null) return;

          if (framework.isToReformatOnCreation(file) || file.isDirectory()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!file.isValid()) return;
                if (!framework.hasSupport(module)) return;

                final List<VirtualFile> files = new ArrayList<VirtualFile>();

                if (file.isDirectory()) {
                  ModuleRootManager.getInstance(module).getFileIndex().iterateContentUnderDirectory(file, new ContentIterator() {
                    @Override
                    public boolean processFile(VirtualFile fileOrDir) {
                      if (!fileOrDir.isDirectory() && framework.isToReformatOnCreation(fileOrDir)) {
                        files.add(file);
                      }
                      return true;
                    }
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
              }
            }, module.getDisposed());
          }
        }
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        myModificationCount++;

        final VirtualFile file = event.getFile();
        if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
      }

      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName)) {
          queue(SyncAction.UpdateProjectStructure, event.getFile());
        }
      }

      @Override
      public void fileMoved(VirtualFileMoveEvent event) {
        myModificationCount++;
      }

      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          myModificationCount++;
        }
      }
    }));
  }

  public static MvcModuleStructureSynchronizer getInstance(Project project) {
    return project.getComponent(MvcModuleStructureSynchronizer.class);
  }

  private static boolean isApplicationDirectoryName(String fileName) {
    for (MvcFramework framework : MvcFramework.EP_NAME.getExtensions()) {
      if (framework.getApplicationDirectoryName().equals(fileName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLibDirectory(@Nullable final VirtualFile file) {
    return file != null && "lib".equals(file.getName());
  }

  @Override
  public void projectOpened() {
    queue(SyncAction.UpdateProjectStructure, myProject);
    queue(SyncAction.EnsureRunConfigurationExists, myProject);
    queue(SyncAction.UpgradeFramework, myProject);
    queue(SyncAction.CreateAppStructureIfNeeded, myProject);
  }

  private void queue(SyncAction action, Object on) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myActions.isEmpty()) {
      if (myProject.isDisposed()) return;
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          Application app = ApplicationManager.getApplication();
          if (!app.isUnitTestMode()) {
            app.invokeLater(new Runnable() {
              @Override
              public void run() {
                runActions();
              }
            }, ModalityState.NON_MODAL);
          }
          else {
            runActions();
          }
        }
      });
    }

    myActions.add(new Pair<Object, SyncAction>(on, action));
  }

  @NotNull
  private List<Module> determineModuleBySyncActionObject(Object o) {
    if (o instanceof Module) {
      return Collections.singletonList((Module)o);
    }
    if (o instanceof Project) {
      return Arrays.asList(ModuleManager.getInstance((Project)o).getModules());
    }
    if (o instanceof VirtualFile) {
      final VirtualFile file = (VirtualFile)o;
      if (file.isValid()) {
        final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
        if (module == null) {
          return Collections.emptyList();
        }

        return Collections.singletonList(module);
      }
    }
    return Collections.emptyList();
  }

  @TestOnly
  public static void forceUpdateProject(Project project) {
    project.getComponent(MvcModuleStructureSynchronizer.class).runActions();
  }

  private void runActions() {
    try {
      if (myProject.isDisposed()) {
        return;
      }

      if (ApplicationManager.getApplication().isUnitTestMode() && !ourGrailsTestFlag) {
        return;
      }

      @SuppressWarnings("unchecked") Pair<Object, SyncAction>[] actions = myActions.toArray(new Pair[myActions.size()]);
      //get module by object and kill duplicates

      final Set<Trinity<Module, SyncAction, MvcFramework>> rawActions = new LinkedHashSet<Trinity<Module, SyncAction, MvcFramework>>();

      for (final Pair<Object, SyncAction> pair : actions) {
        for (Module module : determineModuleBySyncActionObject(pair.first)) {
          if (!module.isDisposed()) {
            final MvcFramework framework = (pair.second == SyncAction.CreateAppStructureIfNeeded)
                                           ? MvcFramework.getInstanceBySdk(module)
                                           : MvcFramework.getInstance(module);

            if (framework != null && !framework.isAuxModule(module)) {
              rawActions.add(Trinity.create(module, pair.second, framework));
            }
          }
        }
      }

      boolean isProjectStructureUpdated = false;

      for (final Trinity<Module, SyncAction, MvcFramework> rawAction : rawActions) {
        final Module module = rawAction.first;
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
      myActions.clear();
    }
  }

  private enum SyncAction {
    SyncLibrariesInPluginsModule {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.syncSdkAndLibrariesInPluginsModule(module);
      }
    },

    UpgradeFramework {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.upgradeFramework(module);
      }
    },

    CreateAppStructureIfNeeded {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.createApplicationIfNeeded(module);
      }
    },

    UpdateProjectStructure {
      @Override
      void doAction(final Module module, final MvcFramework framework) {
        framework.updateProjectStructure(module);
      }
    },

    EnsureRunConfigurationExists {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.ensureRunConfigurationExists(module);
      }
    },

    OutOfModuleDirectoryCreated {
      @Override
      void doAction(Module module, MvcFramework framework) {
        final Project project = module.getProject();
        final MvcModuleStructureSynchronizer mvcModuleStructureSynchronizer = getInstance(project);

        if (mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded) {
          mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded = false;

          Set<VirtualFile> roots = new HashSet<VirtualFile>();

          for (String rootPath : MvcWatchedRootProvider.getRootsToWatch(project)) {
            ContainerUtil.addIfNotNull(roots, LocalFileSystem.getInstance().findFileByPath(rootPath));
          }

          if (!roots.equals(mvcModuleStructureSynchronizer.myPluginRoots)) {
            mvcModuleStructureSynchronizer.myPluginRoots = roots;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                mvcModuleStructureSynchronizer.queue(UpdateProjectStructure, project);
              }
            });
          }
        }
      }
    };

    abstract void doAction(Module module, MvcFramework framework);
  }

  private void updateProjectViewVisibility() {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myProject.isDisposed()) return;

            for (ToolWindowEP ep : ToolWindowEP.EP_NAME.getExtensions()) {
              if (MvcToolWindowDescriptor.class.isAssignableFrom(ep.getFactoryClass())) {
                MvcToolWindowDescriptor descriptor = (MvcToolWindowDescriptor)ep.getToolWindowFactory();
                String id = descriptor.getToolWindowId();
                boolean shouldShow = MvcModuleStructureUtil.hasModulesWithSupport(myProject, descriptor.getFramework());

                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

                ToolWindow toolWindow = toolWindowManager.getToolWindow(id);

                if (shouldShow && toolWindow == null) {
                  toolWindow = toolWindowManager.registerToolWindow(id, true, ToolWindowAnchor.LEFT, myProject, true);
                  toolWindow.setIcon(descriptor.getFramework().getToolWindowIcon());
                  descriptor.createToolWindowContent(myProject, toolWindow);
                }
                else if (!shouldShow && toolWindow != null) {
                  toolWindowManager.unregisterToolWindow(id);
                  Disposer.dispose(toolWindow.getContentManager());
                }
              }
            }
          }
        });
      }
    });
  }

}
