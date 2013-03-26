package org.jetbrains.android.importDependencies;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.newProjectWizard.SourcePathsStep;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class ImportDependenciesUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.importDependencies.ImportDependenciesUtil");
  private static final Key<Boolean> WAIT_FOR_IMPORTING_DEPENDENCIES_KEY = new Key<Boolean>("WAIT_FOR_IMPORTING_DEPENDENCIES_KEY");
  private static final Object LOCK = new Object();

  private ImportDependenciesUtil() {
  }

  public static void importDependencies(@NotNull final Module module,
                                        final boolean updateBackwardDependencies) {
    synchronized (LOCK) {
      final Project project = module.getProject();

      module.putUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY, Boolean.TRUE);

      if (project.getUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY) != Boolean.TRUE) {
        project.putUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY, Boolean.TRUE);

        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            // todo: this doesn't work in module configurator after 'Apply' button pressed
            if (module.isLoaded()) {
              importDependenciesForMarkedModules(project, updateBackwardDependencies);
            }
            else {
              final MessageBusConnection connection = module.getMessageBus().connect();
              connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
                @Override
                public void moduleAdded(final Project project, final Module addedModule) {
                  if (module.equals(addedModule)) {
                    connection.disconnect();
                    importDependenciesForMarkedModules(project, updateBackwardDependencies);
                  }
                }
              });
            }
          }
        });
      }
    }
  }

  private static void importDependenciesForMarkedModules(final Project project, final boolean updateBackwardDependencies) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        doImportDependenciesForMarkedModules(project, updateBackwardDependencies);
      }
    });
  }

  private static void doImportDependenciesForMarkedModules(Project project, boolean updateBackwardDependencies) {
    if (project.getUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY) != Boolean.TRUE) {
      return;
    }

    project.putUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY, null);

    final List<Module> modulesToProcess = new ArrayList<Module>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (module.getUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY) == Boolean.TRUE) {
        module.putUserData(WAIT_FOR_IMPORTING_DEPENDENCIES_KEY, null);
        modulesToProcess.add(module);
      }
    }
    doImportDependencies(project, modulesToProcess, updateBackwardDependencies);
  }

  public static void doImportDependencies(@NotNull Project project, @NotNull List<Module> modules, boolean updateBackwardDependencies) {
    final List<ImportDependenciesTask> tasks = new OrderedSet<ImportDependenciesTask>();
    final List<MyUnresolvedDependency> unresolvedDependencies = new ArrayList<MyUnresolvedDependency>();

    for (Module module : modules) {
      importDependencies(module, updateBackwardDependencies, tasks, unresolvedDependencies);
    }

    final Map<VirtualFile, ModuleProvidingTask> libDir2ModuleProvidingTask = new HashMap<VirtualFile, ModuleProvidingTask>();
    for (ImportDependenciesTask task : tasks) {
      if (task instanceof ModuleProvidingTask) {
        final ModuleProvidingTask moduleProvidingTask = (ModuleProvidingTask)task;
        libDir2ModuleProvidingTask.put(moduleProvidingTask.getContentRoot(), moduleProvidingTask);
      }
    }

    for (MyUnresolvedDependency unresolvedDependency : unresolvedDependencies) {
      final ModuleProvidingTask taskProvidingDepModule = libDir2ModuleProvidingTask.get(unresolvedDependency.myLibDir);
      if (taskProvidingDepModule != null) {
        tasks.add(new AddModuleDependencyTask(unresolvedDependency.myModuleProvider,
                                              ModuleProvider.create(taskProvidingDepModule)));
      }
    }

    if (tasks.size() > 0) {
      doImportDependencies(project, tasks);
    }
  }

  private static void importDependencies(Module module,
                                         boolean updateBackwardDependencies,
                                         List<ImportDependenciesTask> tasks,
                                         List<MyUnresolvedDependency> unresolvedDependencies) {
    importDependencies(module, null, tasks, unresolvedDependencies);

    if (updateBackwardDependencies) {
      importBackwardDependencies(module, tasks, unresolvedDependencies);
    }
  }

  private static void doImportDependencies(@NotNull Project project, @NotNull List<ImportDependenciesTask> tasks) {
    final ImportDependenciesDialog dialog = new ImportDependenciesDialog(project, tasks);
    dialog.show();

    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }

    final List<ImportDependenciesTask> selectedTasks = dialog.getSelectedTasks();
    final StringBuilder messageBuilder = new StringBuilder();
    boolean failed = false;
    final List<CreateNewModuleTask> createNewModuleTasks = new ArrayList<CreateNewModuleTask>();

    for (ImportDependenciesTask selectedTask : selectedTasks) {
      final Exception error = selectedTask.perform();
      if (error != null) {
        LOG.info(error);
        if (messageBuilder.length() > 0) {
          messageBuilder.append('\n');
        }
        messageBuilder.append(error.getMessage());
        failed = true;
      }
      else if (selectedTask instanceof CreateNewModuleTask) {
        createNewModuleTasks.add((CreateNewModuleTask)selectedTask);
      }
    }

    if (createNewModuleTasks.size() > 0) {
      final List<JavaModuleSourceRoot> sourceRoots = new ArrayList<JavaModuleSourceRoot>();
      for (CreateNewModuleTask task : createNewModuleTasks) {
        final String contentRootPath = task.getContentRoot().getPath();
        sourceRoots.addAll(SourcePathsStep.calculateSourceRoots(contentRootPath));
      }

      if (sourceRoots.size() > 0) {
        final ImportSourceRootsDialog sourceRootsDialog = new ImportSourceRootsDialog(project, sourceRoots);
        sourceRootsDialog.show();

        if (sourceRootsDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          addSourceRoots(project, sourceRootsDialog.getMarkedElements());
        }
      }
    }

    if (failed) {
      Messages.showErrorDialog(project, AndroidBundle.message("android.import.dependencies.error.message.header") +
                                        "\n" +
                                        messageBuilder.toString(), CommonBundle.getErrorTitle());
    }
  }

  private static void addSourceRoots(final Project project, final List<JavaModuleSourceRoot> sourceRoots) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (JavaModuleSourceRoot sourceRootTrinity : sourceRoots) {
          final String path = sourceRootTrinity.getDirectory().getPath();
          final VirtualFile sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(path));
          if (sourceRoot == null) {
            LOG.debug(new Exception("Cannot find source root " + path));
            continue;
          }

          final Module module = ModuleUtil.findModuleForFile(sourceRoot, project);
          if (module == null) {
            LOG.debug(new Exception("Cannot find module for file " + sourceRoot.getPath()));
            continue;
          }

          final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          final ContentEntry[] entries = model.getContentEntries();
          if (entries.length > 0) {
            entries[0].addSourceFolder(sourceRoot, false, sourceRootTrinity.getPackagePrefix());
          }
          else {
            LOG.debug(new Exception("Module " + module.getName() + " has no content entries"));
          }
          model.commit();
        }
      }
    });
  }

  @Nullable
  private static VirtualFile findModuleFileChild(@NotNull VirtualFile dir) {
    for (VirtualFile child : dir.getChildren()) {
      if (child.getFileType() instanceof ModuleFileType) {
        return child;
      }
    }
    return null;
  }

  private static class MyUnresolvedDependency {
    final ModuleProvider myModuleProvider;
    final VirtualFile myLibDir;

    private MyUnresolvedDependency(ModuleProvider moduleProvider, VirtualFile libDir) {
      myModuleProvider = moduleProvider;
      myLibDir = libDir;
    }
  }

  private static void importDependencies(@NotNull Module module,
                                         @Nullable Module allowedDepModule,
                                         @NotNull List<ImportDependenciesTask> tasks,
                                         @NotNull List<MyUnresolvedDependency> unresolvedDependencies) {
    final Project project = module.getProject();
    final ModuleProvider moduleProvider = ModuleProvider.create(module);
    final Pair<Properties, VirtualFile> pair = AndroidRootUtil.readProjectPropertyFile(module);

    if (pair != null) {
      doImportDependencies(module, allowedDepModule, tasks, unresolvedDependencies, project, moduleProvider, pair);
    }
  }

  private static void importDependenciesForNewModule(@NotNull Project project,
                                                     @NotNull ModuleProvider newModuleProvider,
                                                     @NotNull VirtualFile newModuleContentRoot,
                                                     @NotNull List<ImportDependenciesTask> tasks,
                                                     @NotNull List<MyUnresolvedDependency> unresolvedDependencies) {
    final Pair<Properties, VirtualFile> properties =
      AndroidRootUtil.readProjectPropertyFile(newModuleContentRoot);
    if (properties != null) {
      doImportDependencies(null, null, tasks, unresolvedDependencies, project, newModuleProvider, properties);
    }
  }

  private static void doImportDependencies(@Nullable Module module,
                                           @Nullable Module allowedDepModule,
                                           @NotNull List<ImportDependenciesTask> tasks,
                                           @NotNull List<MyUnresolvedDependency> unresolvedDependencies,
                                           @NotNull Project project,
                                           @NotNull ModuleProvider moduleProvider,
                                           @NotNull Pair<Properties, VirtualFile> defaultProperties) {
    for (VirtualFile libDir : getLibDirs(defaultProperties)) {
      final Module depModule = ModuleUtil.findModuleForFile(libDir, project);

      if (depModule != null) {
        if ((allowedDepModule == null || allowedDepModule == depModule) &&
            ArrayUtil.find(ModuleRootManager.getInstance(depModule).getContentRoots(), libDir) >= 0 &&
            !(module != null && ModuleRootManager.getInstance(module).isDependsOn(depModule))) {

          tasks.add(new AddModuleDependencyTask(moduleProvider, ModuleProvider.create(depModule)));
        }
      }
      else {
        final VirtualFile libModuleFile = findModuleFileChild(libDir);
        final ModuleProvidingTask task = libModuleFile != null && new File(libModuleFile.getPath()).exists()
                                         ? new ImportModuleTask(project, libModuleFile.getPath(), libDir)
                                         : new CreateNewModuleTask(project, libDir);
        if (!tasks.contains(task)) {
          tasks.add(task);
          final ModuleProvider newModuleProvider = ModuleProvider.create(task);
          tasks.add(new AddModuleDependencyTask(moduleProvider, newModuleProvider));
          importDependenciesForNewModule(project, newModuleProvider, libDir, tasks, unresolvedDependencies);
        }
        else {
          unresolvedDependencies.add(new MyUnresolvedDependency(moduleProvider, libDir));
        }
      }
    }
  }

  @NotNull
  public static Set<VirtualFile> getLibDirs(@NotNull Pair<Properties, VirtualFile> properties) {
    final Set<VirtualFile> resultSet = new HashSet<VirtualFile>(); 
    final VirtualFile baseDir = properties.second.getParent();
    
    String libDirPath;
    int i = 1;
    do {
      libDirPath = properties.first.getProperty(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX + i);
      if (libDirPath != null) {
        final VirtualFile libDir = AndroidUtils.findFileByAbsoluteOrRelativePath(baseDir, FileUtil.toSystemIndependentName(libDirPath));
        if (libDir != null) {
          resultSet.add(libDir);
        }
      }
      i++;
    }
    while (libDirPath != null);
    
    return resultSet;
  }

  private static void importBackwardDependencies(@NotNull Module module, @NotNull List<ImportDependenciesTask> tasks,
                                                 @NotNull List<MyUnresolvedDependency> unresolvedDependencies) {
    for (Module module1 : ModuleManager.getInstance(module.getProject()).getModules()) {
      if (module1 != module) {
        importDependencies(module1, module, tasks, unresolvedDependencies);
      }
    }
  }
}
