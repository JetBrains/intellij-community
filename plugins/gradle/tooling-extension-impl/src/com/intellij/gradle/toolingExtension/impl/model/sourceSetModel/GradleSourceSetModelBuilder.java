// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollectionVisitor;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollections;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import com.intellij.gradle.toolingExtension.util.GradleReflectionUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.util.StringUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

@ApiStatus.Internal
public class GradleSourceSetModelBuilder extends AbstractModelBuilderService {

  private static final String JVM_TEST_SUITE_PLUGIN_ID = "jvm-test-suite";
  private static final String TESTING_EXTENSION_CLASS = "org.gradle.testing.base.TestingExtension";
  private static final String JVM_TEST_SUITE_CLASS = "org.gradle.api.plugins.jvm.JvmTestSuite";

  @Override
  public boolean canBuild(String modelName) {
    return GradleSourceSetModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    DefaultGradleSourceSetModel sourceSetModel = new DefaultGradleSourceSetModel();
    sourceSetModel.setSourceCompatibility(JavaPluginUtil.getSourceCompatibility(project));
    sourceSetModel.setTargetCompatibility(JavaPluginUtil.getTargetCompatibility(project));
    sourceSetModel.setTaskArtifacts(collectProjectTaskArtifacts(project, context));
    sourceSetModel.setConfigurationArtifacts(collectProjectConfigurationArtifacts(project, context));
    sourceSetModel.setAdditionalArtifacts(collectNonSourceSetArtifacts(project, context));
    sourceSetModel.setSourceSets(collectSourceSets(project, context));

    return sourceSetModel;
  }

