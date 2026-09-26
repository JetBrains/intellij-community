// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.testFramework.common.mock.notImplemented
import com.intellij.testFramework.common.mock.requireImplemented
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.ProjectModel
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.assertNotNull

/**
 * A test [ProjectResolverContext] for [org.jetbrains.plugins.gradle.service.project.JavaGradleProjectResolver] unit tests.
 */

class GradleTestProjectResolverContext private constructor() {

  val models = mutableMapOf<Class<*>, Map<out ProjectModel, *>>()
  var buildEnvironment: BuildEnvironment? = null
  var listener: ExternalSystemTaskNotificationListener? = null

  @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
  private class ProjectResolverContextImpl(
    override val project: Project,
    private val configuration: GradleTestProjectResolverContext,
  ) : ProjectResolverContext by notImplemented<ProjectResolverContext>() {

    override val projectPath: String = project.basePath!!

    override val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project)
    override fun getExternalSystemTaskId(): ExternalSystemTaskId = taskId

    override val buildEnvironment: BuildEnvironment
      get() = configuration.requireImplemented(GradleTestProjectResolverContext::buildEnvironment)
    override val listener: ExternalSystemTaskNotificationListener
      get() = configuration.requireImplemented(GradleTestProjectResolverContext::listener)

    override fun getBuildSrcGroup(): String? = null
    override fun isResolveModulePerSourceSet(): Boolean = true

    override fun <T> getProjectModel(projectModel: ProjectModel, modelClass: Class<T>): T {
      val modelsForClass = configuration.models[modelClass]
      assertNotNull(modelsForClass) {
        "Unexpected request for model class\n" +
        " modelClass=$modelClass"
      }
      val model = modelsForClass[projectModel]
      assertNotNull(model) {
        "Unexpected request for Gradle project\n" +
        " modelClass=$modelClass\n" +
        " projectModel=$projectModel"
      }
      return modelClass.cast(model)
    }

    private val userDataHolder = UserDataHolderBase()
    override fun <T> getUserData(key: Key<T>): T? = userDataHolder.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = userDataHolder.putUserData(key, value)
    override fun <T : Any> putUserDataIfAbsent(key: Key<T>, value: T): T = userDataHolder.putUserDataIfAbsent(key, value)
    override fun <T> replace(key: Key<T>, oldValue: T?, newValue: T?): Boolean = userDataHolder.replace(key, oldValue, newValue)
  }

  companion object {

    fun projectResolverContext(project: Project, configure: (GradleTestProjectResolverContext) -> Unit = {}): ProjectResolverContext {
      val configuration = GradleTestProjectResolverContext()
      configure(configuration)
      return ProjectResolverContextImpl(project, configuration)
    }

    fun projectResolverContext(
      project: Project,
      ideaProject: IdeaProject,
      externalProjects: List<ExternalProject>,
    ): ProjectResolverContext {
      val externalProjectModels = ideaProject.modules.zip(externalProjects).toMap()
      val sourceSetModels = externalProjectModels.mapValues { it.value.sourceSetModel }
      return projectResolverContext(project) {
        it.models[ExternalProject::class.java] = externalProjectModels
        it.models[GradleSourceSetModel::class.java] = sourceSetModels
      }
    }
  }
}