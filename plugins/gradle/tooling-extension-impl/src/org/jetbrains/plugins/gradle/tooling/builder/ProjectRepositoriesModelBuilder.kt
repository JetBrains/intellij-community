// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import org.gradle.api.Project
import com.intellij.gradle.toolingExtension.model.repositoryModel.ProjectRepositoriesModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel
import com.intellij.gradle.toolingExtension.model.repositoryModel.UrlRepositoryModel
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultFileRepositoryModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultProjectRepositoriesModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultRepositoryModel
import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultUrlRepositoryModel
import org.jetbrains.plugins.gradle.tooling.util.*

class ProjectRepositoriesModelBuilder : ModelBuilderService {

  override fun canBuild(modelName: String): Boolean {
    return ProjectRepositoriesModel::class.java.getName() == modelName
  }

  override fun buildAll(modelName: String, project: Project): Any {
    val repositories = getDeclaredRepositories(project)
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

  private fun DeclaredRepository.toRepositoryModel(): RepositoryModel {
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
}
