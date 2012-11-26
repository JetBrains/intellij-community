package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.application.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates functionality of creating IntelliJ IDEA modules on the basis of {@link GradleModule gradle modules}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/26/11 10:01 AM
 */
// TODO den remove
public class GradleModulesImporter {
  
  private static final Map<LibraryPathType, OrderRootType> LIBRARY_ROOT_MAPPINGS
    = new EnumMap<LibraryPathType, OrderRootType>(LibraryPathType.class);
  static {
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.DOC, JavadocOrderRootType.getInstance());
    assert LibraryPathType.values().length == LIBRARY_ROOT_MAPPINGS.size();
  }
  
  /**
   * We can't modify project modules (add/remove) until it's initialised, so, we delay that activity. Current constant
   * holds number of milliseconds to wait between 'after project initialisation' processing attempts.
   */
  private static final int PROJECT_INITIALISATION_DELAY_MS = (int)TimeUnit.SECONDS.toMillis(1);
  
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  /**
   * Entry point for the whole 'import modules' procedure.
   * 
   * @param modules            module info containers received from the gradle api
   * @param project            project that should host the modules
   * @param model              modules model
   * @param gradleProjectPath  file system path to the gradle project file being imported
   * @return                   mappings between the given gradle modules and newly created intellij modules
   */
  @NotNull
  public Map<GradleModule, Module> importModules(@NotNull final Iterable<GradleModule> modules, @Nullable final Project project, 
                                                 @Nullable final ModifiableModuleModel model, @NotNull String gradleProjectPath)
  {
    if (project == null) {
      return Collections.emptyMap();
    }
    removeExistingModulesSettings(modules);
    if (!project.isInitialized()) {
      myAlarm.addRequest(new ImportModulesTask(project, modules, gradleProjectPath), PROJECT_INITIALISATION_DELAY_MS);
      return Collections.emptyMap();
    } 
    if (model == null) {
      return Collections.emptyMap();
    }
    return importModules(modules, model, project, gradleProjectPath);
  }

  private static void removeExistingModulesSettings(@NotNull Iterable<GradleModule> modules) {
    for (GradleModule module : modules) {
      // Remove existing '*.iml' file if necessary.
      final String moduleFilePath = module.getModuleFilePath();
      File file = new File(moduleFilePath);
      if (file.isFile()) {
        boolean success = file.delete();
        if (!success) {
          GradleLog.LOG.warn("Can't remove existing module file at '" + moduleFilePath + "'");
        }
      }
    }
  }
  
  public Map<GradleModule, Module> importModules(@NotNull final Iterable<GradleModule> modules, 
                                                 @NotNull final ModifiableModuleModel model,
                                                 @NotNull final Project intellijProject,
                                                 @NotNull final String gradleProjectPath)
  {
    final Map<GradleModule, Module> result = new HashMap<GradleModule, Module>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        Application application = ApplicationManager.getApplication();
        AccessToken writeLock = application.acquireWriteActionLock(getClass());
        try {
          final List<ModifiableRootModel> rootModels = new ArrayList<ModifiableRootModel>();
          final GradleProjectEntityChangeListener publisher =
            intellijProject.getMessageBus().syncPublisher(GradleProjectEntityChangeListener.TOPIC);
          for (GradleModule module : modules) {
            publisher.onChangeStart(module);
          }
          try {
            Map<GradleModule, Module> moduleMappings = doImportModules(modules, model, rootModels);
            result.putAll(moduleMappings);
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(
              new SetupExternalLibrariesTask(moduleMappings, gradleProjectPath, intellijProject),
              PROJECT_INITIALISATION_DELAY_MS
            );
          }
          finally {
            ModifiableRootModel[] modelsAsArray = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
            ModifiableModelCommitter.multiCommit(modelsAsArray, model);
            for (GradleModule module : modules) {
              publisher.onChangeEnd(module);
            }
          }
        }
        finally {
          writeLock.finish();
        }
      }
    });
    return result;
  }

  /**
   * Actual implementation of {@link #importModules(Iterable, Project, ModifiableModuleModel, String)}.
   * Insists on all arguments to be ready to use.
   *  
   * @param modules     modules to import
   * @param model       modules model
   * @param rootModels  holder for the module root modules. Is expected to be populated during the current method processing
   * @return            mappings between the given gradle modules and corresponding intellij modules
   */
  @NotNull
  @SuppressWarnings("MethodMayBeStatic")
  private Map<GradleModule, Module> doImportModules(@NotNull Iterable<GradleModule> modules,
                                                    @NotNull ModifiableModuleModel model,
                                                    @NotNull List<ModifiableRootModel> rootModels)
  {
    Map<GradleModule, Module> result = new HashMap<GradleModule, Module>();
    for (GradleModule moduleToImport : modules) {
      Module createdModule = createModule(moduleToImport, model);
      result.put(moduleToImport, createdModule);
    }
    for (GradleModule moduleToImport : modules) {
      ModifiableRootModel rootModel = configureModule(moduleToImport, result);
      rootModels.add(rootModel);
    }
    return result;
  }

  /**
   * We need to create module objects for all modules at first and then configure them. That is necessary for setting up
   * module dependencies.
   * 
   * @param module  gradle module to import
   * @param model   module model
   * @return        newly created IJ module
   */
  @NotNull
  private static Module createModule(@NotNull GradleModule module, @NotNull ModifiableModuleModel model) {
    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();
    final String moduleFilePath = module.getModuleFilePath();
    return model.newModule(moduleFilePath, StdModuleTypes.JAVA.getId());
  }

  /**
   * Applies module settings received from the gradle api (encapsulate at the given {@link GradleModule} object) to the
   * target intellij module (retrieved from the given module mappings).
   * 
   * @param module   target gradle module which corresponding intellij module should be configured
   * @param modules  gradle module to intellij modules mappings. Is assumed to have a value for the given gradle modules used as a key
   * @return         module roots model used during configuration
   */
  @NotNull
  private static ModifiableRootModel configureModule(@NotNull GradleModule module, @NotNull Map<GradleModule, Module> modules) {
    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();
    
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(modules.get(module));
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    configureModule(module, rootModel, modules);
    return rootModel;
  }

  /**
   * Contains actual logic of {@link #configureModule(GradleModule, Map)}.
   * 
   * @param module   target module settings holder
   * @param model    intellij module setting manager
   * @param modules  modules mappings
   */
  private static void configureModule(@NotNull GradleModule module, @NotNull final ModifiableRootModel model,
                                      @NotNull final Map<GradleModule, Module> modules)
  {
    // Ensure that dependencies are clear.
    final Object key = new Object();
    final Object dummy = new Object();
    RootPolicy<Object> policy = new RootPolicy<Object>() {
      @Override
      public Object visitModuleSourceOrderEntry(ModuleSourceOrderEntry moduleSourceOrderEntry, Object value) {
        return key;
      }
    };
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      // Don't remove 'module source' order entry (configured automatically on module creation).
      if (key != orderEntry.accept(policy, dummy)) {
        model.removeOrderEntry(orderEntry);
      }
    }
    
    // Configure SDK.
    model.inheritSdk();

    // Compile output.
    CompilerModuleExtension compilerExtension = model.getModuleExtension(CompilerModuleExtension.class);
    compilerExtension.inheritCompilerOutputPath(module.isInheritProjectCompileOutputPath());
    if (!module.isInheritProjectCompileOutputPath()) {
      String compileOutputPath = module.getCompileOutputPath(SourceType.SOURCE);
      String testCompileOutputPath = module.getCompileOutputPath(SourceType.TEST);
      if (StringUtil.isEmpty(compileOutputPath) || StringUtil.isEmpty(testCompileOutputPath)) {
        GradleLog.LOG.warn(String.format(
          "Module '%s' doesn't inherit project compile output path but has incomplete local setup. Falling back to the project "
          + "compile output path. Local compile output path: '%s', local test compile output path: '%s'",
          module.getName(), compileOutputPath, testCompileOutputPath
        ));
        compilerExtension.inheritCompilerOutputPath(true);
      }
      else {
        compilerExtension.setCompilerOutputPath(compileOutputPath);
        compilerExtension.setCompilerOutputPathForTests(testCompileOutputPath);
      }
    }
    
    // Content roots.
    for (GradleContentRoot contentRoot : module.getContentRoots()) {
      ContentEntry contentEntry = model.addContentEntry(toVfsUrl(contentRoot.getRootPath()));
      for (String path : contentRoot.getPaths(SourceType.SOURCE)) {
        contentEntry.addSourceFolder(toVfsUrl(path), false);
      }
      for (String path : contentRoot.getPaths(SourceType.TEST)) {
        contentEntry.addSourceFolder(toVfsUrl(path), true);
      }
      for (String path : contentRoot.getPaths(SourceType.EXCLUDED)) {
        contentEntry.addExcludeFolder(toVfsUrl(path));
      }
    }
    
    // Module dependencies.
    for (GradleDependency dependency : module.getDependencies()) {
      dependency.invite(new GradleEntityVisitorAdapter() {
        @Override
        public void visit(@NotNull GradleModuleDependency dependency) {
          ModuleOrderEntry orderEntry = model.addModuleOrderEntry(modules.get(dependency.getTarget()));
          orderEntry.setExported(dependency.isExported());
          orderEntry.setScope(dependency.getScope());
        }
      });
    }
  }

  /**
   * Resolves (downloads if necessary) external libraries necessary for the gradle project located at the given path and configures
   * them for the corresponding intellij project.
   * <p/>
   * <b>Note:</b> is assumed to be executed under write action.
   * 
   * @param moduleMappings     gradle-intellij module mappings
   * @param intellijProject    intellij project for the target gradle project
   * @param gradleProjectPath  file system path to the target gradle project
   */
  private static void setupLibraries(@NotNull final Map<GradleModule, Module> moduleMappings,
                                     @NotNull final Project intellijProject,
                                     @NotNull final String gradleProjectPath)
  {
    final Ref<GradleProject> gradleProjectRef = new Ref<GradleProject>();
    final Ref<Library> libraryToPreserve = new Ref<Library>();
    
    final Runnable setupExternalDependenciesTask = new Runnable() {
      @Override
      public void run() {
        final GradleProject gradleProject = gradleProjectRef.get();
        if (gradleProject == null) {
          return;
        }

        Application application = ApplicationManager.getApplication();
        AccessToken writeLock = application.acquireWriteActionLock(getClass());
        try {
          doSetupLibraries(moduleMappings, gradleProject, intellijProject, libraryToPreserve.get());
        }
        finally {
          writeLock.finish();
        }

        if (intellijProject.isDisposed()) {
          return;
        }
        
        // Force refresh the infrastructure in order to apply newly introduce intellij project structure changes
        final GradleProjectStructureChangesModel changesModel = intellijProject.getComponent(GradleProjectStructureChangesModel.class);
        if (changesModel != null) {
          final GradleProject project = changesModel.getGradleProject();
          if (project != null) {
            changesModel.update(project);
          }
        }
      }
    };
    
    final Runnable resolveDependenciesTask = new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().run(
          new Task.Backgroundable(intellijProject, GradleBundle.message("gradle.library.resolve.progress.text"), false) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
              GradleResolveProjectTask task = new GradleResolveProjectTask(intellijProject, gradleProjectPath, true);
              task.execute(indicator);
              GradleProject projectWithResolvedLibraries = task.getGradleProject();
              gradleProjectRef.set(projectWithResolvedLibraries);
              ApplicationManager.getApplication().invokeLater(setupExternalDependenciesTask, ModalityState.NON_MODAL);
            }
          }); 
      }
    };

    UIUtil.invokeLaterIfNeeded(resolveDependenciesTask);
  }

  private static void doSetupLibraries(@NotNull Map<GradleModule, Module> moduleMappings,
                                       @NotNull GradleProject gradleProject,
                                       @NotNull Project intellijProject,
                                       @Nullable Library libraryToPreserve) {
    if (intellijProject.isDisposed()) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();

    LibraryTable projectLibraryTable = ProjectLibraryTable.getInstance(intellijProject);
    if (projectLibraryTable == null) {
      GradleLog.LOG.warn(
        "Can't resolve external dependencies of the target gradle project (" + intellijProject + "). Reason: project "
        + "library table is undefined"
      );
      return;
    }
    LibraryTable.ModifiableModel model = projectLibraryTable.getModifiableModel();
    // Clean existing libraries (if any).
    try {
      for (Library library : model.getLibraries()) {
        if (libraryToPreserve != library) {
          model.removeLibrary(library);
        }
      }
    }
    finally {
      model.commit();
    }

    model = projectLibraryTable.getModifiableModel();
    List<ModifiableRootModel> modelsToCommit = new ArrayList<ModifiableRootModel>();
    Map<GradleLibrary, Library> libraryMappings = registerProjectLibraries(gradleProject, model);
    final GradleProjectEntityChangeListener publisher
      = intellijProject.getMessageBus().syncPublisher(GradleProjectEntityChangeListener.TOPIC);
    try {
      if (libraryMappings == null) {
        return;
      }
      for (GradleLibrary library : libraryMappings.keySet()) {
        publisher.onChangeStart(library);
      }
      modelsToCommit.addAll(configureModulesLibraryDependencies(moduleMappings, libraryMappings, gradleProject));
    }
    finally {
      model.commit();
      ProjectRootManager projectRootManager = ProjectRootManager.getInstance(intellijProject);
      ModifiableRootModel[] modelsAsArray = modelsToCommit.toArray(new ModifiableRootModel[modelsToCommit.size()]);
      if (modelsAsArray.length > 0) {
        ModifiableModelCommitter.multiCommit(modelsAsArray, ModuleManager.getInstance(modelsAsArray[0].getProject()).getModifiableModel());
      }
      if (libraryMappings != null) {
        for (GradleLibrary library : libraryMappings.keySet()) {
          publisher.onChangeEnd(library);
        }
      }
    }
  }

  /**
   * Registers {@link GradleProject#getLibraries() libraries} of the given gradle project at the intellij project.
   * 
   * @param gradleProject    target gradle project being imported
   * @param librariesModel   model that manages project libraries
   * @return                 mapping between libraries of the given gradle and intellij projects
   */
  @Nullable
  private static Map<GradleLibrary, Library> registerProjectLibraries(@NotNull GradleProject gradleProject,
                                                                      @NotNull LibraryTable.ModifiableModel librariesModel)
  {
    Map<GradleLibrary, Library> libraryMappings = new HashMap<GradleLibrary, Library>();
    for (GradleLibrary gradleLibrary : gradleProject.getLibraries()) {
      Library intellijLibrary = librariesModel.createLibrary(gradleLibrary.getName());
      libraryMappings.put(gradleLibrary, intellijLibrary);
      Library.ModifiableModel model = intellijLibrary.getModifiableModel();
      try {
        registerPath(gradleLibrary, model);
      }
      finally {
        model.commit();
      }
    }
    return libraryMappings;
  }

  private static Collection<ModifiableRootModel> configureModulesLibraryDependencies(
    @NotNull Map<GradleModule, Module> moduleMappings,
    @NotNull final Map<GradleLibrary, Library> libraryMappings,
    @NotNull GradleProject gradleProject)
  {
    List<ModifiableRootModel> result = new ArrayList<ModifiableRootModel>();
    for (GradleModule gradleModule : gradleProject.getModules()) {
      Module intellijModule = moduleMappings.get(gradleModule);
      if (intellijModule == null) {
        GradleLog.LOG.warn(String.format(
          "Can't find intellij module for the gradle module '%s'. Registered mappings: %s", gradleModule, moduleMappings
        ));
        continue;
      }
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(intellijModule);
      final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
      result.add(moduleRootModel);
      GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
        @Override
        public void visit(@NotNull GradleLibraryDependency dependency) {
          GradleLibrary gradleLibrary = dependency.getTarget();
          Library intellijLibrary = libraryMappings.get(gradleLibrary);
          if (intellijLibrary == null) {
            GradleLog.LOG.warn(String.format(
              "Can't find registered intellij library for gradle library '%s'. Registered mappings: %s", gradleLibrary, libraryMappings
            ));
            return;
          }
          LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(intellijLibrary);
          orderEntry.setExported(dependency.isExported());
          orderEntry.setScope(dependency.getScope());
        }
      };
      for (GradleDependency dependency : gradleModule.getDependencies()) {
        dependency.invite(visitor);
      }
    }
    return result;
  }

  private static void registerPath(@NotNull GradleLibrary gradleLibrary, @NotNull Library.ModifiableModel model) {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      for (String path : gradleLibrary.getPaths(pathType)) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
        if (virtualFile == null) {
          GradleLog.LOG.warn(String.format("Can't find %s of the library '%s' at path '%s'", pathType, gradleLibrary.getName(), path));
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            GradleLog.LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", path, gradleLibrary.getName()
            ));
            continue;
          }
          model.addRoot(jarRoot, LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
      }
    }
  }
  
  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
  
  private class ImportModulesTask implements Runnable {
    
    private final Project myProject;
    private final Iterable<GradleModule> myModules;
    private final String myGradleProjectPath;

    ImportModulesTask(@NotNull Project project, @NotNull Iterable<GradleModule> modules, @NotNull String gradleProjectPath) {
      myProject = project;
      myModules = modules;
      myGradleProjectPath = gradleProjectPath;
    }

    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      if (!myProject.isInitialized()) {
        myAlarm.addRequest(
          new ImportModulesTask(myProject, myModules, myGradleProjectPath),
          PROJECT_INITIALISATION_DELAY_MS
        );
        return;
      } 
      
      final ModifiableModuleModel model = new ReadAction<ModifiableModuleModel>() {
        protected void run(Result<ModifiableModuleModel> result) throws Throwable {
          result.setResult(ModuleManager.getInstance(myProject).getModifiableModel());
        }
      }.execute().getResultObject();
      
      importModules(myModules, model, myProject, myGradleProjectPath);
    }
  }
  
  private static class SetupExternalLibrariesTask implements Runnable {

    private final Map<GradleModule, Module>         myModules;
    private final String                            myGradleProjectPath;
    private final Project                           myIntellijProject;

    SetupExternalLibrariesTask(@NotNull Map<GradleModule, Module> modules,
                               @NotNull String gradleProjectPath,
                               @NotNull Project intellijProject)
    {
      myModules = modules;
      myGradleProjectPath = gradleProjectPath;
      myIntellijProject = intellijProject;
    }

    @Override
    public void run() {
      setupLibraries(myModules, myIntellijProject, myGradleProjectPath); 
    }
  }
}
