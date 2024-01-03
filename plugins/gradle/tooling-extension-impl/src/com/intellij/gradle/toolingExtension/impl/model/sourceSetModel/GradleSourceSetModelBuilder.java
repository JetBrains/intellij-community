// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.collectionUtil.GradleCollectionVisitor;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil.getTaskArchiveFile;
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.dynamicCheckInstanceOf;

@ApiStatus.Internal
public class GradleSourceSetModelBuilder extends AbstractModelBuilderService {

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
    sourceSetModel.setSourceSets(GradleSourceSetGroovyHelper.getSourceSets(project, context));
    sourceSetModel.setAdditionalArtifacts(collectNonSourceSetArtifacts(project, context));

    GradleSourceSetCache.getInstance(context).setSourceSetModel(project, sourceSetModel);

    return sourceSetModel;
  }

  @Override
  public void reportErrorMessage(@NotNull String modelName,
                                 @NotNull Project project,
                                 @NotNull ModelBuilderContext context,
                                 @NotNull Exception exception) {
    GradleSourceSetCache.getInstance(context).markSourceSetModelAsError(project);

    context.getMessageReporter().createMessage()
      .withGroup(Messages.SOURCE_SET_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Source set model building failure")
      .withException(exception)
      .reportMessage(project);
  }

  @NotNull
  private static List<File> collectProjectTaskArtifacts(@NotNull Project project, @NotNull ModelBuilderContext context) {
    List<File> taskArtifacts = new ArrayList<>();
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      public void visit(Jar element) {
        File archiveFile = getTaskArchiveFile(element);
        if (archiveFile != null) {
          taskArtifacts.add(archiveFile);
        }
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
          .withText("Artifact files collecting for project Jar task was finished. " +
                    "Resolution for Jar task " + element.getPath() + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project);
      }
    });
    return new ArrayList<>(taskArtifacts);
  }

  @NotNull
  private static List<File> collectNonSourceSetArtifacts(@NotNull Project project, @NotNull ModelBuilderContext context) {
    List<File> additionalArtifacts = new ArrayList<>();
    GradleCollectionVisitor.accept(project.getTasks().withType(Jar.class), new GradleCollectionVisitor<Jar>() {

      @Override
      public void visit(Jar element) {
        File archiveFile = getTaskArchiveFile(element);
        if (archiveFile != null) {
          if (isJarDescendant(element) || containsPotentialClasspathElements(element, project)) {
            additionalArtifacts.add(archiveFile);
          }
        }
      }

      @Override
      public void onFailure(Jar element, @NotNull Exception exception) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Cannot resolve artifact file for the project Jar task: " + element.getPath())
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(Jar element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_NON_SOURCE_SET_ARTIFACT_GROUP)
          .withTitle("Jar task configuration error")
          .withText("Artifact files collecting for project Jar task was finished. " +
                    "Resolution for Jar task " + element.getPath() + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project);
      }
    });
    return additionalArtifacts;
  }

  @NotNull
  private static Map<String, Set<File>> collectProjectConfigurationArtifacts(@NotNull Project project,
                                                                             @NotNull ModelBuilderContext context) {
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
          .withText("Cannot resolve artifact files for project configuration" + element)
          .withKind(Message.Kind.WARNING)
          .withException(exception)
          .reportMessage(project);
      }

      @Override
      public void visitAfterAccept(Configuration element) {
        context.getMessageReporter().createMessage()
          .withGroup(Messages.SOURCE_SET_MODEL_SKIPPED_PROJECT_CONFIGURATION_ARTIFACT_GROUP)
          .withTitle("Project configuration error")
          .withText("Artifact files collecting for project configuration was finished. " +
                    "Resolution for configuration " + element + " will be skipped.")
          .withKind(Message.Kind.INTERNAL)
          .withStackTrace()
          .reportMessage(project);
      }
    });
    return configurationArtifacts;
  }

  static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> map) {
    ExternalSourceSet mainSourceSet = map.get(SourceSet.MAIN_SOURCE_SET_NAME);
    cleanupSharedSourceFolders(map, mainSourceSet, null);
    cleanupSharedSourceFolders(map, map.get(SourceSet.TEST_SOURCE_SET_NAME), mainSourceSet);
  }

  static void cleanupSharedSourceFolders(Map<String, ExternalSourceSet> result, ExternalSourceSet sourceSet, ExternalSourceSet toIgnore) {
    if (sourceSet == null) return;

    for (Map.Entry<String, ExternalSourceSet> sourceSetEntry : result.entrySet()) {
      if (sourceSetEntry.getValue() == sourceSet) continue;
      if (sourceSetEntry.getValue() == toIgnore) continue;
      ExternalSourceSet customSourceSet = sourceSetEntry.getValue();
      for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
        ExternalSourceDirectorySet customSourceDirectorySet = customSourceSet.getSources().get(sourceType);
        if (customSourceDirectorySet != null) {
          for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sourceDirEntry : sourceSet.getSources()
            .entrySet()) {
            customSourceDirectorySet.getSrcDirs().removeAll(sourceDirEntry.getValue().getSrcDirs());
          }
        }
      }
    }
  }

  private static boolean isJarDescendant(Jar task) {
    Class<?> type = GradleNegotiationUtil.getTaskIdentityType(task);
    return type != null && !type.equals(Jar.class);
  }

  /**
   * Check the configured archive task inputs for specific files.
   * <p>
   * We want to check, if IDEA is interested in keeping this archive task output in dependencies list
   * <br>
   * This may happen if <ul>
   * <li>there are some class files
   * <li>there are directories, not known to be outputs of source sets (modules)
   *
   * @param archiveTask task to check
   * @param project     project with source sets, potentially contributing to this task.
   * @return true if this jar should be kept in IDEA modules' dependencies' lists.
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

  static boolean containsAllSourceSetOutput(@NotNull AbstractArchiveTask archiveTask, @NotNull SourceSet sourceSet) {
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

  private static Set<Object> getArchiveTaskSourcePaths(AbstractArchiveTask archiveTask) throws RuntimeException {
    try {
      final Method mainSpecGetter = AbstractCopyTask.class.getDeclaredMethod("getMainSpec");
      mainSpecGetter.setAccessible(true);
      Object mainSpec = mainSpecGetter.invoke(archiveTask);
      Method getSourcePaths = mainSpec.getClass().getMethod("getSourcePaths");

      @SuppressWarnings("unchecked") Set<Object> sourcePaths = (Set<Object>)getSourcePaths.invoke(mainSpec);
      if (sourcePaths != null) {
        return sourcePaths;
      }
      else {
        return Collections.emptySet();
      }
    }
    catch (Throwable t) {
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
   * @param object
   * @return true if object is safe to resolve using {@link Project#files(Object...)}
   * @see GradleSourceSetModelBuilder#tryUnpackPresentProvider
   */
  private static boolean isSafeToResolve(Object param, Project project) {
    Object object = tryUnpackPresentProvider(param, project);
    boolean isDirectoryOrRegularFile = dynamicCheckInstanceOf(object, "org.gradle.api.file.Directory", "org.gradle.api.file.RegularFile");

    return object instanceof CharSequence ||
           object instanceof File ||
           object instanceof Path ||
           isDirectoryOrRegularFile ||
           object instanceof SourceSetOutput;
  }

  /**
   * Some Gradle {@link org.gradle.api.provider.Provider} implementations can not be resolved during sync,
   * causing {@link org.gradle.api.InvalidUserCodeException} and {@link org.gradle.api.InvalidUserDataException}.
   * Also some {@link org.gradle.api.provider.Provider} attempts to resolve dynamic
   * configurations, witch results in resolving a configuration without write lock on the project.
   *
   * @return provided value or current if value isn't present or cannot be evaluated
   */
  private static Object tryUnpackPresentProvider(Object object, Project project) {
    if (!dynamicCheckInstanceOf(object, "org.gradle.api.provider.Provider")) {
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
    catch (InvocationTargetException|NoSuchMethodException | IllegalAccessException exception) {
      Throwable cause = exception.getCause();
      boolean isCodeException = dynamicCheckInstanceOf(cause, "org.gradle.api.InvalidUserCodeException");
      boolean isDataException = dynamicCheckInstanceOf(cause, "org.gradle.api.InvalidUserDataException");
      if (isCodeException || isDataException) {
        return object;
      }
      String msg = cause.getMessage();
      String className = cause.getClass().getCanonicalName();
      project.getLogger().info("Unable to resolve task source path: {} ({})", msg, className);
      return object;
    }
  }
}
