// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener

/** Publishes fetched IntelliJ Platform models after both successful and failed Gradle syncs. */
internal class IntelliJPlatformGradleSyncListener : GradleSyncListener {

  override fun onModelFetchCompleted(context: ProjectResolverContext) {
    importProjectModels(context)
  }

  override fun onModelFetchFailed(context: ProjectResolverContext, exception: Throwable) {
    importProjectModels(context)
  }

  @Suppress("SSBasedInspection") // Gradle's tooling model exposes the project directory as File.
  private fun importProjectModels(context: ProjectResolverContext) {
    val provider = IntelliJPlatformGradleModelProvider.getInstance(context.project) as? IntelliJPlatformGradleModelProviderImpl ?: return
    val modelsByModulePath = context.allBuilds
      .asSequence()
      .flatMap { it.projects.asSequence() }
      .mapNotNull { gradleProject ->
        context.getProjectModel(gradleProject, IntelliJPlatformGradleModel::class.java)?.let {
          gradleProject.projectDirectory.toPath().toString() to it
        }
      }
      .toMap()

    val importedModels = provider.importProjectModels(context.externalProjectPath, modelsByModulePath)
    if (importedModels > 0) {
      LOG.debug("Imported IntelliJ Platform Gradle data for $importedModels module(s)")
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IntelliJPlatformGradleSyncListener::class.java)
  }
}

/** Converts fetched tooling models and publishes modules with a usable product-release catalog. */
internal fun IntelliJPlatformGradleModelProviderImpl.importProjectModels(
  linkedProjectPath: String,
  modelsByModulePath: Map<String, IntelliJPlatformGradleModel>,
): Int {
  val dataByModulePath = modelsByModulePath
    .mapValues { (_, model) -> model.toIntelliJPlatformGradleData() }
    .filterValues { it.productReleases.isNotEmpty() }
  if (dataByModulePath.isNotEmpty()) {
    replaceProjectData(linkedProjectPath, dataByModulePath)
  }
  return dataByModulePath.size
}
