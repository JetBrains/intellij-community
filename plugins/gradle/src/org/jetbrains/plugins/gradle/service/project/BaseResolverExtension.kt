// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.Pair
import com.intellij.util.net.getCurrentSettingsAsJvmProperties
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import kotlin.Int.Companion.MAX_VALUE

@Order(MAX_VALUE)
internal class BaseResolverExtension : GradleProjectResolverExtension {
  override fun setProjectResolverContext(projectResolverContext: ProjectResolverContext) {}
  override fun getNext(): GradleProjectResolverExtension? = null
  override fun setNext(projectResolverExtension: GradleProjectResolverExtension) {
    throw AssertionError("should be the last extension in the chain")
  }
  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {}
  override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? = null
  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleCompileOutputSettings(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {}
  override fun populateModuleTasks(
    gradleModule: IdeaModule,
    ideModule: DataNode<ModuleData>,
    ideProject: DataNode<ProjectData>,
  ): Collection<TaskData> = emptyList()
  override fun getToolingExtensionsClasses(): Set<Class<*>> = linkedSetOf()

  override fun getExtraJvmArgs(): List<Pair<String, String>> {
    val extraJvmArgs = mutableListOf<Pair<String, String>>()
    getCurrentSettingsAsJvmProperties().forEach { (key, value) -> extraJvmArgs.add(Pair(key, value)) }
    return extraJvmArgs
  }

  override fun getUserFriendlyError(
    buildEnvironment: BuildEnvironment?,
    error: Throwable,
    projectPath: String,
    buildFilePath: String?,
  ): ExternalSystemException = BaseProjectImportErrorHandler().getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath)
}
