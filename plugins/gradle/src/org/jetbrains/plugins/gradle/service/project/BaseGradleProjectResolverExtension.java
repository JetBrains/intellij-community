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
package org.jetbrains.plugins.gradle.service.project;

import com.google.gson.GsonBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.CharArrayUtil;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.*;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.tooling.builder.ModelBuildScriptClasspathBuilderImpl;
import org.jetbrains.plugins.gradle.tooling.internal.init.Init;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.slf4j.impl.Log4jLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link BaseGradleProjectResolverExtension} provides base implementation of Gradle project resolver.
 *
 * @author Vladislav.Soroka
 * @since 10/14/13
 */
@Order(Integer.MAX_VALUE)
public class BaseGradleProjectResolverExtension implements GradleProjectResolverExtension {
  private static final Logger LOG = Logger.getInstance("#" + BaseGradleProjectResolverExtension.class.getName());

  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";
  private static final String MAIN_SOURCE_SET = "main";
  private static final String TEST_SOURCE_SET = "test";

  @NotNull private ProjectResolverContext resolverCtx;
  @NotNull private final BaseProjectImportErrorHandler myErrorHandler = new BaseProjectImportErrorHandler();

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    resolverCtx = projectResolverContext;
  }

  @Override
  public void setNext(@NotNull GradleProjectResolverExtension next) {
    // should be the last extension in the chain
  }

  @Nullable
  @Override
  public GradleProjectResolverExtension getNext() {
    return null;
  }

  @NotNull
  @Override
  public ProjectData createProject() {
    final String projectDirPath = resolverCtx.getProjectPath();
    final IdeaProject ideaProject = resolverCtx.getModels().getIdeaProject();
    return new ProjectData(GradleConstants.SYSTEM_ID, ideaProject.getName(), projectDirPath, projectDirPath);
  }

  @NotNull
  @Override
  public JavaProjectData createJavaProjectData() {
    final String projectDirPath = resolverCtx.getProjectPath();
    final IdeaProject ideaProject = resolverCtx.getModels().getIdeaProject();

    // Gradle API doesn't expose gradleProject compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    javaProjectData.setJdkVersion(ideaProject.getJdkName());
    javaProjectData.setLanguageLevel(ideaProject.getLanguageLevel().getLevel());
    return javaProjectData;
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject) {
    final ExternalProject externalProject = resolverCtx.getExtraProject(ExternalProject.class);
    if (externalProject != null) {
      ideProject.createChild(ExternalProjectDataService.KEY, externalProject);
    }
  }

  @NotNull
  @Override
  public ModuleData createModule(@NotNull IdeaModule gradleModule, @NotNull ProjectData projectData) {
    final String moduleName = gradleModule.getName();
    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }

    final String moduleConfigPath = getModuleConfigPath(gradleModule, projectData.getLinkedExternalProjectPath());

    if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
      LOG.info(String.format(
        "Creating module data ('%s') with the external config path: '%s'", gradleModule.getGradleProject().getPath(), moduleConfigPath
      ));
    }

    String gradlePath = gradleModule.getGradleProject().getPath();
    String moduleId = StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
    ModuleData moduleData =
      new ModuleData(moduleId, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), moduleName, moduleConfigPath, moduleConfigPath);

    final ModuleExtendedModel moduleExtendedModel = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    if (moduleExtendedModel != null) {
      moduleData.setGroup(moduleExtendedModel.getGroup());
      moduleData.setVersion(moduleExtendedModel.getVersion());
      moduleData.setArtifacts(moduleExtendedModel.getArtifacts());
    }
    return moduleData;
  }

  @Override
  public void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    final BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
    if (buildScriptClasspathModel != null) {
      classpathEntries = ContainerUtil
        .map(buildScriptClasspathModel.getClasspath(), new Function<ClasspathEntryModel, BuildScriptClasspathData.ClasspathEntry>() {
          @Override
          public BuildScriptClasspathData.ClasspathEntry fun(ClasspathEntryModel model) {
            return new BuildScriptClasspathData.ClasspathEntry(model.getClasses(), model.getSources(), model.getJavadoc());
          }
        });
    }
    else {
      classpathEntries = ContainerUtil.emptyList();
    }
    BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
    ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule) {
    DomainObjectSet<? extends IdeaContentRoot> contentRoots;
    ModuleExtendedModel moduleExtendedModel = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    if (moduleExtendedModel != null) {
      contentRoots = moduleExtendedModel.getContentRoots();
    }
    else {
      contentRoots = gradleModule.getContentRoots();
    }

    if (contentRoots == null) {
      return;
    }
    for (IdeaContentRoot gradleContentRoot : contentRoots) {
      if (gradleContentRoot == null) continue;

      File rootDirectory = gradleContentRoot.getRootDirectory();
      if (rootDirectory == null) continue;

      ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.getAbsolutePath());
      ideModule.getData().setModuleFileDirectoryPath(ideContentRoot.getRootPath());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
      populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());

      if (gradleContentRoot instanceof ExtIdeaContentRoot) {
        ExtIdeaContentRoot extIdeaContentRoot = (ExtIdeaContentRoot)gradleContentRoot;
        populateContentRoot(ideContentRoot, ExternalSystemSourceType.RESOURCE, extIdeaContentRoot.getResourceDirectories());
        populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST_RESOURCE, extIdeaContentRoot.getTestResourceDirectories());
      }

      Set<File> excluded = gradleContentRoot.getExcludeDirectories();
      if (excluded != null) {
        for (File file : excluded) {
          ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getAbsolutePath());
        }
      }
      ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
    }
  }


  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    IdeaCompilerOutput moduleCompilerOutput = gradleModule.getCompilerOutput();

    File buildDir = null;
    try {
      buildDir = gradleModule.getGradleProject().getBuildDirectory();
    }
    catch (UnsupportedMethodException ignore) {
      // see org.gradle.tooling.model.GradleProject.getBuildDirectory method supported only since Gradle 2.0
      // will use com.intellij.openapi.externalSystem.model.ExternalProject.getBuildDir() instead
    }

    Map<ExternalSystemSourceType, File> compileOutputPaths = ContainerUtil.newHashMap();

    boolean inheritOutputDirs = false;

    ModuleData moduleData = ideModule.getData();
    if (moduleCompilerOutput != null) {
      compileOutputPaths.put(ExternalSystemSourceType.SOURCE, moduleCompilerOutput.getOutputDir());
      compileOutputPaths.put(ExternalSystemSourceType.RESOURCE, moduleCompilerOutput.getOutputDir());
      compileOutputPaths.put(ExternalSystemSourceType.TEST, moduleCompilerOutput.getTestOutputDir());
      compileOutputPaths.put(ExternalSystemSourceType.TEST_RESOURCE, moduleCompilerOutput.getTestOutputDir());

      inheritOutputDirs = moduleCompilerOutput.getInheritOutputDirs();
    }

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      externalProject = new DefaultExternalProject(externalProject);
      buildDir = buildDir == null ? externalProject.getBuildDir() : buildDir;

      if (!inheritOutputDirs) {
        final File sourceCompileOutputPath = compileOutputPaths.get(ExternalSystemSourceType.SOURCE);
        if (sourceCompileOutputPath == null) {
          addCompileOutputPath(compileOutputPaths, externalProject, MAIN_SOURCE_SET, ExternalSystemSourceType.SOURCE);
          addCompileOutputPath(compileOutputPaths, externalProject, MAIN_SOURCE_SET, ExternalSystemSourceType.RESOURCE);
        }

        final File testCompileOutputPath = compileOutputPaths.get(ExternalSystemSourceType.TEST);
        if (testCompileOutputPath == null) {
          addCompileOutputPath(compileOutputPaths, externalProject, TEST_SOURCE_SET, ExternalSystemSourceType.TEST);
          addCompileOutputPath(compileOutputPaths, externalProject, TEST_SOURCE_SET, ExternalSystemSourceType.TEST_RESOURCE);
        }
      }
    }
    else {
      LOG.warn(String.format("Unable to get ExternalProject model for '%s'", gradleModule.getName()));
    }

    for (Map.Entry<ExternalSystemSourceType, File> sourceTypeFileEntry : compileOutputPaths.entrySet()) {
      final File outputPath = ObjectUtils.chooseNotNull(sourceTypeFileEntry.getValue(), buildDir);
      if (outputPath != null) {
        moduleData.setCompileOutputPath(sourceTypeFileEntry.getKey(), outputPath.getAbsolutePath());
      }
    }

    moduleData.setInheritProjectCompileOutputPath(inheritOutputDirs);
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    final List<? extends IdeaDependency> dependencies = gradleModule.getDependencies().getAll();

    if (dependencies == null) return;

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
        LibraryDependencyData d = buildDependency(gradleModule, ideModule, (IdeaSingleEntryLibraryDependency)dependency, ideProject);
        d.setExported(dependency.getExported());
        if (scope != null) {
          d.setScope(scope);
        }
        ideModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, d);
      }
    }
  }

  @NotNull
  @Override
  public Collection<TaskData> populateModuleTasks(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule,
                                                  @NotNull DataNode<ProjectData> ideProject)
    throws IllegalArgumentException, IllegalStateException {

    final Collection<TaskData> tasks = ContainerUtil.newArrayList();
    final String moduleConfigPath = ideModule.getData().getLinkedExternalProjectPath();

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);

    if (externalProject != null) {
      for (ExternalTask task : externalProject.getTasks().values()) {
        String taskName = task.getName();
        if (taskName.trim().isEmpty() || isIdeaTask(taskName)) {
          continue;
        }
        TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, moduleConfigPath, task.getDescription());
        taskData.setGroup(task.getGroup());
        ideModule.createChild(ProjectKeys.TASK, taskData);
        taskData.setInherited(StringUtil.equals(task.getName(), task.getQName()));
        tasks.add(taskData);
      }

      return tasks;
    }

    for (GradleTask task : gradleModule.getGradleProject().getTasks()) {
      String taskName = task.getName();
      if (taskName == null || taskName.trim().isEmpty() || isIdeaTask(taskName)) {
        continue;
      }
      TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, moduleConfigPath, task.getDescription());
      ideModule.createChild(ProjectKeys.TASK, taskData);
      tasks.add(taskData);
    }

    return tasks;
  }

  @NotNull
  @Override
  public Set<Class> getExtraProjectModelClasses() {
    Set<Class> result = ContainerUtil.<Class>set(GradleBuild.class, ExternalProject.class, ModuleExtendedModel.class);
    if (!ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID) || !resolverCtx.isPreviewMode()) {
      result.add(BuildScriptClasspathModel.class);
    }
    return result;
  }

  @NotNull
  @Override
  public Set<Class> getToolingExtensionsClasses() {
    return ContainerUtil.<Class>set(
      ExternalProject.class,
      // gradle-tooling-extension-api jar
      ProjectImportAction.class,
      // gradle-tooling-extension-impl jar
      ModelBuildScriptClasspathBuilderImpl.class,
      GsonBuilder.class,
      ShortTypeHandling.class
    );
  }

  @NotNull
  @Override
  public List<KeyValue<String, String>> getExtraJvmArgs() {
    if (ExternalSystemApiUtil.isInProcessMode(GradleConstants.SYSTEM_ID)) {
      final List<KeyValue<String, String>> extraJvmArgs = ContainerUtil.newArrayList();
      final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (!StringUtil.isEmpty(httpConfigurable.PROXY_EXCEPTIONS)) {
        List<String> hosts = StringUtil.split(httpConfigurable.PROXY_EXCEPTIONS, ",");
        if (!hosts.isEmpty()) {
          final String nonProxyHosts = StringUtil.join(hosts, StringUtil.TRIMMER, "|");
          extraJvmArgs.add(KeyValue.create("http.nonProxyHosts", nonProxyHosts));
          extraJvmArgs.add(KeyValue.create("https.nonProxyHosts", nonProxyHosts));
        }
      }
      if (httpConfigurable.USE_HTTP_PROXY && StringUtil.isNotEmpty(httpConfigurable.PROXY_LOGIN)) {
        extraJvmArgs.add(KeyValue.create("http.proxyUser", httpConfigurable.PROXY_LOGIN));
        extraJvmArgs.add(KeyValue.create("https.proxyUser", httpConfigurable.PROXY_LOGIN));
        final String plainProxyPassword = httpConfigurable.getPlainProxyPassword();
        extraJvmArgs.add(KeyValue.create("http.proxyPassword", plainProxyPassword));
        extraJvmArgs.add(KeyValue.create("https.proxyPassword", plainProxyPassword));
      }
      extraJvmArgs.addAll(HttpConfigurable.getJvmPropertiesList(false, null));

      return extraJvmArgs;
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<String> getExtraCommandLineArgs() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    return myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
  }

  @Override
  public void preImportCheck() {
  }

  @Override
  public void enhanceTaskProcessing(@NotNull List<String> taskNames,
                                    @Nullable String debuggerSetup,
                                    @NotNull Consumer<String> initScriptConsumer) {
    if (!StringUtil.isEmpty(debuggerSetup)) {
      final String[] lines = {
        "gradle.taskGraph.beforeTask { Task task ->",
        "    if (task instanceof JavaForkOptions) {",
        "        def jvmArgs = task.jvmArgs.findAll{!it?.startsWith('-agentlib') && !it?.startsWith('-Xrunjdwp')}",
        "        jvmArgs << '" + debuggerSetup.trim() + '\'',
        "        task.jvmArgs jvmArgs",
        "    }" +
        "}",
      };
      final String script = StringUtil.join(lines, SystemProperties.getLineSeparator());
      initScriptConsumer.consume(script);
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
    PathsList classPath = parameters.getClassPath();

    // Gradle i18n bundle.
    ExternalSystemApiUtil.addBundle(classPath, GradleBundle.PATH_TO_BUNDLE, GradleBundle.class);

    // Gradle tool jars.
    String toolingApiPath = PathManager.getJarPathForClass(ProjectConnection.class);
    if (toolingApiPath == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries");
    }
    File gradleJarsDir = new File(toolingApiPath).getParentFile();
    File[] gradleJars = gradleJarsDir.listFiles(FileFilters.filesWithExtension("jar"));
    if (gradleJars == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries at " + gradleJarsDir.getAbsolutePath());
    }
    for (File jar : gradleJars) {
      classPath.add(jar.getAbsolutePath());
    }

    List<String> additionalEntries = ContainerUtilRt.newArrayList();
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(GroovyObject.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(GsonBuilder.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ExternalProject.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaProjectData.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(LanguageLevel.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(StdModuleTypes.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(EmptyModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ProjectImportAction.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(Init.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(org.slf4j.Logger.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }
  }

  @Override
  public void enhanceLocalProcessing(@NotNull List<URL> urls) {
  }

  @NotNull
  private String getModuleConfigPath(@NotNull IdeaModule gradleModule, @NotNull String rootProjectPath) {
    GradleBuild build = resolverCtx.getExtraProject(gradleModule, GradleBuild.class);
    if (build != null) {
      String gradlePath = gradleModule.getGradleProject().getPath();
      File moduleDirPath = getModuleDirPath(build, gradlePath);
      if (moduleDirPath == null) {
        throw new IllegalStateException(String.format("Unable to find root directory for module '%s'", gradleModule.getName()));
      }
      try {
        return ExternalSystemApiUtil.toCanonicalPath(moduleDirPath.getCanonicalPath());
      }
      catch (IOException e) {
        LOG.warn("construction of the canonical path for the module fails", e);
      }
    }

    return GradleUtil.getConfigPath(gradleModule.getGradleProject(), rootProjectPath);
  }

  /**
   * Returns the physical path of the module's root directory (the path in the file system.)
   * <p>
   * It is important to note that Gradle has its own "logical" path that may or may not be equal to the physical path of a Gradle project.
   * For example, the sub-project at ${projectRootDir}/apps/app will have the Gradle path :apps:app. Gradle also allows mapping physical
   * paths to a different logical path. For example, in settings.gradle:
   * <pre>
   *   include ':app'
   *   project(':app').projectDir = new File(rootDir, 'apps/app')
   * </pre>
   * In this example, sub-project at ${projectRootDir}/apps/app will have the Gradle path :app.
   * </p>
   *
   * @param build contains information about the root Gradle project and its sub-projects. Such information includes the physical path of
   *              the root Gradle project and its sub-projects.
   * @param path  the Gradle "logical" path. This path uses colon as separator, and may or may not be equal to the physical path of a
   *              Gradle project.
   * @return the physical path of the module's root directory.
   */
  @Nullable
  static File getModuleDirPath(@NotNull GradleBuild build, @NotNull String path) {
    for (BasicGradleProject project : build.getProjects()) {
      if (project.getPath().equals(path)) {
        return project.getProjectDirectory();
      }
    }
    return null;
  }

  /**
   * Stores information about given directories at the given content root
   *
   * @param contentRoot target paths info holder
   * @param type        type of data located at the given directories
   * @param dirs        directories which paths should be stored at the given content root
   * @throws IllegalArgumentException if specified by {@link ContentRootData#storePath(ExternalSystemSourceType, String)}
   */
  private static void populateContentRoot(@NotNull final ContentRootData contentRoot,
                                          @NotNull final ExternalSystemSourceType type,
                                          @Nullable final Iterable<? extends IdeaSourceDirectory> dirs)
    throws IllegalArgumentException {
    if (dirs == null) {
      return;
    }
    for (IdeaSourceDirectory dir : dirs) {
      ExternalSystemSourceType dirSourceType = type;
      try {
        if (dir.isGenerated() && !dirSourceType.isGenerated()) {
          final ExternalSystemSourceType generatedType = ExternalSystemSourceType.from(
            dirSourceType.isTest(), dir.isGenerated(), dirSourceType.isResource(), dirSourceType.isExcluded()
          );
          dirSourceType = generatedType != null ? generatedType : dirSourceType;
        }
      }
      catch (UnsupportedMethodException e) {
        // org.gradle.tooling.model.idea.IdeaSourceDirectory.isGenerated method supported only since Gradle 2.2
        LOG.warn(e.getMessage());
        printToolingProxyDiagnosticInfo(dir);
      }
      catch (Throwable e) {
        LOG.debug(e);
        printToolingProxyDiagnosticInfo(dir);
      }
      contentRoot.storePath(dirSourceType, dir.getDirectory().getAbsolutePath());
    }
  }

  private static void printToolingProxyDiagnosticInfo(@Nullable Object obj) {
    if (!LOG.isDebugEnabled() || obj == null) return;

    LOG.debug(String.format("obj: %s", obj));
    final Class<?> aClass = obj.getClass();
    LOG.debug(String.format("obj class: %s", aClass));
    LOG.debug(String.format("classloader: %s", aClass.getClassLoader()));
    for (Method m : aClass.getDeclaredMethods()) {
      LOG.debug(String.format("obj m: %s", m));
    }

    if (obj instanceof Proxy) {
      try {
        final Field hField = ReflectionUtil.findField(obj.getClass(), null, "h");
        hField.setAccessible(true);
        final Object h = hField.get(obj);
        final Field delegateField = ReflectionUtil.findField(h.getClass(), null, "delegate");
        delegateField.setAccessible(true);
        final Object delegate = delegateField.get(h);
        LOG.debug(String.format("delegate: %s", delegate));
        LOG.debug(String.format("delegate class: %s", delegate.getClass()));
        LOG.debug(String.format("delegate classloader: %s", delegate.getClass().getClassLoader()));
        for (Method m : delegate.getClass().getDeclaredMethods()) {
          LOG.debug(String.format("delegate m: %s", m));
        }
      }
      catch (NoSuchFieldException e) {
        LOG.debug(e);
      }
      catch (IllegalAccessException e) {
        LOG.debug(e);
      }
    }
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

  @NotNull
  private static ModuleDependencyData buildDependency(@NotNull DataNode<ModuleData> ownerModule,
                                                      @NotNull IdeaModuleDependency dependency,
                                                      @NotNull DataNode<ProjectData> ideProject)
    throws IllegalStateException {
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
      String name = moduleDataNode.getData().getExternalName();
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
  private LibraryDependencyData buildDependency(@NotNull IdeaModule gradleModule,
                                                @NotNull DataNode<ModuleData> ownerModule,
                                                @NotNull IdeaSingleEntryLibraryDependency dependency,
                                                @NotNull DataNode<ProjectData> ideProject)
  throws IllegalStateException {
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      throw new IllegalStateException(String.format(
        "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the binaries", dependency
      ));
    }

    String libraryName;
    final GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
    final LibraryLevel level;

    // Gradle API doesn't explicitly provide information about unresolved libraries (http://issues.gradle.org/browse/GRADLE-1995).
    // That's why we use this dirty hack here.
    boolean unresolved = binaryPath.getPath().startsWith(UNRESOLVED_DEPENDENCY_PREFIX);

    if (moduleVersion == null) {
      // use module library level if the dependency does not originate from a remote repository.
      level = LibraryLevel.MODULE;

      if (binaryPath.isFile()) {
        libraryName = FileUtil.getNameWithoutExtension(binaryPath);
      }
      else {
        libraryName = FileUtil.sanitizeFileName(binaryPath.getPath());
      }

      if (unresolved) {
        // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
        libraryName = binaryPath.getPath().substring(UNRESOLVED_DEPENDENCY_PREFIX.length());
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
    }
    else {
      level = LibraryLevel.PROJECT;
      libraryName = String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
      if (binaryPath.isFile()) {
        String libraryFileName = FileUtil.getNameWithoutExtension(binaryPath);
        final String mavenLibraryFileName = String.format("%s-%s", moduleVersion.getName(), moduleVersion.getVersion());
        if (!mavenLibraryFileName.equals(libraryFileName)) {
          Pattern pattern = Pattern.compile(moduleVersion.getName() + "-" + moduleVersion.getVersion() + "-(.*)");
          Matcher matcher = pattern.matcher(libraryFileName);
          if (matcher.matches()) {
            final String classifier = matcher.group(1);
            libraryName += (":" + classifier);
          }
          else {
            final String artifactId = StringUtil.trimEnd(StringUtil.trimEnd(libraryFileName, moduleVersion.getVersion()), "-");
            libraryName = String.format("%s:%s:%s",
                                        moduleVersion.getGroup(),
                                        artifactId,
                                        moduleVersion.getVersion());
          }
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

    if (!unresolved && sourcePath == null) {
      attachGradleSdkSources(gradleModule, libraryName, binaryPath, library);
    }

    File javadocPath = dependency.getJavadoc();
    if (!unresolved && javadocPath != null) {
      library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
    }

    if(level == LibraryLevel.PROJECT) {
      DataNode<LibraryData> libraryData =
        ExternalSystemApiUtil.find(ideProject, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
          @Override
          public boolean fun(DataNode<LibraryData> node) {
            return library.equals(node.getData());
          }
        });
      if (libraryData == null) {
        ideProject.createChild(ProjectKeys.LIBRARY, library);
      }
    }

    return new LibraryDependencyData(ownerModule.getData(), library, level);
  }

  private void attachGradleSdkSources(@NotNull IdeaModule gradleModule,
                                      @NotNull final String libName,
                                      @Nullable final File libFile,
                                      LibraryData library) {
    if (libFile == null || !libName.startsWith("gradle-")) return;

    final BuildScriptClasspathModel buildScriptClasspathModel =
      resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    if (buildScriptClasspathModel == null) return;
    final File gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
    if (gradleHomeDir == null) return;

    if (!FileUtil.isAncestor(gradleHomeDir, libFile, true)) return;

    File libOrPluginsFile = libFile.getParentFile();
    if (libOrPluginsFile != null && ("plugins".equals(libOrPluginsFile.getName()))) {
      libOrPluginsFile = libOrPluginsFile.getParentFile();
    }

    if (libOrPluginsFile != null && "lib".equals(libOrPluginsFile.getName()) && libOrPluginsFile.getParentFile() != null) {
      File srcDir = new File(libOrPluginsFile.getParentFile(), "src");

      GradleVersion current = GradleVersion.version(buildScriptClasspathModel.getGradleVersion());
      if (current.compareTo(GradleVersion.version("1.9")) >= 0) {
        int endIndex = libName.indexOf(current.getVersion());
        if (endIndex != -1) {
          String srcDirChild = libName.substring("gradle-".length(), endIndex - 1);
          srcDir = new File(srcDir, srcDirChild);
        }
      }

      if (srcDir.isDirectory()) {
        library.addPath(LibraryPathType.SOURCE, srcDir.getAbsolutePath());
      }
    }
  }

  private static boolean isIdeaTask(final String taskName) {
    return taskName.toLowerCase(Locale.ENGLISH).contains("idea");
  }

  private static void addCompileOutputPath(@NotNull Map<ExternalSystemSourceType, File> compileOutputPaths,
                                           @NotNull ExternalProject externalProject,
                                           @NotNull String sourceSetName,
                                           @NotNull ExternalSystemSourceType sourceType) {
    final ExternalSourceSet sourceSet = externalProject.getSourceSets().get(sourceSetName);
    if (sourceSet == null) return;

    final ExternalSourceDirectorySet directorySet = sourceSet.getSources().get(sourceType);
    if (directorySet != null) {
      compileOutputPaths.put(sourceType, directorySet.getOutputDir());
    }
  }
}
