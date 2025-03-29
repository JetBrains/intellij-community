// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.externalSystem.MavenRepositoryData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.RepositoryModels
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.*


class MavenRepositoriesProjectResolver: AbstractProjectResolverExtension() {

  private val processedRepositoryModels = IdentityHashMap<RepositoryModels, Unit>()

  override fun resolveFinished(projectDataNode: DataNode<ProjectData>) {
    processedRepositoryModels.clear()
  }

  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {
    val repositories = resolverCtx.getRootModel(RepositoryModels::class.java)
    addRepositoriesToProject(ideProject, repositories)

    super.populateProjectExtraModels(gradleProject, ideProject)
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val repositories = resolverCtx.getExtraProject(gradleModule, RepositoryModels::class.java)
    val ideProject = ExternalSystemApiUtil.findParent(ideModule, ProjectKeys.PROJECT)
    ideProject?.let { addRepositoriesToProject(it, repositories) }

    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  override fun getExtraProjectModelClasses(): MutableSet<Class<*>> {
    return Collections.singleton(RepositoryModels::class.java)
  }

  private fun addRepositoriesToProject(ideProject: DataNode<ProjectData>,
                                       repositories: RepositoryModels?) {
    if (repositories != null && !processedRepositoryModels.containsKey(repositories)) {
      val knownRepositories = ExternalSystemApiUtil.getChildren(ideProject, MavenRepositoryData.KEY)
        .asSequence()
        .map { it.data }
        .toSet()

      repositories.repositories.asSequence()
        .map { MavenRepositoryData(GradleConstants.SYSTEM_ID, it.name, it.url) }
        .filter { !knownRepositories.contains(it) }
        .distinct()
        .forEach { ideProject.addChild(DataNode(MavenRepositoryData.KEY, it, ideProject)) }
      processedRepositoryModels[repositories] = Unit
    }
  }
}