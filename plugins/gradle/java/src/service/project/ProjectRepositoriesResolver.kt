// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.repository.FileRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.ProjectRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.UrlRepositoryData
import com.intellij.gradle.toolingExtension.model.repositoryModel.FileRepositoryModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.ProjectRepositoriesModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.UrlRepositoryModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.*

class ProjectRepositoriesResolver: AbstractProjectResolverExtension() {

  private val processedRepositories: Set<RepositoryModel> = Collections.newSetFromMap(IdentityHashMap())

  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {
    resolverCtx.getRootModel(ProjectRepositoriesModel::class.java)?.register(ideProject)
    super.populateProjectExtraModels(gradleProject, ideProject)
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    resolverCtx.getExtraProject(gradleModule, ProjectRepositoriesModel::class.java)
      ?.run {
        val ideProject = ExternalSystemApiUtil.findParent(ideModule, ProjectKeys.PROJECT)
        if (ideProject != null) {
          register(ideProject)
        }
      }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  override fun getExtraProjectModelClasses(): Set<Class<*>> {
    return setOf(ProjectRepositoriesModel::class.java)
  }

  private fun ProjectRepositoriesModel.register(ideProject: DataNode<ProjectData>) {
    val registeredRepositories = ExternalSystemApiUtil.getChildren(ideProject, ProjectRepositoryData.KEY)
      .map { it.data }
      .toSet()
    for (repository in repositories.distinct()) {
      if (processedRepositories.contains(repository)) {
        continue
      }
      val dataNode = repository.toDataNode()
      if (registeredRepositories.contains(dataNode)) {
        continue
      }
      ideProject.addChild(DataNode(ProjectRepositoryData.KEY, dataNode, ideProject))
    }
  }

  private fun RepositoryModel.toDataNode(): ProjectRepositoryData {
    return when (this) {
      is FileRepositoryModel -> FileRepositoryData(GradleConstants.SYSTEM_ID, name, files)
      is UrlRepositoryModel -> UrlRepositoryData(GradleConstants.SYSTEM_ID, name, url, type.asDataNodeType())
      else -> ProjectRepositoryData(GradleConstants.SYSTEM_ID, name)
    }
  }

  private fun UrlRepositoryModel.Type.asDataNodeType(): UrlRepositoryData.Type {
    return when (this) {
      UrlRepositoryModel.Type.MAVEN -> UrlRepositoryData.Type.MAVEN
      UrlRepositoryModel.Type.IVY -> UrlRepositoryData.Type.IVY
      UrlRepositoryModel.Type.OTHER -> UrlRepositoryData.Type.OTHER
    }
  }
}
