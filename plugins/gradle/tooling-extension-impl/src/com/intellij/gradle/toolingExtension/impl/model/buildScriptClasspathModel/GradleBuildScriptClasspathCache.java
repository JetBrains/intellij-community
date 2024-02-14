// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import org.gradle.api.Project;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class GradleBuildScriptClasspathCache {

  private final @NotNull ModelBuilderContext context;
  private final @NotNull ConcurrentMap<ProjectIdentifier, GradleBuildScriptClasspathModel> models;

  private GradleBuildScriptClasspathCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.models = new ConcurrentHashMap<>();
  }

  public @NotNull GradleBuildScriptClasspathModel getBuildScriptClasspathModel(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    GradleBuildScriptClasspathModel model = models.get(projectIdentifier);
    if (model == null) {
      String projectDisplayName = project.getDisplayName();
      context.getMessageReporter().createMessage()
        .withGroup(Messages.BUILDSCRIPT_CLASSPATH_MODEL_CACHE_GET_GROUP)
        .withTitle("Gradle build script class-path model aren't found")
        .withText("Gradle build script class-path model for " + projectDisplayName + " wasn't collected.")
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
      return new DefaultGradleBuildScriptClasspathModel();
    }
    return model;
  }

  public void setBuildScriptClasspathModel(@NotNull Project project, @NotNull GradleBuildScriptClasspathModel model) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    GradleBuildScriptClasspathModel oldModel = models.put(projectIdentifier, model);
    if (oldModel != null) {
      String projectDisplayName = project.getDisplayName();
      context.getMessageReporter().createMessage()
        .withGroup(Messages.BUILDSCRIPT_CLASSPATH_MODEL_CACHE_SET_GROUP)
        .withTitle("Gradle build script class-path model redefinition")
        .withText("Gradle build script class-path model for " + projectDisplayName + " was already collected.")
        .withInternal().withStackTrace()
        .withKind(Message.Kind.ERROR)
        .reportMessage(project);
    }
  }

  /**
   * Marks that a build script class-path model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markBuildScriptClasspathModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    models.put(projectIdentifier, new DefaultGradleBuildScriptClasspathModel());
  }

  private static final @NotNull ModelBuilderContext.DataProvider<GradleBuildScriptClasspathCache> INSTANCE_PROVIDER =
    GradleBuildScriptClasspathCache::new;

  public static @NotNull GradleBuildScriptClasspathCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
