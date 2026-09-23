// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.build.events.MessageEvent
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
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
    if (!context.hasModulesWithModel(IntelliJPlatformGradleModel::class.java)) return
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

    reportOutdatedPluginVersion(context, modelsByModulePath.values)
  }

  private fun reportOutdatedPluginVersion(context: ProjectResolverContext, models: Collection<IntelliJPlatformGradleModel>) {
    if (isOutdatedPluginVersionInspectionDisabled(context.project)) return
    val (currentVersion, latestVersion) = findOutdatedIntelliJPlatformGradlePluginVersion(models) ?: return

    val issue = OutdatedIntelliJPlatformGradlePluginVersionIssue(context.externalProjectPath, currentVersion, latestVersion)
    issue.addOpenInspectionSettingsQuickFix(OUTDATED_PLUGIN_VERSION_INSPECTION)
    context.report(MessageEvent.Kind.INFO, issue)
  }

  private fun isOutdatedPluginVersionInspectionDisabled(project: Project): Boolean {
    val inspectionKey = HighlightDisplayKey.find(OUTDATED_PLUGIN_VERSION_INSPECTION) ?: return true
    val inspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
    return !inspectionProfile.isToolEnabled(inspectionKey)
  }

  companion object {
    private val LOG = Logger.getInstance(IntelliJPlatformGradleSyncListener::class.java)
    private const val OUTDATED_PLUGIN_VERSION_INSPECTION = "OutdatedIntelliJPlatformGradlePluginVersion"
  }
}

/** Converts fetched tooling models and publishes modules with a usable product-release catalog. */
internal fun IntelliJPlatformGradleModelProviderImpl.importProjectModels(
  linkedProjectPath: String,
  modelsByModulePath: Map<String, IntelliJPlatformGradleModel>,
): Int {
  val dataByModulePath = modelsByModulePath
    .mapValues { (_, model) -> model.toIntelliJPlatformGradleData() }
    .filterValues { it.hasUsableData() }
  if (dataByModulePath.isNotEmpty()) {
    replaceProjectData(linkedProjectPath, dataByModulePath)
  }
  return dataByModulePath.size
}

/** Returns the current and the latest plugin version of the first model that uses an outdated plugin. */
internal fun findOutdatedIntelliJPlatformGradlePluginVersion(
  models: Collection<IntelliJPlatformGradleModel>,
): Pair<Version, Version>? = models.asSequence()
  .mapNotNull { model ->
    val currentVersion = Version.parseVersion(model.currentPluginVersion) ?: return@mapNotNull null
    val latestVersion = Version.parseVersion(model.latestPluginVersion) ?: return@mapNotNull null
    currentVersion to latestVersion
  }
  .firstOrNull { (currentVersion, latestVersion) -> currentVersion < latestVersion }
