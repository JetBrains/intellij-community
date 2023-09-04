// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.projectModel


import com.intellij.gradle.toolingExtension.impl.sourceSetModel.SourceSetModelBuilder
import com.intellij.gradle.toolingExtension.impl.taskModel.GradleTaskCache
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.DefaultExternalProject
import org.jetbrains.plugins.gradle.model.DefaultExternalTask
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalProjectPreview
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.builder.ProjectExtensionsDataBuilderImpl

/**
 * @author Vladislav.Soroka
 */
@CompileStatic
@ApiStatus.Internal
class ExternalProjectBuilderImpl extends AbstractModelBuilderService {

  private static final GradleVersion gradleBaseVersion = GradleVersion.current().baseVersion
  private static final boolean is44OrBetter = gradleBaseVersion >= GradleVersion.version("4.4")

  @Override
  boolean canBuild(String modelName) {
    return ExternalProject.name == modelName || ExternalProjectPreview.name == modelName
  }

  @Nullable
  @Override
  Object buildAll(@NotNull final String modelName, @NotNull final Project project, @NotNull ModelBuilderContext context) {
    if (System.properties.'idea.internal.failEsModelBuilder' as boolean) {
      throw new RuntimeException("Boom!")
    }
    return buildExternalProject(project, context)
  }

  @NotNull
  private static DefaultExternalProject buildExternalProject(
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    DefaultExternalProject externalProject = new DefaultExternalProject()
    externalProject.externalSystemId = "GRADLE"
    externalProject.name = project.name
    def qName = ":" == project.path ? project.name : project.path
    externalProject.QName = qName
    final IdeaPlugin ideaPlugin = project.getPlugins().findPlugin(IdeaPlugin.class)
    def ideaPluginModule = ideaPlugin?.model?.module
    def ideaModuleName = ideaPluginModule?.name ?: project.name

    /*
    Right now, there is no public API available to get this identityPath
    Agreement with Gradle: We can use ProjectInternal for now.
    This identity path will get a public tooling API which will replace the cast.
    Until then, this API will be kept stable as agreement between Gradle and JetBrains

    Note: identityPath was introduced with Gradle 3.3:
    https://github.com/gradle/gradle/commit/2c009b27b97c1564344f3cc93258ce5a0e18a03f
     */
    def projectIdentityPath = GradleVersion.current() >= GradleVersion.version("3.3") ?
                              (project as ProjectInternal).identityPath.path : project.path

    externalProject.id = projectIdentityPath == ":" ? ideaModuleName : projectIdentityPath
    externalProject.path = project.path
    externalProject.identityPath = projectIdentityPath
    externalProject.version = wrap(project.version)
    externalProject.description = project.description
    externalProject.buildDir = project.buildDir
    externalProject.buildFile = project.buildFile
    externalProject.group = wrap(project.group)
    externalProject.projectDir = project.projectDir
    externalProject.sourceSets = SourceSetModelBuilder.getSourceSets(project, context)
    externalProject.tasks = getTasks(project, context)
    externalProject.sourceCompatibility = SourceSetModelBuilder.getSourceCompatibility(project)
    externalProject.targetCompatibility = SourceSetModelBuilder.getTargetCompatibility(project)

    SourceSetModelBuilder.addArtifactsData(project, externalProject)

    return externalProject
  }

  static Map<String, DefaultExternalTask> getTasks(@NotNull Project project, @NotNull ModelBuilderContext context) {
    def result = [:] as Map<String, DefaultExternalTask>

    def taskCache = GradleTaskCache.getInstance(context)
    for (Task task in taskCache.getTasks(project)) {
      DefaultExternalTask externalTask = result.get(task.name)
      if (externalTask == null) {
        externalTask = new DefaultExternalTask()
        externalTask.name = task.name
        externalTask.QName = task.name
        externalTask.description = task.description
        externalTask.group = task.group ?: "other"
        def ext = task.getExtensions()?.extraProperties
        def isInternalTest = ext?.has("idea.internal.test") && Boolean.valueOf(ext.get("idea.internal.test").toString())
        def isEffectiveTest = "check" == task.name && "verification" == task.group
        def isJvmTest = task instanceof Test
        def isAbstractTest = is44OrBetter && task instanceof AbstractTestTask
        externalTask.test = isJvmTest || isAbstractTest || isInternalTest || isEffectiveTest
        externalTask.jvmTest = isJvmTest || isAbstractTest
        externalTask.type = ProjectExtensionsDataBuilderImpl.getType(task)
        result.put(externalTask.name, externalTask)
      }

      def projectTaskPath = (project.path == ':' ? ':' : project.path + ':') + task.name
      if (projectTaskPath == task.path) {
        externalTask.QName = task.path
      }
    }
    result
  }

  private static String wrap(Object o) {
    return o instanceof CharSequence ? o.toString() : ""
  }

  @NotNull
  @Override
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Project resolve errors"
    ).withDescription("Unable to resolve additional project configuration.")
  }
}
