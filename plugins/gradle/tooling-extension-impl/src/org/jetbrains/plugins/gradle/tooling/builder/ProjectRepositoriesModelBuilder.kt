// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultFileRepositoryModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultProjectRepositoriesModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultRepositoryModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultUrlRepositoryModel
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.model.repositoryModel.ProjectRepositoriesModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.UrlRepositoryModel
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

class ProjectRepositoriesModelBuilder : ModelBuilderService {

  override fun canBuild(modelName: String): Boolean {
    return ProjectRepositoriesModel::class.java.getName() == modelName
  }

  override fun buildAll(modelName: String, project: Project): Any {
    val repositories = getRepositories(project)
      .map { it.toRepositoryModel() }
      .toList()
    return DefaultProjectRepositoriesModel(repositories)
  }

  override fun reportErrorMessage(
    modelName: String,
    project: Project,
    context: ModelBuilderContext,
    exception: Exception,
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.MAVEN_REPOSITORY_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project repositories import failure")
      .withText("Unable to obtain information about configured repositories in the project")
      .withException(exception)
      .reportMessage(project)
  }

  private fun Repository.toRepositoryModel(): RepositoryModel {
    return when (this) {
      is UrlRepository -> DefaultUrlRepositoryModel(name, url, type.toModelRepositoryType())
      is FileRepository -> DefaultFileRepositoryModel(name, files)
      else -> DefaultRepositoryModel(name)
    }
  }

  private fun UrlRepositoryType.toModelRepositoryType(): UrlRepositoryModel.Type {
    return when (this) {
      UrlRepositoryType.MAVEN -> UrlRepositoryModel.Type.MAVEN
      UrlRepositoryType.IVY -> UrlRepositoryModel.Type.IVY
      UrlRepositoryType.OTHER -> UrlRepositoryModel.Type.OTHER
    }
  }

  private sealed interface Repository {
    val name: String
  }

  private class DeclaredRepositoryImpl(override val name: String) : Repository

  private class FileRepository(override val name: String, val files: List<String>) : Repository

  private class UrlRepository(override val name: String, val url: String?, val type: UrlRepositoryType) : Repository

  private enum class UrlRepositoryType {
    MAVEN, IVY, OTHER
  }

  private fun getRepositories(project: Project): List<Repository> {
    val isUrlArtifactRepositoryClassAvailable = GradleVersionUtil.isCurrentGradleAtLeast("5.7")
    @Suppress("UNNECESSARY_SAFE_CALL") // url is nullable; e.g, an ivy repository could be declared with ivyPattern instead of url
    return project.repositories
      .map {
        return@map when {
          it is MavenArtifactRepository -> UrlRepository(it.name, it.url?.toString(), UrlRepositoryType.MAVEN)
          it is IvyArtifactRepository -> UrlRepository(it.name, it.url?.toString(), UrlRepositoryType.IVY)
          isUrlArtifactRepositoryClassAvailable && it is UrlArtifactRepository -> UrlRepository(it.name, it.url?.toString(), UrlRepositoryType.OTHER)
          it is FlatDirectoryArtifactRepository -> FileRepository(it.name, it.dirs.map { file -> file.path })
          else -> DeclaredRepositoryImpl(it.name)
        }
      }
      .toList()
  }
}
