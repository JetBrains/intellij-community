package org.jetbrains.plugins.gradle.service.project;

import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {

  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

  @Nullable private Pair<List<String>, List<GradleProjectResolverExtension>> myCachedExtensions;

  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                                  @NotNull final String projectPath,
                                                  final boolean downloadLibraries,
                                                  @Nullable final GradleExecutionSettings settings,
                                                  @NotNull final ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException
  {
    if (settings != null) {
      List<String> extensionClassNames = settings.getResolverExtensions();
      if (myCachedExtensions == null || !myCachedExtensions.first.equals(extensionClassNames)) {
        List<String> classNames = ContainerUtilRt.newArrayList(extensionClassNames);
        List<GradleProjectResolverExtension> extensions = ContainerUtilRt.newArrayList();
        for (String className : classNames) {
          try {
            extensions.add((GradleProjectResolverExtension)Class.forName(className).newInstance());
          }
          catch (Exception e) {
            throw new IllegalArgumentException(String.format("Can't instantiate project resolve extension for class '%s'", className), e);
          }
        }
        myCachedExtensions = Pair.create(classNames, extensions);
      }
      for (GradleProjectResolverExtension extension : myCachedExtensions.second) {
        DataNode<ProjectData> result = extension.resolveProjectInfo(id, projectPath, downloadLibraries, settings, listener);
        if (result != null) {
          return result;
        }
      }
    }

    return myHelper.execute(projectPath, settings, new Function<ProjectConnection, DataNode<ProjectData>>() {
      @Override
      public DataNode<ProjectData> fun(ProjectConnection connection) {
        return doResolveProjectInfo(id, projectPath, settings, connection, listener, downloadLibraries);
      }
    });
  }

  @NotNull
  private DataNode<ProjectData> doResolveProjectInfo(@NotNull final ExternalSystemTaskId id,
                                                     @NotNull String projectPath,
                                                     @Nullable GradleExecutionSettings settings,
                                                     @NotNull ProjectConnection connection,
                                                     @NotNull ExternalSystemTaskNotificationListener listener,
                                                     boolean downloadLibraries)
    throws IllegalArgumentException, IllegalStateException
  {
    ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(id, settings, connection, listener, downloadLibraries);
    IdeaProject project = modelBuilder.get();
    DataNode<ProjectData> result = populateProject(project, projectPath);

    // We need two different steps ('create' and 'populate') in order to handle module dependencies, i.e. when one module is
    // configured to be dependency for another one, corresponding dependency module object should be available during
    // populating dependent module object.
    Map<String, Pair<DataNode<ModuleData>, IdeaModule>> modules = createModules(project, result);
    populateModules(modules.values(), result);
    Collection<DataNode<LibraryData>> libraries = ExternalSystemApiUtil.getChildren(result, ProjectKeys.LIBRARY);
    myLibraryNamesMixer.mixNames(libraries);
    parseTasks(result, project);
    return result;
  }

  @NotNull
  private static DataNode<ProjectData> populateProject(@NotNull IdeaProject project, @NotNull String projectPath) {
    String projectDirPath = ExternalSystemApiUtil.toCanonicalPath(projectPath);

    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectPath);
    projectData.setName(project.getName());

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/out");
    javaProjectData.setJdkVersion(project.getJdkName());
    javaProjectData.setLanguageLevel(project.getLanguageLevel().getLevel());

    DataNode<ProjectData> result = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);
    result.createChild(JavaProjectData.KEY, javaProjectData);
    return result;
  }

  @NotNull
  private static Map<String, Pair<DataNode<ModuleData>, IdeaModule>> createModules(
    @NotNull IdeaProject gradleProject,
    @NotNull DataNode<ProjectData> ideProject) throws IllegalStateException
  {

    DomainObjectSet<? extends IdeaModule> gradleModules = gradleProject.getModules();
    if (gradleModules == null || gradleModules.isEmpty()) {
      throw new IllegalStateException("No modules found for the target project: " + gradleProject);
    }
    Map<String, Pair<DataNode<ModuleData>, IdeaModule>> result = ContainerUtilRt.newHashMap();
    for (IdeaModule gradleModule : gradleModules) {
      if (gradleModule == null) {
        continue;
      }
      String moduleName = gradleModule.getName();
      if (moduleName == null) {
        throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
      }
      ProjectData projectData = ideProject.getData();
      String moduleConfigPath
        = GradleUtil.getConfigPath(gradleModule.getGradleProject(), ideProject.getData().getLinkedExternalProjectPath());
      ModuleData ideModule = new ModuleData(GradleConstants.SYSTEM_ID,
                                            StdModuleTypes.JAVA.getId(),
                                            moduleName,
                                            projectData.getIdeProjectFileDirectoryPath(),
                                            moduleConfigPath);
      Pair<DataNode<ModuleData>, IdeaModule> previouslyParsedModule = result.get(moduleName);
      if (previouslyParsedModule != null) {
        throw new IllegalStateException(
          String.format("Modules with duplicate name (%s) detected: '%s' and '%s'", moduleName, ideModule, previouslyParsedModule)
        );
      }
      DataNode<ModuleData> moduleDataNode = ideProject.createChild(ProjectKeys.MODULE, ideModule);
      result.put(moduleName, new Pair<DataNode<ModuleData>, IdeaModule>(moduleDataNode, gradleModule));
    }
    return result;
  }

  private static void populateModules(@NotNull Iterable<Pair<DataNode<ModuleData>,IdeaModule>> modules,
                                      @NotNull DataNode<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException
  {
    for (Pair<DataNode<ModuleData>, IdeaModule> pair : modules) {
      populateModule(pair.second, pair.first, ideProject);
    }
  }

  private static void populateModule(@NotNull IdeaModule gradleModule,
                                     @NotNull DataNode<ModuleData> ideModule,
                                     @NotNull DataNode<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException
  {
    populateContentRoots(gradleModule, ideModule);
    populateCompileOutputSettings(gradleModule.getCompilerOutput(), ideModule);
    populateDependencies(gradleModule, ideModule, ideProject);
  }

  /**
   * Populates {@link ProjectKeys#CONTENT_ROOT) content roots} of the given ide module on the basis of the information
   * contained at the given gradle module.
   *
   * @param gradleModule    holder of the module information received from the gradle tooling api
   * @param ideModule       corresponding module from intellij gradle plugin domain
   * @throws IllegalArgumentException   if given gradle module contains invalid data
   */
  private static void populateContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule)
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
      ideModule.getData().setModuleFileDirectoryPath(ideContentRoot.getRootPath());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());
      Set<File> excluded = gradleContentRoot.getExcludeDirectories();
      if (excluded != null) {
        for (File file : excluded) {
          ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getAbsolutePath());
        }
      }
      ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
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
                                                    @NotNull DataNode<ModuleData> ideModule)
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
                                           @NotNull DataNode<ModuleData> ideModule,
                                           @NotNull DataNode<ProjectData> ideProject)
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
        ideModule.createChild(ProjectKeys.MODULE_DEPENDENCY, d);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        LibraryDependencyData d = buildDependency(ideModule, (IdeaSingleEntryLibraryDependency)dependency, ideProject);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        ideModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, d);
      }
    }
  }

  @NotNull
  private static ModuleDependencyData buildDependency(@NotNull DataNode<ModuleData> ownerModule,
                                                      @NotNull IdeaModuleDependency dependency,
                                                      @NotNull DataNode<ProjectData> ideProject)
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
    Collection<DataNode<ModuleData>> modulesDataNode = ExternalSystemApiUtil.getChildren(ideProject, ProjectKeys.MODULE);
    for (DataNode<ModuleData> moduleDataNode : modulesDataNode) {
      String name = moduleDataNode.getData().getName();
      registeredModuleNames.add(name);
      if (name.equals(moduleName)) {
        return new ModuleDependencyData(ownerModule.getData(), moduleDataNode.getData());
      }
    }
    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModuleNames
    ));
  }

  @NotNull
  private static LibraryDependencyData buildDependency(@NotNull DataNode<ModuleData> ownerModule,
                                                       @NotNull IdeaSingleEntryLibraryDependency dependency,
                                                       @NotNull DataNode<ProjectData> ideProject)
    throws IllegalStateException
  {
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      throw new IllegalStateException(String.format(
        "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the binaries", dependency
      ));
    }

    // Gradle API doesn't provide library name at the moment.
    String libraryName = FileUtil.getNameWithoutExtension(binaryPath);
    
    // Gradle API doesn't explicitly provide information about unresolved libraries (http://issues.gradle.org/browse/GRADLE-1995).
    // That's why we use this dirty hack here.
    boolean unresolved = libraryName.startsWith(UNRESOLVED_DEPENDENCY_PREFIX);
    if (unresolved) {
      // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
      libraryName = binaryPath.getName().substring(UNRESOLVED_DEPENDENCY_PREFIX.length());
      int i = libraryName.indexOf(' ');
      if (i >= 0) {
        i = CharArrayUtil.shiftForward(libraryName, i + 1, " ");
      }
      
      if (i >= 0 && i < libraryName.length()) {
        int dependencyNameIndex = i;
        i = libraryName.indexOf(' ', dependencyNameIndex);
        if (i > 0) {
          libraryName = String.format("%s-%s", libraryName.substring(dependencyNameIndex, i), libraryName.substring(i + 1));
        }
      }
    }
    
    final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, unresolved);
    if (!unresolved) {
      library.addPath(LibraryPathType.BINARY, binaryPath.getAbsolutePath());
    }

    File sourcePath = dependency.getSource();
    if (!unresolved && sourcePath != null) {
      library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
    }

    File javadocPath = dependency.getJavadoc();
    if (!unresolved && javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
    }

    DataNode<LibraryData> libraryData =
      ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
        @Override
        public boolean fun(DataNode<LibraryData> node) {
          return library.equals(node.getData());
        }
      });
    if (libraryData == null) {
      libraryData = ideProject.createChild(ProjectKeys.LIBRARY, library);
    }

    return new LibraryDependencyData(ownerModule.getData(), libraryData.getData(), LibraryLevel.PROJECT);
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

  private static void parseTasks(@NotNull DataNode<ProjectData> rootProjectNode, @NotNull IdeaProject project) {
    
    // So, the general idea is to fill target nodes by nodes with TaskData. Specifics:
    //   1. Gradle tooling api doesn't explicitly provide information about root project tasks, e.g. when a root project
    //      contains code block like below:
    //        subprojects {
    //          apply plugin: 'java'
    //        }
    //   2. Gradle tooling api provides an IdeaModule object for every IdeaProject among IdeaModule objects which correspond
    //      to real sub-projects;
    //
    // Our aim is to make sub-project nodes contain corresponding TaskData nodes and add root project tasks to ProjectData node.
    // The later is achieved by composing all tasks from IdeaModule which corresponds to the IdeaProject plus all tasks
    // which are shared between all sub-projects.

    ProjectData projectData = rootProjectNode.getData();
    final String rootProjectPath = projectData.getLinkedExternalProjectPath();
    Map<String/* module name */, Collection<TaskData>> tasksByModule = ContainerUtilRt.newHashMap();
    TObjectIntHashMap<Pair<String/* task name */, String /* task description */>> rootProjectTaskCandidates
      = new TObjectIntHashMap<Pair<String, String>>();
    final Collection<TaskData> rootProjectTasks = ContainerUtilRt.newArrayList();
    final DomainObjectSet<? extends IdeaModule> modules = project.getModules();
    for (IdeaModule module : modules) {
      String moduleConfigPath = GradleUtil.getConfigPath(module.getGradleProject(), rootProjectPath);
      for (GradleTask task : module.getGradleProject().getTasks()) {
        String name = task.getName();
        if (name == null || name.trim().isEmpty()) {
          continue;
        }

        String s = name.toLowerCase();
        if (s.contains("idea")) {
          continue;
        }

        TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, name, moduleConfigPath, task.getDescription());
        
        if (rootProjectPath.equals(moduleConfigPath)) {
          rootProjectTasks.add(taskData);
        }
        else {
          Collection<TaskData> tasks = tasksByModule.get(module.getName());
          if (tasks == null) {
            tasksByModule.put(module.getName(), tasks = ContainerUtilRt.newArrayList());
          }
          tasks.add(taskData);
          Pair<String, String> key = Pair.create(name, task.getDescription());
          rootProjectTaskCandidates.put(key, rootProjectTaskCandidates.get(key) + 1);
        }
      }
    }
    rootProjectTaskCandidates.forEachEntry(new TObjectIntProcedure<Pair<String, String>>() {
      @Override
      public boolean execute(Pair<String, String> p, int occurrenceNumber) {
        if (modules.size() == 1 || occurrenceNumber >= modules.size() - 1) {
          rootProjectTasks.add(new TaskData(GradleConstants.SYSTEM_ID, p.first, rootProjectPath, p.second));
        }
        return true;
      }
    });
    for (TaskData task : rootProjectTasks) {
      rootProjectNode.createChild(ProjectKeys.TASK, task);
    }

    Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAll(rootProjectNode, ProjectKeys.MODULE);
    for (DataNode<ModuleData> moduleNode : moduleNodes) {
      ModuleData moduleData = moduleNode.getData();
      if (rootProjectPath.equals(moduleData.getLinkedExternalProjectPath()) && !projectData.getName().equals(moduleData.getName())) {
        moduleData.setName(projectData.getName());
      }
      Collection<TaskData> tasks = tasksByModule.get(moduleData.getName());
      if (tasks != null && !tasks.isEmpty()) {
        for (TaskData task : tasks) {
          moduleNode.createChild(ProjectKeys.TASK, task);
        }
      }
    }
  }
}
