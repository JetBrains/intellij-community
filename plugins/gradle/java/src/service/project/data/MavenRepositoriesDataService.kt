// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.data

import com.intellij.externalSystem.MavenRepositoryData
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project

class MavenRepositoriesDataService: AbstractProjectDataService<MavenRepositoryData, Void>() {
  override fun getTargetDataKey(): Key<MavenRepositoryData> = MavenRepositoryData.KEY

  override fun onSuccessImport(imported: MutableCollection<DataNode<MavenRepositoryData>>,
                               projectData: ProjectData?,
                               project: Project,
                               modelsProvider: IdeModelsProvider) {

    val repositoriesConfiguration = RemoteRepositoriesConfiguration.getInstance(project)

    val repositories = hashSetOf<RemoteRepositoryDescription>().apply {
      addAll(repositoriesConfiguration.repositories)
    }

    imported
      .map { it.data }
      .map { RemoteRepositoryDescription(it.name, it.name, it.url) }
      .forEach { repositories.add(it) }

    repositoriesConfiguration.repositories = repositories.toList()

    super.onSuccessImport(imported, projectData, project, modelsProvider)
  }
}