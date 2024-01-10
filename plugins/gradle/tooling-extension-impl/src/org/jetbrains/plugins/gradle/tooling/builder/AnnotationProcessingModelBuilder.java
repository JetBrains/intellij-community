// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleIdeaPluginUtil;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingConfig;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingModel;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.AnnotationProcessingConfigImpl;
import org.jetbrains.plugins.gradle.tooling.internal.AnnotationProcessingModelImpl;

import java.io.File;
import java.util.*;

public class AnnotationProcessingModelBuilder extends AbstractModelBuilderService {

  private static final boolean isAtLeastGradle3_4 = GradleVersionUtil.isCurrentGradleAtLeast("3.4");
  private static final boolean isAtLeastGradle4_3 = isAtLeastGradle3_4 && GradleVersionUtil.isCurrentGradleAtLeast("4.3");
  private static final boolean isAtLeastGradle4_5 = isAtLeastGradle4_3 && GradleVersionUtil.isCurrentGradleAtLeast("4.5");
  private static final boolean isAtLeastGradle6_3 = isAtLeastGradle4_3 && GradleVersionUtil.isCurrentGradleAtLeast("6.3");

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    if (!canBuild(modelName)) {
      return null;
    }

    if (!isAtLeastGradle3_4) {
      return null;
    }

    final SourceSetContainer container = JavaPluginUtil.getSourceSetContainer(project);
    if (container == null) {
      return null;
    }

    Map<String, AnnotationProcessingConfig> sourceSetConfigs = new HashMap<>();

    IdeaModule ideaModule = null;
    IdeaPlugin plugin = project.getPlugins().findPlugin(IdeaPlugin.class);
    if (plugin != null) {
      ideaModule = plugin.getModel().getModule();
    }

    for (final SourceSet sourceSet : container) {
      String compileTaskName = sourceSet.getCompileJavaTaskName();
      Task compileTask = project.getTasks().findByName(compileTaskName);
      if (compileTask instanceof JavaCompile) {
        CompileOptions options = ((JavaCompile)compileTask).getOptions();
        FileCollection path = options.getAnnotationProcessorPath();
        if (path != null) {
          final Set<File> files = path.getFiles();
          if (!files.isEmpty()) {
            List<String> annotationProcessorArgs = new ArrayList<>();
            List<String> args = isAtLeastGradle4_5 ? options.getAllCompilerArgs() : options.getCompilerArgs();
            for (String arg : args) {
              if (arg.startsWith("-A")) {
                annotationProcessorArgs.add(arg);
              }
            }

            File generatedSourcesDirectory;
            if (isAtLeastGradle6_3) {
              generatedSourcesDirectory = options.getGeneratedSourceOutputDirectory().get().getAsFile();
            } else if (isAtLeastGradle4_3) {
              generatedSourcesDirectory = options.getAnnotationProcessorGeneratedSourcesDirectory();
            } else {
              generatedSourcesDirectory = null;
            }

            String output = generatedSourcesDirectory != null ? generatedSourcesDirectory.getAbsolutePath() : null;
            sourceSetConfigs.put(sourceSet.getName(), new AnnotationProcessingConfigImpl(files, annotationProcessorArgs, output, isTestSourceSet(sourceSet, ideaModule)));
          }
        }
      }
    }

    if (!sourceSetConfigs.isEmpty()) {
      return new AnnotationProcessingModelImpl(sourceSetConfigs);
    }

    return null;
  }

  private static boolean isTestSourceSet(@NotNull SourceSet sourceSet, @Nullable IdeaModule module) {
    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      return true;
    }
    if (module != null) {
      Set<File> testSourceDirectories = GradleIdeaPluginUtil.getTestSourceDirectories(module);
      Set<File> sourceDirectories = sourceSet.getAllJava().getSrcDirs();
      return testSourceDirectories.containsAll(sourceDirectories);
    }
    return false;
  }

  @Override
  public boolean canBuild(String modelName) {
    return AnnotationProcessingModel.class.getName().equals(modelName);
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.ANNOTATION_PROCESSOR_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project annotation processor import failure")
      .withText("Unable to create annotation processors model")
      .withException(exception)
      .reportMessage(project);
  }
}
