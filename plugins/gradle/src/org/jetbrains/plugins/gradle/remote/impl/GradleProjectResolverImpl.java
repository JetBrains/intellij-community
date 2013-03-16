package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.execution.rmi.RemoteObject;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolverImpl extends RemoteObject implements GradleProjectResolver, RemoteGradleService {

  @NotNull private final RemoteGradleServiceHelper myHelper = new RemoteGradleServiceHelper();

  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  @NotNull
  @Override
  public GradleProject resolveProjectInfo(@NotNull final GradleTaskId id, @NotNull final String projectPath, final boolean downloadLibraries)
    throws RemoteException, GradleApiException, IllegalArgumentException, IllegalStateException
  {
    return myHelper.execute(id, GradleTaskType.RESOLVE_PROJECT, projectPath, new Function<ProjectConnection, GradleProject>() {
      @Nullable
      @Override
      public GradleProject fun(ProjectConnection connection) {
        return doResolveProjectInfo(id, projectPath, connection, downloadLibraries);
      }
    });
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    return myHelper.isTaskInProgress(id);
  }

  @NotNull
  @Override
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return myHelper.getTasksInProgress();
  }

  @NotNull
  private GradleProject doResolveProjectInfo(@NotNull final GradleTaskId id,
                                             @NotNull String projectPath,
                                             @NotNull ProjectConnection connection,
                                             boolean downloadLibraries)
    throws IllegalArgumentException, IllegalStateException
  {
    ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(id, connection, downloadLibraries);
    IdeaProject project = modelBuilder.get();
    GradleProject result = populateProject(project, projectPath);

    // We need two different steps ('create' and 'populate') in order to handle module dependencies, i.e. when one module is
    // configured to be dependency for another one, corresponding dependency module object should be available during
    // populating dependent module object.
    Map<String, Pair<GradleModule, IdeaModule>> modules = createModules(project, result);
    populateModules(modules.values(), result);
    myLibraryNamesMixer.mixNames(result.getLibraries());
    return result;
  }

  private static GradleProject populateProject(@NotNull IdeaProject project, @NotNull String projectPath) {
    String projectDirPath = GradleUtil.toCanonicalPath(PathUtil.getParentPath(projectPath));
    // Gradle API doesn't expose project compile output path yet.
    GradleProject result = new GradleProject(projectDirPath, projectDirPath + "/out");
    result.setName(project.getName());
    result.setJdkVersion(project.getJdkName());
    result.setLanguageLevel(project.getLanguageLevel().getLevel());
    return result;
  }

  @NotNull
  private static Map<String, Pair<GradleModule, IdeaModule>> createModules(@NotNull IdeaProject gradleProject,
                                                                           @NotNull GradleProject intellijProject)
    throws IllegalStateException
  {
    DomainObjectSet<? extends IdeaModule> gradleModules = gradleProject.getModules();
    if (gradleModules == null || gradleModules.isEmpty()) {
      throw new IllegalStateException("No modules found for the target project: " + gradleProject);
    }
    Map<String, Pair<GradleModule, IdeaModule>> result = new HashMap<String, Pair<GradleModule, IdeaModule>>();
    for (IdeaModule gradleModule : gradleModules) {
      if (gradleModule == null) {
        continue;
      }
      String moduleName = gradleModule.getName();
      if (moduleName == null) {
        throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
      }
      GradleModule intellijModule = new GradleModule(moduleName, intellijProject.getProjectFileDirectoryPath());
      Pair<GradleModule, IdeaModule> previouslyParsedModule = result.get(moduleName);
      if (previouslyParsedModule != null) {
        throw new IllegalStateException(
          String.format("Modules with duplicate name (%s) detected: '%s' and '%s'", moduleName, intellijModule, previouslyParsedModule)
        );
      }
      result.put(moduleName, new Pair<GradleModule, IdeaModule>(intellijModule, gradleModule));
      intellijProject.addModule(intellijModule);
    }
    return result;
  }

  private static void populateModules(@NotNull Iterable<Pair<GradleModule,IdeaModule>> modules, 
                                      @NotNull GradleProject intellijProject)
    throws IllegalArgumentException, IllegalStateException
  {
    for (Pair<GradleModule, IdeaModule> pair : modules) {
      populateModule(pair.second, pair.first, intellijProject);
    }
  }

  private static void populateModule(@NotNull IdeaModule gradleModule,
                                     @NotNull GradleModule intellijModule,
                                     @NotNull GradleProject intellijProject)
    throws IllegalArgumentException, IllegalStateException
  {
    populateContentRoots(gradleModule, intellijModule);
    populateCompileOutputSettings(gradleModule.getCompilerOutput(), intellijModule);
    populateDependencies(gradleModule, intellijModule, intellijProject);
  }

  /**
   * Populates {@link GradleModule#getContentRoots() content roots} of the given intellij module on the basis of the information
   * contained at the given gradle module.
   * 
   * @param gradleModule    holder of the module information received from the gradle tooling api
   * @param intellijModule  corresponding module from intellij gradle plugin domain
   * @throws IllegalArgumentException   if given gradle module contains invalid data
   */
  private static void populateContentRoots(@NotNull IdeaModule gradleModule, @NotNull GradleModule intellijModule)
    throws IllegalArgumentException
  {
    DomainObjectSet<? extends IdeaContentRoot> contentRoots = gradleModule.getContentRoots();
    if (contentRoots == null) {
      return;
    }
    for (IdeaContentRoot gradleContentRoot : contentRoots) {
      if (gradleContentRoot == null) {
        continue;
      }
      File rootDirectory = gradleContentRoot.getRootDirectory();
      if (rootDirectory == null) {
        continue;
      }
      GradleContentRoot intellijContentRoot = new GradleContentRoot(intellijModule, rootDirectory.getAbsolutePath());
      populateContentRoot(intellijContentRoot, SourceType.SOURCE, gradleContentRoot.getSourceDirectories());
      populateContentRoot(intellijContentRoot, SourceType.TEST, gradleContentRoot.getTestDirectories());
      Set<File> excluded = gradleContentRoot.getExcludeDirectories();
      if (excluded != null) {
        for (File file : excluded) {
          intellijContentRoot.storePath(SourceType.EXCLUDED, file.getAbsolutePath());
        }
      }
      intellijModule.addContentRoot(intellijContentRoot);
    }
  }

  /**
   * Stores information about given directories at the given content root 
   * 
   * @param contentRoot  target paths info holder
   * @param type         type of data located at the given directories
   * @param dirs         directories which paths should be stored at the given content root
   * @throws IllegalArgumentException   if specified by {@link GradleContentRoot#storePath(SourceType, String)} 
   */
  private static void populateContentRoot(@NotNull GradleContentRoot contentRoot,
                                          @NotNull SourceType type,
                                          @Nullable Iterable<? extends IdeaSourceDirectory> dirs)
    throws IllegalArgumentException
  {
    if (dirs == null) {
      return;
    }
    for (IdeaSourceDirectory dir : dirs) {
      contentRoot.storePath(type, dir.getDirectory().getAbsolutePath());
    }
  }
  
  private static void populateCompileOutputSettings(@Nullable IdeaCompilerOutput gradleSettings,
                                                    @NotNull GradleModule intellijModule)
  {
    if (gradleSettings == null) {
      return;
    }

    File sourceCompileOutputPath = gradleSettings.getOutputDir();
    if (sourceCompileOutputPath != null) {
      intellijModule.setCompileOutputPath(SourceType.SOURCE, sourceCompileOutputPath.getAbsolutePath());
    }

    File testCompileOutputPath = gradleSettings.getTestOutputDir();
    if (testCompileOutputPath != null) {
      intellijModule.setCompileOutputPath(SourceType.TEST, testCompileOutputPath.getAbsolutePath());
    }
    intellijModule.setInheritProjectCompileOutputPath(
      gradleSettings.getInheritOutputDirs() || sourceCompileOutputPath == null || testCompileOutputPath == null
    );
  }

  private static void populateDependencies(@NotNull IdeaModule gradleModule,
                                           @NotNull GradleModule intellijModule, 
                                           @NotNull GradleProject intellijProject)
  {
    DomainObjectSet<? extends IdeaDependency> dependencies = gradleModule.getDependencies();
    if (dependencies == null) {
      return;
    }
    for (IdeaDependency dependency : dependencies) {
      if (dependency == null) {
        continue;
      }
      AbstractGradleDependency intellijDependency = null;
      if (dependency instanceof IdeaModuleDependency) {
        intellijDependency = buildDependency(intellijModule, (IdeaModuleDependency)dependency, intellijProject);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        intellijDependency = buildDependency(intellijModule, (IdeaSingleEntryLibraryDependency)dependency, intellijProject);
      }

      if (intellijDependency == null) {
        continue;
      }
      
      intellijDependency.setExported(dependency.getExported());
      DependencyScope scope = parseScope(dependency.getScope());
      if (scope != null) {
        intellijDependency.setScope(scope);
      }
      intellijModule.addDependency(intellijDependency);
    }
  }

  @NotNull
  private static AbstractGradleDependency buildDependency(@NotNull GradleModule ownerModule,
                                                          @NotNull IdeaModuleDependency dependency,
                                                          @NotNull GradleProject intellijProject)
    throws IllegalStateException
  {
    IdeaModule module = dependency.getDependencyModule();
    if (module == null) {
      throw new IllegalStateException(
        String.format("Can't parse gradle module dependency '%s'. Reason: referenced module is null", dependency)
      );
    }

    String moduleName = module.getName();
    if (moduleName == null) {
      throw new IllegalStateException(String.format(
        "Can't parse gradle module dependency '%s'. Reason: referenced module name is undefined (module: '%s') ", dependency, module
      ));
    }
    
    Set<String> registeredModuleNames = new HashSet<String>();
    for (GradleModule gradleModule : intellijProject.getModules()) {
      registeredModuleNames.add(gradleModule.getName());
      if (gradleModule.getName().equals(moduleName)) {
        return new GradleModuleDependency(ownerModule, gradleModule);
      }
    }
    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModuleNames
    ));
  }

  @NotNull
  private static AbstractGradleDependency buildDependency(@NotNull GradleModule ownerModule,
                                                          @NotNull IdeaSingleEntryLibraryDependency dependency, 
                                                          @NotNull GradleProject intellijProject)
    throws IllegalStateException
  {
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      throw new IllegalStateException(String.format(
        "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the binaries", dependency
      ));
    }
    
    // Gradle API doesn't provide library name at the moment.
    GradleLibrary library = new GradleLibrary(FileUtil.getNameWithoutExtension(binaryPath));
    library.addPath(LibraryPathType.BINARY, binaryPath.getAbsolutePath());

    File sourcePath = dependency.getSource();
    if (sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
    }

    File javadocPath = dependency.getJavadoc();
    if (javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
    }

    if (!intellijProject.addLibrary(library)) {
      for (GradleLibrary registeredLibrary : intellijProject.getLibraries()) {
        if (registeredLibrary.equals(library)) {
          return new GradleLibraryDependency(ownerModule, registeredLibrary);
        }
      }
    }
    
    return new GradleLibraryDependency(ownerModule, library);
  }

  @Nullable
  private static DependencyScope parseScope(@Nullable IdeaDependencyScope scope) {
    if (scope == null) {
      return null;
    }
    String scopeAsString = scope.getScope();
    if (scopeAsString == null) {
      return null;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scopeAsString.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return null;
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    myHelper.setSettings(settings); 
  }

  @Override
  public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    myHelper.setNotificationListener(notificationListener);
  }
}