  @Override
  public void reportErrorMessage(@NotNull String modelName,
                                 @NotNull Project project,
                                 @NotNull ModelBuilderContext context,
                                 @NotNull Exception exception) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.SOURCE_SET_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Source set model building failure")
      .withText("Project source sets cannot be resolved")
      .withException(exception)
      .reportMessage(project);
  }

  private static @NotNull List<File> collectProjectTaskArtifacts(@NotNull Project project, @NotNull ModelBuilderContext context) {
    List<File> taskArtifacts = new ArrayList<>();
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      public void visit(Jar element) {
        File archiveFile = GradleTaskUtil.getTaskArchiveFile(element);
        taskArtifacts.add(archiveFile);
      }

      @Override
      public void onFailure(Jar element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_PROJECT_TASK_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Cannot resolve artifact file for the project Jar task: " + element.getPath())
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(Jar element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_TASK_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Artifact files collecting for project Jar tasks was finished. " +
                    "Resolution for the Jar task " + element.getPath() + " will be skipped.")
          .withInternal().withStackTrace()
          .withKind(Message.Kind.WARNING)
          .reportMessage(project);
      }
    });
    return new ArrayList<>(taskArtifacts);
  }

  private static @NotNull List<File> collectNonSourceSetArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    List<File> additionalArtifacts = new ArrayList<>();
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      public void visit(Jar element) {
        if (isShadowJar(element) || containsPotentialClasspathElements(element, project)) {
          File archiveFile = GradleTaskUtil.getTaskArchiveFile(element);
          additionalArtifacts.add(archiveFile);
        }
      }

      @Override
      public void onFailure(Jar element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Cannot resolve an artifact file for the project Jar task: " + element.getPath())
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(Jar element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Artifact files collecting for project Jar tasks was finished. " +
                    "Resolution for the Jar task " + element.getPath() + " will be skipped.")
          .withInternal().withStackTrace()
          .withKind(Message.Kind.WARNING)
          .reportMessage(project);
      }
    });
    return additionalArtifacts;
  }

  private static @NotNull Map<String, Set<File>> collectProjectConfigurationArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    Map<String, Set<File>> configurationArtifacts = new HashMap<>();
    GradleCollectionVisitor.accept(project.getConfigurations(), new GradleCollectionVisitor<Configuration>() {

      @Override
      public void visit(Configuration element) {
        PublishArtifactSet artifactSet = element.getArtifacts();
        FileCollection fileCollection = artifactSet.getFiles();
        Set<File> files = fileCollection.getFiles();
        configurationArtifacts.put(element.getName(), new LinkedHashSet<>(files));
      }

      @Override
      public void onFailure(Configuration element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_PROJECT_CONFIGURATION_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Cannot resolve an artifact file for the project configuration" + element)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(Configuration element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_CONFIGURATION_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Artifact files collecting for project configurations was finished. " +
                    "Resolution for the configuration " + element + " will be skipped.")
          .withInternal().withStackTrace()
          .withKind(Message.Kind.WARNING)
          .reportMessage(project);
      }
    });
    return configurationArtifacts;
  }

  private static @NotNull Collection<File> collectSourceSetArtifacts(
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull SourceSet sourceSet
  ) {
    Collection<File> sourceSetArtifacts = new LinkedHashSet<>();
    TaskCollection<AbstractArchiveTask> archiveTaskCollection = project.getTasks().withType(AbstractArchiveTask.class);
    GradleCollectionVisitor.accept(archiveTaskCollection, new GradleCollectionVisitor<AbstractArchiveTask>() {

      @Override
      public void visit(AbstractArchiveTask element) {
        if (containsAllSourceSetOutput(element, sourceSet)) {
          File archiveFile = GradleTaskUtil.getTaskArchiveFile(element);
          sourceSetArtifacts.add(archiveFile);
        }
      }

      @Override
      public void onFailure(AbstractArchiveTask element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Cannot resolve an artifact file for the source set " + element)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(AbstractArchiveTask element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Artifact files collecting for source sets was finished. " +
                    "Resolution for the source set" + element + " will be skipped.")
          .withInternal().withStackTrace()
          .withKind(Message.Kind.WARNING)
          .reportMessage(project);
      }
    });
    return sourceSetArtifacts;
  }

  private static void cleanupSharedSourceDirs(
    @NotNull Map<String, DefaultExternalSourceSet> externalSourceSets,
    @NotNull String sourceSetName,
    @Nullable String sourceSetNameToIgnore
  ) {
    DefaultExternalSourceSet sourceSet = externalSourceSets.get(sourceSetName);
    if (sourceSet == null) return;

    for (Map.Entry<String, DefaultExternalSourceSet> sourceSetEntry : externalSourceSets.entrySet()) {
      if (Objects.equals(sourceSetEntry.getKey(), sourceSetName)) continue;
      if (Objects.equals(sourceSetEntry.getKey(), sourceSetNameToIgnore)) continue;

      DefaultExternalSourceSet customSourceSet = sourceSetEntry.getValue();
      for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
        DefaultExternalSourceDirectorySet customSourceDirectorySet = customSourceSet.getSources().get(sourceType);
        if (customSourceDirectorySet != null) {
          for (Map.Entry<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sourceDirEntry : sourceSet.getSources().entrySet()) {
            customSourceDirectorySet.getSrcDirs().removeAll(sourceDirEntry.getValue().getSrcDirs());
          }
        }
      }
    }
  }

  private static void cleanupSharedIdeaSourceDirs(
    @NotNull DefaultExternalSourceSet externalSourceSet, // mutable
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    if (SourceSet.MAIN_SOURCE_SET_NAME.equals(externalSourceSet.getName())) return;
    if (SourceSet.TEST_SOURCE_SET_NAME.equals(externalSourceSet.getName())) return;

    for (DefaultExternalSourceDirectorySet sourceDirectorySet : externalSourceSet.getSources().values()) {
      sourceSetResolutionContext.ideaSourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
      sourceSetResolutionContext.ideaResourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
      sourceSetResolutionContext.ideaTestSourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
      sourceSetResolutionContext.ideaTestResourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
    }
  }

  private static boolean isShadowJar(Jar task) {
    Class<?> type = GradleTaskUtil.getTaskIdentityType(task);
    return type != null && type.getName().equals("com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar");
  }

  /**
   * Check the configured archive task inputs for specific files.
   * <p>
   * We want to check if IDEA is interested in keeping this archive task output in the dependency list
   * <br>
   * This may happen if <ul>
   * <li>there are some class files
   * <li>there are directories, not known to be outputs of source sets (modules)
   *
   * @param archiveTask task to check
   * @param project     project with source sets, potentially contributing to this task.
   * @return true if this jar should be kept in IDEA modules dependency lists.
   */
  private static boolean containsPotentialClasspathElements(@NotNull AbstractArchiveTask archiveTask, @NotNull Project project) {
    SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSetContainer == null || sourceSetContainer.isEmpty()) {
      return true;
    }
    HashSet<File> outputFiles = new HashSet<>();
    sourceSetContainer.all(ss -> outputFiles.addAll(ss.getOutput().getFiles()));
    for (Object path : getArchiveTaskSourcePaths(archiveTask)) {
      if (isSafeToResolve(path, project) || isResolvableFileCollection(path, project)) {
        for (File f : project.files(path).getFiles()) {
          if (outputFiles.contains(f)) continue;
          if (f.isDirectory() || (f.isFile() && f.getName().endsWith(".class"))) {
            return true;
          }
        }
      }
      else {
        return true;
      }
    }
    return false;
  }

  private static boolean containsAllSourceSetOutput(@NotNull AbstractArchiveTask archiveTask, @NotNull SourceSet sourceSet) {
    Set<File> outputFiles = new HashSet<>(sourceSet.getOutput().getFiles());
    Project project = archiveTask.getProject();

    try {
      Set<Object> sourcePaths = getArchiveTaskSourcePaths(archiveTask);
      for (Object path : sourcePaths) {
        if (isSafeToResolve(path, project) || isResolvableFileCollection(path, project)) {
          outputFiles.removeAll(project.files(path).getFiles());
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return outputFiles.isEmpty();
  }

  private static Set<Object> getArchiveTaskSourcePaths(AbstractArchiveTask archiveTask) {
    try {
      final Method mainSpecGetter = AbstractCopyTask.class.getDeclaredMethod("getMainSpec");
      mainSpecGetter.setAccessible(true);
      Object mainSpec = mainSpecGetter.invoke(archiveTask);
      Method getSourcePaths = mainSpec.getClass().getMethod("getSourcePaths");

      @SuppressWarnings("unchecked")
      Set<Object> sourcePaths = (Set<Object>)getSourcePaths.invoke(mainSpec);
      if (sourcePaths != null) {
        return sourcePaths;
      }
      else {
        return Collections.emptySet();
      }
    }
    catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException ignored) {
      return Collections.emptySet();
    }
  }


  private static boolean isResolvableFileCollection(Object param, Project project) {
    Object object = tryUnpackPresentProvider(param, project);
    if (object instanceof FileCollection) {
      try {
        project.files(object).getFiles();
        return true;
      }
      catch (Throwable ignored) {
        return false;
      }
    }
    return false;
  }

  /**
   * Checks that object can be safely resolved using {@link Project#files(Object...)} API.
   *
   * @return true if object is safe to resolve using {@link Project#files(Object...)}
   * @see GradleSourceSetModelBuilder#tryUnpackPresentProvider
   */
  private static boolean isSafeToResolve(Object param, Project project) {
    Object object = tryUnpackPresentProvider(param, project);
    return object instanceof CharSequence ||
           object instanceof File ||
           object instanceof Path ||
           object instanceof SourceSetOutput ||
           GradleReflectionUtil.isInstance(object, "org.gradle.api.file.Directory") ||
           GradleReflectionUtil.isInstance(object, "org.gradle.api.file.RegularFile");
  }

  /**
   * Some Gradle {@link org.gradle.api.provider.Provider} implementations cannot be resolved during sync,
   * causing {@link org.gradle.api.InvalidUserCodeException} and {@link org.gradle.api.InvalidUserDataException}.
   * Also, some {@link org.gradle.api.provider.Provider} attempts to resolve dynamic
   * configurations, witch results in resolving a configuration without write lock on the project.
   *
   * @return provided value or current if value isn't present or cannot be evaluated
   */
  private static Object tryUnpackPresentProvider(Object object, Project project) {
    if (!GradleReflectionUtil.isInstance(object, "org.gradle.api.provider.Provider")) {
      return object;
    }
    try {
      Class<?> providerClass = object.getClass();
      Method isPresentMethod = providerClass.getMethod("isPresent");
      Method getterMethod = providerClass.getMethod("get");
      if ((Boolean)isPresentMethod.invoke(object)) {
        return getterMethod.invoke(object);
      }
      return object;
    }
    catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException exception) {
      Throwable cause = exception.getCause();
      boolean isCodeException = GradleReflectionUtil.isInstance(cause, "org.gradle.api.InvalidUserCodeException");
      boolean isDataException = GradleReflectionUtil.isInstance(cause, "org.gradle.api.InvalidUserDataException");
      if (isCodeException || isDataException) {
        return object;
      }
      String msg = cause.getMessage();
      String className = cause.getClass().getCanonicalName();
      project.getLogger().info("Unable to resolve task source path: {} ({})", msg, className);
      return object;
    }
  }

  static @NotNull Collection<SourceSet> collectTestSourceSets(@NotNull Project project) {
    Collection<SourceSet> result = new ArrayList<>();
    result.addAll(collectJvmTestSuiteSourceSets(project));
    result.addAll(collectTestFixtureSourceSets(project));
    return result;
  }

  private static @NotNull Collection<SourceSet> collectJvmTestSuiteSourceSets(@NotNull Project project) {
    if (GradleVersionUtil.isCurrentGradleOlderThan("7.4")) {
      return Collections.emptyList();
    }
    Plugin<?> plugin = project.getPlugins().findPlugin(JVM_TEST_SUITE_PLUGIN_ID);
    if (plugin == null) {
      return Collections.emptyList();
    }
    ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
    Class<?> testingExtensionClass = GradleReflectionUtil.loadClassOrNull(pluginClassLoader, TESTING_EXTENSION_CLASS);
    Class<?> jvmTestSuiteClass = GradleReflectionUtil.loadClassOrNull(pluginClassLoader, JVM_TEST_SUITE_CLASS);
    if (testingExtensionClass == null || jvmTestSuiteClass == null) {
      return Collections.emptyList();
    }
    Object testingExtension = project.findProperty("testing");
    if (testingExtension == null) {
      return Collections.emptyList();
    }
    Collection<SourceSet> result = new ArrayList<>();
    if (testingExtensionClass.isInstance(testingExtension)) {
      Collection<?> suites = GradleReflectionUtil.getValue(testingExtension, "getSuites", Collection.class);
      for (Object suite : suites) {
        if (jvmTestSuiteClass.isInstance(suite)) {
          SourceSet sourceSet = GradleReflectionUtil.getValue(suite, "getSources", SourceSet.class);
          if (sourceSet != null) {
            result.add(sourceSet);
          }
        }
      }
    }
    return result;
  }

  private static @NotNull Collection<SourceSet> collectTestFixtureSourceSets(@NotNull Project project) {
    SourceSetContainer sourceSets = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSets == null) {
      return Collections.emptyList();
    }
    SourceSet testFixtureSourceSet = sourceSets.findByName("testFixtures");
    if (testFixtureSourceSet == null) {
      return Collections.emptyList();
    }
    return Collections.singleton(testFixtureSourceSet);
  }

  private static void addJavaCompilerOptions(
    @NotNull DefaultExternalSourceSet externalSourceSet, // mutable
    @NotNull Project project,
    @NotNull SourceSet sourceSet,
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    Task javaCompileTask = project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
    if (javaCompileTask instanceof JavaCompile) {
      JavaCompile javaCompile = (JavaCompile)javaCompileTask;
      externalSourceSet.setJavaToolchainHome(getJavaToolchainHome(project, javaCompile));
      externalSourceSet.setSourceCompatibility(javaCompile.getSourceCompatibility());
      externalSourceSet.setTargetCompatibility(javaCompile.getTargetCompatibility());
      externalSourceSet.setCompilerArguments(GradleCollections.mapToString(javaCompile.getOptions().getAllCompilerArgs()));
    }
    if (externalSourceSet.getSourceCompatibility() == null) {
      externalSourceSet.setSourceCompatibility(sourceSetResolutionContext.projectSourceCompatibility);
    }
    if (externalSourceSet.getSourceCompatibility() == null) {
      externalSourceSet.setTargetCompatibility(sourceSetResolutionContext.projectTargetCompatibility);
    }
  }

  private static @Nullable File getJavaToolchainHome(@NotNull Project project, @NotNull JavaCompile javaCompile) {
    if (GradleVersionUtil.isCurrentGradleOlderThan("6.7")) {
      return null;
    }
    Property<JavaCompiler> compiler = javaCompile.getJavaCompiler();
    if (!compiler.isPresent()) {
      return null;
    }
    try {
      JavaInstallationMetadata metadata = compiler.get().getMetadata();
      File javaToolchainHome = metadata.getInstallationPath().getAsFile();
      if (GradleVersionUtil.isCurrentGradleOlderThan("8.0")) {
        return javaToolchainHome;
      }
      if (metadata instanceof JavaToolchain) {
        JavaToolchain javaToolchain = (JavaToolchain)metadata;
        if (!javaToolchain.isFallbackToolchain()) {
          return javaToolchainHome;
        }
      }
    }
    catch (Throwable e) {
      Logger logger = project.getLogger();
      logger.warn(String.format("Skipping java toolchain information for %s : %s", javaCompile.getPath(), e.getMessage()));
      logger.info(String.format("Failed to resolve java toolchain info for %s", javaCompile.getPath()), e);
    }
    return null;
  }

  private static @NotNull Map<String, DefaultExternalSourceSet> collectSourceSets(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    GradleSourceSetResolutionContext sourceSetResolutionContext = new GradleSourceSetResolutionContext(project, context);

    SourceSetContainer sourceSets = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSets == null) {
      return new LinkedHashMap<>();
    }

    Map<String, DefaultExternalSourceSet> result = new LinkedHashMap<>();
    sourceSets.forEach(sourceSet -> {
      DefaultExternalSourceSet externalSourceSet = new DefaultExternalSourceSet();
      externalSourceSet.setName(sourceSet.getName());
      externalSourceSet.setArtifacts(collectSourceSetArtifacts(project, context, sourceSet));

      addJavaCompilerOptions(externalSourceSet, project, sourceSet, sourceSetResolutionContext);
      addSourceDirs(externalSourceSet, project, sourceSet, sourceSetResolutionContext);
      addLegacyTestSourceDirs(externalSourceSet, project, sourceSetResolutionContext);

      cleanupSharedIdeaSourceDirs(externalSourceSet, sourceSetResolutionContext);

      result.put(externalSourceSet.getName(), externalSourceSet);
    });

    addUnprocessedIdeaSourceDirs(result, sourceSets, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME);
    addUnprocessedIdeaResourceDirs(result, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME);
    addUnprocessedIdeaGeneratedSourcesDirs(result, sourceSetResolutionContext, SourceSet.MAIN_SOURCE_SET_NAME);

    addUnprocessedIdeaSourceDirs(result, sourceSets, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME);
    addUnprocessedIdeaResourceDirs(result, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME);
    addUnprocessedIdeaGeneratedSourcesDirs(result, sourceSetResolutionContext, SourceSet.TEST_SOURCE_SET_NAME);

    cleanupSharedSourceDirs(result, SourceSet.MAIN_SOURCE_SET_NAME, null);
    cleanupSharedSourceDirs(result, SourceSet.TEST_SOURCE_SET_NAME, SourceSet.MAIN_SOURCE_SET_NAME);

    return result;
  }

  private static void addSourceDirs(
    @NotNull DefaultExternalSourceSet externalSourceSet, // mutable
    @NotNull Project project,
    @NotNull SourceSet sourceSet,
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    boolean resolveSourceSetDependencies = Boolean.getBoolean("idea.resolveSourceSetDependencies");

    DefaultExternalSourceDirectorySet sourceDirectorySet = new DefaultExternalSourceDirectorySet();
    sourceDirectorySet.setName(sourceSet.getAllJava().getName());
    sourceDirectorySet.setSrcDirs(sourceSet.getAllJava().getSrcDirs());
    sourceDirectorySet.setGradleOutputDirs(sourceSet.getOutput().getClassesDirs().getFiles());
    if (sourceDirectorySet.getGradleOutputDirs().isEmpty()) {
      sourceDirectorySet.setGradleOutputDirs(Collections.singleton(GradleProjectUtil.getBuildDirectory(project)));
    }
    sourceDirectorySet.setCompilerOutputPathInherited(sourceSetResolutionContext.isIdeaInheritOutputDirs);

    DefaultExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet();
    resourcesDirectorySet.setName(sourceSet.getResources().getName());
    resourcesDirectorySet.setSrcDirs(sourceSet.getResources().getSrcDirs());
    resourcesDirectorySet.setExcludes(sourceSet.getResources().getExcludes());
    resourcesDirectorySet.setIncludes(sourceSet.getResources().getIncludes());
    if (sourceSet.getOutput().getResourcesDir() != null) {
      resourcesDirectorySet.setGradleOutputDirs(Collections.singleton(sourceSet.getOutput().getResourcesDir()));
    }
    if (resourcesDirectorySet.getGradleOutputDirs().isEmpty()) {
      resourcesDirectorySet.setGradleOutputDirs(sourceDirectorySet.getGradleOutputDirs());
    }
    resourcesDirectorySet.setCompilerOutputPathInherited(sourceSetResolutionContext.isIdeaInheritOutputDirs);

    DefaultExternalSourceDirectorySet generatedSourceDirectorySet = null;
    Set<File> generatedSourceDirs = new LinkedHashSet<>(sourceDirectorySet.getSrcDirs());
    generatedSourceDirs.retainAll(sourceSetResolutionContext.ideaGeneratedSourceDirs);
    if (!generatedSourceDirs.isEmpty()) {
      sourceDirectorySet.getSrcDirs().removeAll(generatedSourceDirs);
      sourceSetResolutionContext.unprocessedIdeaGeneratedSourceDirs.removeAll(generatedSourceDirs);

      generatedSourceDirectorySet = new DefaultExternalSourceDirectorySet();
      generatedSourceDirectorySet.setName("generated " + sourceDirectorySet.getName());
      generatedSourceDirectorySet.setSrcDirs(generatedSourceDirs);
      generatedSourceDirectorySet.setGradleOutputDirs(sourceDirectorySet.getGradleOutputDirs());
      generatedSourceDirectorySet.setCompilerOutputPathInherited(sourceDirectorySet.isCompilerOutputPathInherited());
    }

    if (sourceSetResolutionContext.isJavaTestSourceSet(sourceSet)) {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaTestOutputDir != null) {
        sourceDirectorySet.setOutputDir(sourceSetResolutionContext.ideaTestOutputDir);
        resourcesDirectorySet.setOutputDir(sourceSetResolutionContext.ideaTestOutputDir);
      }
      else if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName()) || !resolveSourceSetDependencies) {
        sourceDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/test/classes"));
        resourcesDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/test/resources"));
      }
      else {
        String outputName = StringUtils.toCamelCase(sourceSet.getName(), true);
        sourceDirectorySet.setOutputDir(new File(project.getProjectDir(), String.format("out/%s/classes", outputName)));
        resourcesDirectorySet.setOutputDir(new File(project.getProjectDir(), String.format("out/%s/resources", outputName)));
      }
      if (generatedSourceDirectorySet != null) {
        generatedSourceDirectorySet.setOutputDir(sourceDirectorySet.getOutputDir());
      }

      resourcesDirectorySet.getExcludes().addAll(sourceSetResolutionContext.testResourcesExcludes);
      resourcesDirectorySet.getIncludes().addAll(sourceSetResolutionContext.testResourcesIncludes);
      resourcesDirectorySet.setFilters(sourceSetResolutionContext.testResourceFilters);

      externalSourceSet.addSource(ExternalSystemSourceType.TEST, sourceDirectorySet);
      externalSourceSet.addSource(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
      if (generatedSourceDirectorySet != null) {
        externalSourceSet.addSource(ExternalSystemSourceType.TEST_GENERATED, generatedSourceDirectorySet);
      }
    }
    else {
      if (!sourceSetResolutionContext.isIdeaInheritOutputDirs && sourceSetResolutionContext.ideaOutputDir != null) {
        sourceDirectorySet.setOutputDir(sourceSetResolutionContext.ideaOutputDir);
        resourcesDirectorySet.setOutputDir(sourceSetResolutionContext.ideaOutputDir);
      }
      else if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) || !resolveSourceSetDependencies) {
        sourceDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/production/classes"));
        resourcesDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/production/resources"));
      }
      else {
        String outputName = StringUtils.toCamelCase(sourceSet.getName(), true);
        sourceDirectorySet.setOutputDir(new File(project.getProjectDir(), String.format("out/%s/classes", outputName)));
        resourcesDirectorySet.setOutputDir(new File(project.getProjectDir(), String.format("out/%s/resources", outputName)));
      }
      if (generatedSourceDirectorySet != null) {
        generatedSourceDirectorySet.setOutputDir(sourceDirectorySet.getOutputDir());
      }

      resourcesDirectorySet.getExcludes().addAll(sourceSetResolutionContext.resourcesExcludes);
      resourcesDirectorySet.getIncludes().addAll(sourceSetResolutionContext.resourcesIncludes);
      resourcesDirectorySet.setFilters(sourceSetResolutionContext.resourceFilters);

      externalSourceSet.addSource(ExternalSystemSourceType.SOURCE, sourceDirectorySet);
      externalSourceSet.addSource(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet);
      if (generatedSourceDirectorySet != null) {
        externalSourceSet.addSource(ExternalSystemSourceType.SOURCE_GENERATED, generatedSourceDirectorySet);
      }
    }
  }

  static void addLegacyTestSourceDirs(
    @NotNull DefaultExternalSourceSet externalSourceSet, // mutable
    @NotNull Project project,
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext
  ) {
    if (Boolean.getBoolean("idea.resolveSourceSetDependencies")) return;

    Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources = externalSourceSet.getSources();
    DefaultExternalSourceDirectorySet sourceDirectorySet = sources.get(ExternalSystemSourceType.SOURCE);
    DefaultExternalSourceDirectorySet resourcesDirectorySet = sources.get(ExternalSystemSourceType.RESOURCE);
    DefaultExternalSourceDirectorySet generatedSourceDirectorySet = sources.get(ExternalSystemSourceType.SOURCE_GENERATED);

    if (sourceDirectorySet != null) {
      Set<File> testSourceDirs = new LinkedHashSet<>(sourceDirectorySet.getSrcDirs());
      testSourceDirs.retainAll(sourceSetResolutionContext.ideaTestSourceDirs);
      if (!testSourceDirs.isEmpty()) {
        sourceDirectorySet.getSrcDirs().removeAll(sourceSetResolutionContext.ideaTestSourceDirs);

        DefaultExternalSourceDirectorySet testSourceDirectorySet = new DefaultExternalSourceDirectorySet();
        testSourceDirectorySet.setName(sourceDirectorySet.getName());
        testSourceDirectorySet.setSrcDirs(testSourceDirs);
        testSourceDirectorySet.setGradleOutputDirs(Collections.singleton(sourceDirectorySet.getOutputDir()));
        if (sourceSetResolutionContext.ideaTestOutputDir != null) {
          testSourceDirectorySet.setOutputDir(sourceSetResolutionContext.ideaTestOutputDir);
        }
        else {
          testSourceDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/test/classes"));
        }
        testSourceDirectorySet.setCompilerOutputPathInherited(sourceDirectorySet.isCompilerOutputPathInherited());

        externalSourceSet.addSource(ExternalSystemSourceType.TEST, testSourceDirectorySet);
      }
    }

    if (resourcesDirectorySet != null) {
      Set<File> testResourceDirs = new LinkedHashSet<>(resourcesDirectorySet.getSrcDirs());
      testResourceDirs.retainAll(sourceSetResolutionContext.ideaTestSourceDirs);
      if (!testResourceDirs.isEmpty()) {
        resourcesDirectorySet.getSrcDirs().removeAll(sourceSetResolutionContext.ideaTestSourceDirs);

        DefaultExternalSourceDirectorySet testResourcesDirectorySet = new DefaultExternalSourceDirectorySet();
        testResourcesDirectorySet.setName(resourcesDirectorySet.getName());
        testResourcesDirectorySet.setSrcDirs(testResourceDirs);
        testResourcesDirectorySet.setGradleOutputDirs(Collections.singleton(resourcesDirectorySet.getOutputDir()));
        if (sourceSetResolutionContext.ideaTestOutputDir != null) {
          testResourcesDirectorySet.setOutputDir(sourceSetResolutionContext.ideaTestOutputDir);
        }
        else {
          testResourcesDirectorySet.setOutputDir(new File(project.getProjectDir(), "out/test/resources"));
        }
        testResourcesDirectorySet.setCompilerOutputPathInherited(resourcesDirectorySet.isCompilerOutputPathInherited());

        externalSourceSet.addSource(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet);
      }
    }

    if (generatedSourceDirectorySet != null) {
      Set<File> testGeneratedSourceDirs = new LinkedHashSet<>(generatedSourceDirectorySet.getSrcDirs());
      testGeneratedSourceDirs.retainAll(sourceSetResolutionContext.ideaTestSourceDirs);
      if (!testGeneratedSourceDirs.isEmpty()) {
        generatedSourceDirectorySet.getSrcDirs().removeAll(sourceSetResolutionContext.ideaTestSourceDirs);

        DefaultExternalSourceDirectorySet testGeneratedDirectorySet = new DefaultExternalSourceDirectorySet();
        testGeneratedDirectorySet.setName(generatedSourceDirectorySet.getName());
        testGeneratedDirectorySet.setSrcDirs(testGeneratedSourceDirs);
        testGeneratedDirectorySet.setGradleOutputDirs(Collections.singleton(generatedSourceDirectorySet.getOutputDir()));
        testGeneratedDirectorySet.setOutputDir(generatedSourceDirectorySet.getOutputDir());
        testGeneratedDirectorySet.setCompilerOutputPathInherited(generatedSourceDirectorySet.isCompilerOutputPathInherited());

        externalSourceSet.addSource(ExternalSystemSourceType.TEST_GENERATED, testGeneratedDirectorySet);
      }
    }
  }

  private static void addUnprocessedIdeaSourceDirs(
    @NotNull Map<String, DefaultExternalSourceSet> externalSourceSets, // mutable
    @NotNull SourceSetContainer sourceSets,
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext,
    @NotNull String sourceSetName
  ) {
    Collection<File> ideaSourceDirs = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                      ? sourceSetResolutionContext.ideaTestSourceDirs
                                      : sourceSetResolutionContext.ideaSourceDirs;
    ExternalSystemSourceType sourceType = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                          ? ExternalSystemSourceType.TEST
                                          : ExternalSystemSourceType.SOURCE;

    SourceSet sourceSet = sourceSets.findByName(sourceSetName);
    if (sourceSet == null) return;
    DefaultExternalSourceSet externalSourceSet = externalSourceSets.get(sourceSetName);
    if (externalSourceSet == null) return;
    ExternalSourceDirectorySet sourceDirectorySet = externalSourceSet.getSources().get(sourceType);
    if (sourceDirectorySet == null) return;

    Set<File> sourceDirs = new LinkedHashSet<>(ideaSourceDirs);
    sourceDirs.removeAll(sourceSet.getResources().getSrcDirs());
    sourceDirs.removeAll(sourceSetResolutionContext.ideaGeneratedSourceDirs);
    sourceDirectorySet.getSrcDirs().addAll(sourceDirs);
  }

  private static void addUnprocessedIdeaResourceDirs(
    @NotNull Map<String, DefaultExternalSourceSet> externalSourceSets, // mutable
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext,
    @NotNull String sourceSetName
  ) {
    Collection<File> ideaResourceDirs = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                        ? sourceSetResolutionContext.ideaTestResourceDirs
                                        : sourceSetResolutionContext.ideaResourceDirs;
    ExternalSystemSourceType resourceType = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                            ? ExternalSystemSourceType.TEST_RESOURCE
                                            : ExternalSystemSourceType.RESOURCE;

    DefaultExternalSourceSet externalSourceSet = externalSourceSets.get(sourceSetName);
    if (externalSourceSet == null) return;
    ExternalSourceDirectorySet resourceDirectorySet = externalSourceSet.getSources().get(resourceType);
    if (resourceDirectorySet == null) return;

    resourceDirectorySet.getSrcDirs().addAll(ideaResourceDirs);
  }

  private static void addUnprocessedIdeaGeneratedSourcesDirs(
    @NotNull Map<String, DefaultExternalSourceSet> externalSourceSets, // mutable
    @NotNull GradleSourceSetResolutionContext sourceSetResolutionContext,
    @NotNull String sourceSetName
  ) {
    Collection<File> ideaSourceDirs = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                      ? sourceSetResolutionContext.ideaTestSourceDirs
                                      : sourceSetResolutionContext.ideaSourceDirs;
    ExternalSystemSourceType sourceType = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                          ? ExternalSystemSourceType.TEST
                                          : ExternalSystemSourceType.SOURCE;
    ExternalSystemSourceType generatedSourceType = SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSetName)
                                                   ? ExternalSystemSourceType.TEST_GENERATED
                                                   : ExternalSystemSourceType.SOURCE_GENERATED;

    DefaultExternalSourceSet externalSourceSet = externalSourceSets.get(sourceSetName);
    if (externalSourceSet == null) return;

    Collection<File> generatedSourceDirs = new LinkedHashSet<>(sourceSetResolutionContext.unprocessedIdeaGeneratedSourceDirs);
    generatedSourceDirs.retainAll(ideaSourceDirs);
    if (!generatedSourceDirs.isEmpty()) {
      ExternalSourceDirectorySet generatedSourceDirectorySet = externalSourceSet.getSources().get(generatedSourceType);
      if (generatedSourceDirectorySet != null) {
        generatedSourceDirectorySet.getSrcDirs().addAll(generatedSourceDirs);
      }
      else {
        DefaultExternalSourceDirectorySet generatedDirectorySet = new DefaultExternalSourceDirectorySet();
        generatedDirectorySet.setName("generated " + externalSourceSet.getName());
        generatedDirectorySet.getSrcDirs().addAll(generatedSourceDirs);
        ExternalSourceDirectorySet sourceDirectorySet = externalSourceSet.getSources().get(sourceType);
        if (sourceDirectorySet != null) {
          generatedDirectorySet.setGradleOutputDirs(Collections.singleton(sourceDirectorySet.getOutputDir()));
          generatedDirectorySet.setOutputDir(sourceDirectorySet.getOutputDir());
          generatedDirectorySet.setCompilerOutputPathInherited(sourceDirectorySet.isCompilerOutputPathInherited());
        }
        externalSourceSet.addSource(generatedSourceType, generatedDirectorySet);
      }
    }
  }
}
