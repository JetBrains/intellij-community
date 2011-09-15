/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author peter
 */
public class MvcModuleStructureSynchronizer extends AbstractProjectComponent {
  private final Set<Pair<Object, SyncAction>> myActions = new LinkedHashSet<Pair<Object, SyncAction>>();

  public MvcModuleStructureSynchronizer(Project project) {
    super(project);
  }

  public void initComponent() {
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        queue(SyncAction.SyncLibrariesInPluginsModule, myProject);
        queue(SyncAction.UpgradeFramework, myProject);
        queue(SyncAction.CreateAppStructureIfNeeded, myProject);
        queue(SyncAction.UpdateProjectStructure, myProject);
        queue(SyncAction.EnsureRunConfigurationExists, myProject);
      }
    });

    connection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
      public void moduleAdded(Project project, Module module) {
        queue(SyncAction.UpdateProjectStructure, module);
        queue(SyncAction.CreateAppStructureIfNeeded, module);
      }

      public void beforeModuleRemoved(Project project, Module module) {
      }

      public void moduleRemoved(Project project, Module module) {
      }

      public void modulesRenamed(Project project, List<Module> modules) {
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
      public void fileCreated(final VirtualFileEvent event) {
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
              if (file.findChild(MvcModuleStructureUtil.APPLICATION_PROPERTIES) != null) {
                queue(SyncAction.UpdateProjectStructure, myProject);
              }
            }
            return;
          }

          if (!MvcConsole.isUpdatingVfsByConsoleProcess(module)) return;

          final MvcFramework framework = getFramework(module);
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
                    new ReformatCodeProcessor(myProject, psiFile, null).run();
                  }
                }
              }
            }, module.getDisposed());
          }
        }
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
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
    }));
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

  public void projectOpened() {
    queue(SyncAction.UpdateProjectStructure, myProject);
    queue(SyncAction.EnsureRunConfigurationExists, myProject);
    queue(SyncAction.UpgradeFramework, myProject);
    queue(SyncAction.CreateAppStructureIfNeeded, myProject);
  }

  private void queue(SyncAction action, Object on) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (myActions) {
      if (myActions.isEmpty()) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                runActions();
              }
            }, ModalityState.NON_MODAL);
          }
        });
      }

      myActions.add(new Pair<Object, SyncAction>(on, action));
    }
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
        final Module module = ModuleUtil.findModuleForFile(file, myProject);
        if (module == null) {
          return Collections.emptyList();
        }

        return Collections.singletonList(module);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static MvcFramework getFramework(@Nullable Module module) {
    if (module == null) {
      return null;
    }

    for (final MvcFramework framework : MvcFramework.EP_NAME.getExtensions()) {
      if (framework.hasSupport(module)) {
        return framework;
      }
    }
    return null;
  }

  @Nullable
  public static MvcFramework getFrameworkByFrameworkSdk(@NotNull Module module) {
    for (final MvcFramework framework : MvcFramework.EP_NAME.getExtensions()) {
      if (framework.getSdkRoot(module) != null) {
        return framework;
      }
    }
    return null;
  }

  @TestOnly
  public static void forceUpdateProject(Project project) {
    project.getComponent(MvcModuleStructureSynchronizer.class).runActions();
  }

  private void runActions() {
    try {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (myProject.isDisposed()) {
        return;
      }

      Pair<Object, SyncAction>[] actions;
      //get module by object and kill duplicates
      synchronized (myActions) {
        actions = myActions.toArray(new Pair[myActions.size()]);
      }

      final Set<Trinity<Module, SyncAction, MvcFramework>> rawActions = new LinkedHashSet<Trinity<Module, SyncAction, MvcFramework>>();

      for (final Pair<Object, SyncAction> pair : actions) {
        for (Module module : determineModuleBySyncActionObject(pair.first)) {
          if (!module.isDisposed()) {
            final MvcFramework framework = (pair.second == SyncAction.CreateAppStructureIfNeeded)
                                           ? getFrameworkByFrameworkSdk(module)
                                           : getFramework(module);

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
      synchronized (myActions) {
        myActions.clear();
      }
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
    };

    abstract void doAction(Module module, MvcFramework framework);
  }
}
