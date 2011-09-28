package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.execution.rmi.RemoteObject;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.model.*;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolverImpl extends RemoteObject implements GradleProjectResolver, RemoteGradleService {
  
  private static final Set<String> NON_UNIQUE_PATH_ENTRIES = new HashSet<String>(Arrays.asList(
    "src", "main", "java", "test", "resources" 
  ));
  
  private final BlockingQueue<ProjectConnection>             myConnections = new LinkedBlockingQueue<ProjectConnection>();
  private final AtomicReference<RemoteGradleProcessSettings> mySettings    = new AtomicReference<RemoteGradleProcessSettings>();
  
  @NotNull
  @Override
  public GradleProject resolveProjectInfo(@NotNull String projectPath, boolean downloadLibraries)
    throws RemoteException, IllegalArgumentException, IllegalStateException
  {
    ProjectConnection connection = getConnection(projectPath);
    IdeaProject project = connection.getModel(downloadLibraries ? IdeaProject.class : BasicIdeaProject.class);
    GradleProject result = populateProject(project, projectPath);

    // We need two different steps ('create' and 'populate') in order to handle module dependencies, i.e. when one module is
    // configured to be dependency for another one, corresponding dependency module object should be available during
    // populating dependent module object.
    Map<String, Pair<GradleModule, IdeaModule>> modules = createModules(project, result);
    populateModules(modules.values(), result);
    diversifyLibraryNamesIfNecessary(result);
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
      GradleContentRoot intellijContentRoot = new GradleContentRoot(rootDirectory.getAbsolutePath());
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
    intellijModule.setInheritProjectCompileOutputPath(gradleSettings.getInheritOutputDirs());

    File sourceCompileOutputPath = gradleSettings.getOutputDir();
    if (sourceCompileOutputPath != null) {
      intellijModule.setCompileOutputPath(SourceType.SOURCE, sourceCompileOutputPath.getAbsolutePath());
    }

    File testCompileOutputPath = gradleSettings.getTestOutputDir();
    if (testCompileOutputPath != null) {
      intellijModule.setCompileOutputPath(SourceType.TEST, testCompileOutputPath.getAbsolutePath());
    }
  }

  private static void populateDependencies(@NotNull IdeaModule gradleModule, @NotNull GradleModule intellijModule, 
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
        intellijDependency = buildDependency((IdeaModuleDependency)dependency, intellijProject);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        intellijDependency = buildDependency((IdeaSingleEntryLibraryDependency)dependency, intellijProject);
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
  private static AbstractGradleDependency buildDependency(@NotNull IdeaModuleDependency dependency, @NotNull GradleProject intellijProject)
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
        return new GradleModuleDependency(gradleModule);
      }
    }
    throw new IllegalStateException(String.format(
      "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. Registered modules: %s",
      dependency, moduleName, registeredModuleNames
    ));
  }

  @NotNull
  private static AbstractGradleDependency buildDependency(@NotNull IdeaSingleEntryLibraryDependency dependency, 
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
      library.addPath(LibraryPathType.JAVADOC, javadocPath.getAbsolutePath());
    }

    if (!intellijProject.addLibrary(library)) {
      for (GradleLibrary registeredLibrary : intellijProject.getLibraries()) {
        if (registeredLibrary.equals(library)) {
          return new GradleLibraryDependency(registeredLibrary);
        }
      }
    }
    
    return new GradleLibraryDependency(library);
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

  /**
   * Gradle API doesn't provide library names at the moment, so, we deduce them from the path. However, it's possible to have identical
   * names then.
   * <p/>
   * This method solves that by diversifying duplicate library names.
   * 
   * @param project  target project which library names should be diversified if necessary
   */
  private static void diversifyLibraryNamesIfNecessary(@NotNull GradleProject project) {
    Map<String, GradleLibrary> libraries = new HashMap<String, GradleLibrary>();
    for (GradleLibrary library : project.getLibraries()) {
      GradleLibrary previous = libraries.remove(library.getName());
      if (previous == null) {
        libraries.put(library.getName(), library);
        continue;
      }
      previous.setName(generateName(previous));
      libraries.put(previous.getName(), previous);
      library.setName(generateName(library));
      libraries.put(library.getName(), library);
    }
  }
  
  @NotNull
  private static String generateName(@NotNull GradleLibrary library) {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      String path = library.getPath(pathType);
      if (path == null) {
        continue;
      }
      File file = new File(path).getParentFile();
      for (; file != null; file = file.getParentFile()) {
        if (!NON_UNIQUE_PATH_ENTRIES.contains(file.getName())) {
          return file.getName() + "-" + library.getName();
        }
      }
    }
    return library.getName();
  }
  
  /**
   * Allows to retrieve gradle api connection to use for the given project.
   * 
   * @param projectPath     target project path
   * @return                connection to use
   * @throws IllegalStateException    if it's not possible to create the connection
   */
  @NotNull
  private ProjectConnection getConnection(@NotNull String projectPath) throws IllegalStateException {
    File projectFile = new File(projectPath);
    if (!projectFile.isFile()) {
      throw new IllegalArgumentException(GradleBundle.message("gradle.import.text.error.invalid.path", projectPath));
    }
    File projectDir = projectFile.getParentFile();
    GradleConnector connector = GradleConnector.newConnector();
    RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {
      connector.useInstallation(new File(settings.getGradleHome()));
    } 
    connector.forProjectDirectory(projectDir);
    ProjectConnection connection = connector.connect();
    if (connection == null) {
      throw new IllegalStateException(String.format(
        "Can't create connection to the target project via gradle tooling api. Project path: '%s'", projectPath
      ));
    }
    myConnections.add(connection);
    return connection;
  }
  
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void unreferenced() {
    releaseConnectionIfPossible();
    super.unreferenced();
  }

  private void releaseConnectionIfPossible() {
    while (!myConnections.isEmpty()) {
      try {
        ProjectConnection connection = myConnections.poll(1, TimeUnit.SECONDS);
        connection.close();
      }
      catch (InterruptedException e) {
        GradleLog.LOG.warn("Detected unexpected thread interruption on releasing gradle connections", e);
        Thread.currentThread().interrupt();
      }
      catch (Throwable e) {
        GradleLog.LOG.warn("Got unexpected exception on closing project connection created by the gradle tooling api", e);
      }
    }
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) {
    mySettings.set(settings); 
  }
}
