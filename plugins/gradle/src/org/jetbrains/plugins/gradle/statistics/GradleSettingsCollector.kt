// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.model.ConfigurationDataImpl
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.statistics.ExternalSystemUsageFields
import com.intellij.openapi.externalSystem.statistics.ExternalSystemUsageFields.JRE_TYPE_FIELD
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.settings.TestRunner
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.nio.file.Paths

internal class GradleSettingsCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val gradleSettings = GradleSettings.getInstance(project)
    val projectsSettings = gradleSettings.linkedProjectsSettings
    if (projectsSettings.isEmpty()) {
      return emptySet()
    }
    val usages = mutableSetOf<MetricEvent>()

    // to have a total users base line to calculate pertentages of settings
    usages.add(HAS_GRADLE_PROJECT.metric(true))

    // global settings
    collectGradleSettingsMetrics(usages, gradleSettings)

    // project settings
    for (projectSettings in gradleSettings.linkedProjectsSettings) {
      collectGradleProjectSettingsMetrics(usages, project, projectSettings)
    }

    // gradle.properties
    for (projectSettings in gradleSettings.linkedProjectsSettings) {
      collectGradlePropertiesMetrics(usages, project, projectSettings.externalProjectPath)
    }

    return usages
  }

  private fun collectGradleSettingsMetrics(usages: MutableSet<MetricEvent>, gradleSettings: GradleSettings) {
    usages.add(OFFLINE_WORK.metric(gradleSettings.isOfflineWork))
    usages.add(HAS_CUSTOM_SERVICE_DIRECTORY_PATH.metric(!gradleSettings.serviceDirectoryPath.isNullOrBlank()))
    usages.add(HAS_CUSTOM_GRADLE_VM_OPTIONS.metric(!gradleSettings.gradleVmOptions.isNullOrBlank()))
    usages.add(SHOW_SELECTIVE_IMPORT_DIALOG_ON_INITIAL_IMPORT.metric(gradleSettings.showSelectiveImportDialogOnInitialImport()))
    usages.add(STORE_PROJECT_FILES_EXTERNALLY.metric(gradleSettings.storeProjectFilesExternally))
    usages.add(GRADLE_DOWNLOAD_DEPENDENCY_SOURCES.metric(GradleSystemSettings.getInstance().isDownloadSources))
    usages.add(GRADLE_PARALLEL_MODEL_FETCH.metric(gradleSettings.isParallelModelFetch))
  }

  private fun collectGradleProjectSettingsMetrics(usages: MutableSet<MetricEvent>, project: Project, setting: GradleProjectSettings) {
    val projectPath = setting.externalProjectPath
    usages.add(IS_USE_QUALIFIED_MODULE_NAMES.metric(setting.isUseQualifiedModuleNames))
    usages.add(CREATE_MODULE_PER_SOURCE_SET.metric(setting.isResolveModulePerSourceSet))
    val distributionType = setting.distributionType
    if (distributionType != null) {
      usages.add(DISTRIBUTION_TYPE.metric(distributionType))
    }

    usages.add(IS_COMPOSITE_BUILDS.metric(setting.compositeBuild != null))
    usages.add(DISABLE_WRAPPER_SOURCE_DISTRIBUTION_NOTIFICATION.metric(setting.isDisableWrapperSourceDistributionNotification))

    usages.add(GRADLE_JVM_TYPE.metric(ExternalSystemUsageFields.getJreType(setting.gradleJvm)))
    usages.add(GRADLE_JVM_VERSION.metric(ExternalSystemUsageFields.getJreVersion(project, setting.gradleJvm)))

    val gradleVersion = setting.resolveGradleVersion()
    if (gradleVersion.isSnapshot) {
      usages.add(GRADLE_VERSION.metric(anonymizeGradleVersion(gradleVersion.baseVersion) + ".SNAPSHOT"))
    }
    else {
      usages.add(GRADLE_VERSION.metric(anonymizeGradleVersion(gradleVersion)))
    }

    usages.add(DELEGATE_BUILD_RUN.metric(GradleProjectSettings.isDelegatedBuildEnabled(project, projectPath)))
    usages.add(PREFERRED_TEST_RUNNER.metric(GradleProjectSettings.getTestRunner(project, projectPath)))

    val hasNonEmptyIntellijConfig = ProjectDataManager
                                      .getInstance()
                                      .getExternalProjectData(project, GradleConstants.SYSTEM_ID, projectPath)
                                      ?.externalProjectStructure
                                      ?.let { dataNode ->
                                        ExternalSystemApiUtil.findFirstRecursively(dataNode) { it.key == ProjectKeys.CONFIGURATION }
                                      }
                                      ?.let { it.data as? ConfigurationDataImpl }
                                      ?.let { it.jsonString.length > 2 } ?: false

    usages.add(IDEA_SPECIFIC_CONFIGURATION_USED.metric(hasNonEmptyIntellijConfig))
    usages.add(GRADLE_DAEMON_JVM_CRITERIA_DEFINED.metric(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(Path.of(projectPath), gradleVersion)))
  }

  private fun collectGradlePropertiesMetrics(usages: MutableSet<MetricEvent>, project: Project, externalProjectPath: String) {
    val gradleProperties = GradlePropertiesFile.getProperties(project, Paths.get(externalProjectPath))

    val parallel = gradleProperties.parallel
    if (parallel != null) {
      usages.add(GRADLE_PROPERTY_PARALLEL.metric(parallel.value))
    }

    val isolatedProjects = gradleProperties.isolatedProjects
    if (isolatedProjects != null) {
      usages.add(GRADLE_ISOLATED_PROJECTS_PARALLEL.metric(isolatedProjects.value))
    }
  }

  private fun anonymizeGradleVersion(version: GradleVersion): String {
    return Version.parseVersion(version.version)?.toCompactString() ?: "unknown"
  }

  private val GROUP = EventLogGroup("build.gradle.state", 9)
  private val HAS_GRADLE_PROJECT = GROUP.registerEvent("hasGradleProject", EventFields.Enabled)
  private val OFFLINE_WORK = GROUP.registerEvent("offlineWork", EventFields.Enabled)
  private val HAS_CUSTOM_SERVICE_DIRECTORY_PATH = GROUP.registerEvent("hasCustomServiceDirectoryPath", EventFields.Enabled)
  private val HAS_CUSTOM_GRADLE_VM_OPTIONS = GROUP.registerEvent("hasCustomGradleVmOptions", EventFields.Enabled)
  private val SHOW_SELECTIVE_IMPORT_DIALOG_ON_INITIAL_IMPORT = GROUP.registerEvent("showSelectiveImportDialogOnInitialImport",
                                                                                   EventFields.Enabled)
  private val STORE_PROJECT_FILES_EXTERNALLY = GROUP.registerEvent("storeProjectFilesExternally", EventFields.Enabled)
  private val IS_USE_QUALIFIED_MODULE_NAMES = GROUP.registerEvent("isUseQualifiedModuleNames", EventFields.Enabled)
  private val CREATE_MODULE_PER_SOURCE_SET = GROUP.registerEvent("createModulePerSourceSet", EventFields.Enabled)
  private val IS_COMPOSITE_BUILDS = GROUP.registerEvent("isCompositeBuilds", EventFields.Enabled)
  private val DISABLE_WRAPPER_SOURCE_DISTRIBUTION_NOTIFICATION = GROUP.registerEvent("disableWrapperSourceDistributionNotification",
                                                                                     EventFields.Enabled)
  private val DELEGATE_BUILD_RUN = GROUP.registerEvent("delegateBuildRun", EventFields.Enabled)
  private val IDEA_SPECIFIC_CONFIGURATION_USED = GROUP.registerEvent("ideaSpecificConfigurationUsed", EventFields.Enabled)

  private val DISTRIBUTION_TYPE = GROUP.registerEvent("distributionType",
                                                      EventFields.Enum("value", DistributionType::class.java) { it.name.lowercase() })
  private val VERSION_FIELD = EventFields.StringValidatedByRegexpReference("value", "version")
  private val GRADLE_VERSION = GROUP.registerEvent("gradleVersion", VERSION_FIELD)
  private val PREFERRED_TEST_RUNNER = GROUP.registerEvent("preferredTestRunner",
                                                          EventFields.Enum("value", TestRunner::class.java) { it.name.lowercase() })
  private val GRADLE_JVM_TYPE = GROUP.registerEvent("gradleJvmType", JRE_TYPE_FIELD)
  private val GRADLE_JVM_VERSION = GROUP.registerEvent("gradleJvmVersion", VERSION_FIELD)
  private val GRADLE_DAEMON_JVM_CRITERIA_DEFINED = GROUP.registerEvent("gradleDaemonJvmCriteriaDefined", EventFields.Enabled)
  private val GRADLE_DOWNLOAD_DEPENDENCY_SOURCES = GROUP.registerEvent("gradleDownloadDependencySources", EventFields.Enabled)
  private val GRADLE_PARALLEL_MODEL_FETCH = GROUP.registerEvent("gradleParallelModelFetch", EventFields.Enabled)

  private val GRADLE_PROPERTY_PARALLEL = GROUP.registerEvent("org.gradle.parallel", EventFields.Enabled)
  private val GRADLE_ISOLATED_PROJECTS_PARALLEL = GROUP.registerEvent("org.gradle.isolated-projects", EventFields.Enabled)
}
