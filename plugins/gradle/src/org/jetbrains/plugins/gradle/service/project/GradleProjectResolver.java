package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.DataHolder;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  @Nullable
  @Override
  public DataHolder<ProjectData> resolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                            @NotNull final String projectPath,
                                            final boolean downloadLibraries,
                                            @Nullable final GradleExecutionSettings settings)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException
  {
    return myHelper.execute(projectPath, settings, new Function<ProjectConnection, DataHolder<ProjectData>>() {
      @Override
      public DataHolder<ProjectData> fun(ProjectConnection connection) {
        return doResolveProjectInfo(id, projectPath, settings, connection, downloadLibraries);
      }
    });
  }

  @NotNull
  private DataHolder<ProjectData> doResolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                                           @NotNull String projectPath,
                                                           @Nullable GradleExecutionSettings settings,
                                                           @NotNull ProjectConnection connection,
                                                           boolean downloadLibraries)
    throws IllegalArgumentException, IllegalStateException
  {
    
    ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(id, settings, connection, downloadLibraries);
    IdeaProject project = modelBuilder.get();
    DataHolder<ProjectData> result = populateProject(project, projectPath);

    // We need two different steps ('create' and 'populate') in order to handle module dependencies, i.e. when one module is
    // configured to be dependency for another one, corresponding dependency module object should be available during
    // populating dependent module object.
    Map<String, Pair<DataHolder<ModuleData>, IdeaModule>> modules = createModules(project, result);
    populateModules(modules.values(), result);
    Collection<DataHolder<LibraryData>> libraries = result.getCompositeNestedData(ExternalSystemProjectKeys.LIBRARY);
    if (libraries != null) {
      myLibraryNamesMixer.mixNames(libraries);
    }
    return result;
  }

  @NotNull
  private static DataHolder<ProjectData> populateProject(@NotNull IdeaProject project, @NotNull String projectPath) {
    String projectDirPath = ExternalSystemUtil.toCanonicalPath(PathUtil.getParentPath(projectPath));
    
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath);
    projectData.setName(project.getName());

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/out");
    javaProjectData.setJdkVersion(project.getJdkName());
    javaProjectData.setLanguageLevel(project.getLanguageLevel().getLevel());
    
    DataHolder<ProjectData> result = new DataHolder<ProjectData>(ExternalSystemProjectKeys.PROJECT, projectData, null);
    result.register(ExternalSystemProjectKeys.JAVA_PROJECT, result.createSingleChild(ExternalSystemProjectKeys.JAVA_PROJECT,
                                                                                     javaProjectData));
    return result;
  }

  @NotNull
  private static Map<String, Pair<DataHolder<ModuleData>, IdeaModule>> createModules(
    @NotNull IdeaProject gradleProject,
    @NotNull DataHolder<ProjectData> ideProject) throws IllegalStateException
  {
    
    DomainObjectSet<? extends IdeaModule> gradleModules = gradleProject.getModules();
    if (gradleModules == null || gradleModules.isEmpty()) {
      throw new IllegalStateException("No modules found for the target project: " + gradleProject);
    }
    Map<String, Pair<DataHolder<ModuleData>, IdeaModule>> result = ContainerUtilRt.newHashMap();
    for (IdeaModule gradleModule : gradleModules) {
      if (gradleModule == null) {
        continue;
      }
      String moduleName = gradleModule.getName();
      if (moduleName == null) {
        throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
      }
      ProjectData projectData = ideProject.getData();
      ModuleData ideModule = new ModuleData(GradleConstants.SYSTEM_ID, moduleName, projectData.getProjectFileDirectoryPath());
      Pair<DataHolder<ModuleData>, IdeaModule> previouslyParsedModule = result.get(moduleName);
      if (previouslyParsedModule != null) {
        throw new IllegalStateException(
          String.format("Modules with duplicate name (%s) detected: '%s' and '%s'", moduleName, ideModule, previouslyParsedModule)
        );
      }
      DataHolder<ModuleData> moduleDataHolder = ideProject.createChildAtSet(ExternalSystemProjectKeys.MODULE, ideModule);
      result.put(moduleName, new Pair<DataHolder<ModuleData>, IdeaModule>(moduleDataHolder, gradleModule));
    }
    return result;
  }

  private static void populateModules(@NotNull Iterable<Pair<DataHolder<ModuleData>,IdeaModule>> modules, 
                                      @NotNull DataHolder<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException
  {
    for (Pair<DataHolder<ModuleData>, IdeaModule> pair : modules) {
      populateModule(pair.second, pair.first, ideProject);
    }
  }

  private static void populateModule(@NotNull IdeaModule gradleModule,
                                     @NotNull DataHolder<ModuleData> ideModule,
                                     @NotNull DataHolder<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException
  {
    populateContentRoots(gradleModule, ideModule);
    populateCompileOutputSettings(gradleModule.getCompilerOutput(), ideModule);
    populateDependencies(gradleModule, ideModule, ideProject);
  }

  /**
   * Populates {@link ExternalSystemProjectKeys#CONTENT_ROOT) content roots} of the given ide module on the basis of the information
   * contained at the given gradle module.
   * 
   * @param gradleModule    holder of the module information received from the gradle tooling api
   * @param ideModule       corresponding module from intellij gradle plugin domain
   * @throws IllegalArgumentException   if given gradle module contains invalid data
   */
  private static void populateContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataHolder<ModuleData> ideModule)
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
      ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.getAbsolutePath());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());
      Set<File> excluded = gradleContentRoot.getExcludeDirectories();
      if (excluded != null) {
        for (File file : excluded) {
          ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getAbsolutePath());
        }
      }
      ideModule.createChildAtSet(ExternalSystemProjectKeys.CONTENT_ROOT, ideContentRoot);
    }
  }

  /**
   * Stores information about given directories at the given content root 
   * 
   * @param contentRoot  target paths info holder
   * @param type         type of data located at the given directories
   * @param dirs         directories which paths should be stored at the given content root
   * @throws IllegalArgumentException   if specified by {@link ContentRootData#storePath(ExternalSystemSourceType, String)} 
   */
  private static void populateContentRoot(@NotNull ContentRootData contentRoot,
                                          @NotNull ExternalSystemSourceType type,
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
                                                    @NotNull DataHolder<ModuleData> ideModule)
  {
    if (gradleSettings == null) {
      return;
    }

    File sourceCompileOutputPath = gradleSettings.getOutputDir();
    ModuleData moduleData = ideModule.getData();
    if (sourceCompileOutputPath != null) {
      moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, sourceCompileOutputPath.getAbsolutePath());
    }

    File testCompileOutputPath = gradleSettings.getTestOutputDir();
    if (testCompileOutputPath != null) {
      moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, testCompileOutputPath.getAbsolutePath());
    }
    moduleData.setInheritProjectCompileOutputPath(
      gradleSettings.getInheritOutputDirs() || sourceCompileOutputPath == null || testCompileOutputPath == null
    );
  }

  private static void populateDependencies(@NotNull IdeaModule gradleModule,
                                           @NotNull DataHolder<ModuleData> ideModule, 
                                           @NotNull DataHolder<ProjectData> ideProject)
  {
    DomainObjectSet<? extends IdeaDependency> dependencies = gradleModule.getDependencies();
    if (dependencies == null) {
      return;
    }
    for (IdeaDependency dependency : dependencies) {
      if (dependency == null) {
        continue;
      }
      DependencyScope scope = parseScope(dependency.getScope());
      
      if (dependency instanceof IdeaModuleDependency) {
        ModuleDependencyData d = buildDependency(ideModule, (IdeaModuleDependency)dependency, ideProject);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        ideModule.createChildAtSet(ExternalSystemProjectKeys.MODULE_DEPENDENCY, d);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        LibraryDependencyData d = buildDependency(ideModule, (IdeaSingleEntryLibraryDependency)dependency, ideProject);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        ideModule.createChildAtSet(ExternalSystemProjectKeys.LIBRARY_DEPENDENCY, d);
      }
    }
  }

  @NotNull
  private static ModuleDependencyData buildDependency(@NotNull DataHolder<ModuleData> ownerModule,
                                                          @NotNull IdeaModuleDependency dependency,
                                                          @NotNull DataHolder<ProjectData> ideProject)
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

    Set<String> registeredModuleNames = ContainerUtilRt.newHashSet();
    Collection<DataHolder<ModuleData>> modulesDataHolder = ideProject.getCompositeNestedData(ExternalSystemProjectKeys.MODULE);
    if (modulesDataHolder != null) {
      for (DataHolder<ModuleData> moduleDataHolder : modulesDataHolder) {
        String name = moduleDataHolder.getData().getName();
        registeredModuleNames.add(name);
        if (name.equals(moduleName)) {
          return new ModuleDependencyData(ownerModule.getData(), moduleDataHolder.getData());
        }
      }
    }
    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModuleNames
    ));
  }

  @NotNull
  private static LibraryDependencyData buildDependency(@NotNull DataHolder<ModuleData> ownerModule,
                                                           @NotNull IdeaSingleEntryLibraryDependency dependency,
                                                           @NotNull DataHolder<ProjectData> ideProject)
    throws IllegalStateException
  {
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      throw new IllegalStateException(String.format(
        "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the binaries", dependency
      ));
    }

    // Gradle API doesn't provide library name at the moment.
    LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, FileUtil.getNameWithoutExtension(binaryPath));
    library.addPath(LibraryPathType.BINARY, binaryPath.getAbsolutePath());

    File sourcePath = dependency.getSource();
    if (sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
    }

    File javadocPath = dependency.getJavadoc();
    if (javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
    }

    Collection<DataHolder<LibraryData>> libraryHolders = ideProject.getCompositeNestedData(ExternalSystemProjectKeys.LIBRARY);
    if (libraryHolders != null) {
      for (DataHolder<LibraryData> holder : libraryHolders) {
        if (library.equals(holder.getData())) {
          return new LibraryDependencyData(ownerModule.getData(), holder.getData());
        }
      }
    }
    
    ideProject.createChildAtSet(ExternalSystemProjectKeys.LIBRARY, library);
    return new LibraryDependencyData(ownerModule.getData(), library);
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
}
