package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.application.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.model.*;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates functionality of creating IntelliJ IDEA modules on the basis of {@link GradleModule gradle modules}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/26/11 10:01 AM
 */
public class GradleModulesImporter {
  
  /**
   * We can't modify project modules (add/remove) until it's initialised, so, we delay that activity. Current constant
   * holds number of milliseconds to wait between 'after project initialisation' processing attempts.
   */
  private static final int PROJECT_INITIALISATION_DELAY_MS = (int)TimeUnit.SECONDS.toMillis(1);
  
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  /**
   * Entry point for the whole 'import modules' procedure.
   * 
   * @param modules  module info containers received from the gradle api
   * @param project  project that should host the modules
   * @param model    modules model
   * @return         mappings between the given gradle modules and newly created intellij modules
   */
  @NotNull
  public Map<GradleModule, Module> importModules(@NotNull final Iterable<GradleModule> modules, @Nullable final Project project, 
                                                 @Nullable final ModifiableModuleModel model)
  {
    if (project == null) {
      return Collections.emptyMap();
    }
    removeExistingModulesSettings(modules);
    if (!project.isInitialized()) {
      myAlarm.addRequest(new ImportModulesTask(project, modules), PROJECT_INITIALISATION_DELAY_MS);
      return Collections.emptyMap();
    } 
    if (model == null) {
      return Collections.emptyMap();
    }
    return importModules(modules, model);
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
                                                 @NotNull final ModifiableModuleModel model)
  {
    final Map<GradleModule, Module> result = new HashMap<GradleModule, Module>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        Application application = ApplicationManager.getApplication();
        AccessToken writeLock = application.acquireWriteActionLock(getClass());
        try {
          try {
            result.putAll(doImportModules(modules, model));
          }
          finally {
            model.commit();
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
   * Actual implementation of {@link #importModules(Iterable, Project, ModifiableModuleModel)}. Insists on all arguments to
   * be ready to use.
   *  
   * @param modules  modules to import
   * @param model    modules model
   * @return         mappings between the given gradle modules and corresponding intellij modules
   */
  @NotNull
  @SuppressWarnings("MethodMayBeStatic")
  private Map<GradleModule, Module> doImportModules(@NotNull Iterable<GradleModule> modules, @NotNull ModifiableModuleModel model) {
    Map<GradleModule, Module> result = new HashMap<GradleModule, Module>();
    for (GradleModule moduleToImport : modules) {
      Module createdModule = createModule(moduleToImport, model);
      result.put(moduleToImport, createdModule);
    }
    for (GradleModule moduleToImport : modules) {
      configureModule(moduleToImport, result);
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
    return model.newModule(moduleFilePath, StdModuleTypes.JAVA);
  }

  /**
   * Applies module settings received from the gradle api (encapsulate at the given {@link GradleModule} object) to the
   * target intellij module (retrieved from the given module mappings).
   * 
   * @param module   target gradle module which corresponding intellij module should be configured
   * @param modules  gradle module to intellij modules mappings. Is assumed to have a value for the given gradle modules used as a key
   */
  private static void configureModule(@NotNull GradleModule module, @NotNull Map<GradleModule, Module> modules) {
    Application application = ApplicationManager.getApplication();
    application.assertWriteAccessAllowed();
    
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(modules.get(module));
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      configureModule(module, rootModel, modules);
    }
    finally {
      rootModel.commit();
    }
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
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      model.removeOrderEntry(orderEntry);
    }
    
    // Configure SDK.
    model.inheritSdk();

    // Compile output.
    CompilerModuleExtension compilerExtension = model.getModuleExtension(CompilerModuleExtension.class);
    compilerExtension.inheritCompilerOutputPath(module.isInheritProjectCompileOutputPath());
    if (!module.isInheritProjectCompileOutputPath()) {
      compilerExtension.setCompilerOutputPath(module.getCompileOutputPath(SourceType.SOURCE));
      compilerExtension.setCompilerOutputPathForTests(module.getCompileOutputPath(SourceType.TEST));
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
          ModuleOrderEntry orderEntry = model.addModuleOrderEntry(modules.get(dependency.getModule()));
          orderEntry.setExported(dependency.isExported());
          orderEntry.setScope(dependency.getScope());
        }
      });
    }
  }

  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
  
  private class ImportModulesTask implements Runnable {
    
    private final Project myProject;
    private final Iterable<GradleModule> myModules;

    private ImportModulesTask(@NotNull Project project, @NotNull Iterable<GradleModule> modules) {
      myProject = project;
      myModules = modules;
    }

    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      if (!myProject.isInitialized()) {
        myAlarm.addRequest(new ImportModulesTask(myProject, myModules), PROJECT_INITIALISATION_DELAY_MS);
        return;
      } 
      
      final ModifiableModuleModel model = new ReadAction<ModifiableModuleModel>() {
        protected void run(Result<ModifiableModuleModel> result) throws Throwable {
          result.setResult(ModuleManager.getInstance(myProject).getModifiableModel());
        }
      }.execute().getResultObject();

      importModules(myModules, model);
    }
  }
}
