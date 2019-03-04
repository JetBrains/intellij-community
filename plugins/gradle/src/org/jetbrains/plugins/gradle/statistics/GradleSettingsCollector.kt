// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.beans.ConvertUsagesUtil
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getEnumUsage
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkType
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsService
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "build.gradle.state"

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val gradleSettings = GradleSettings.getInstance(project)
    val projectsSettings = gradleSettings.linkedProjectsSettings
    if (projectsSettings.isEmpty()) {
      return emptySet()
    }
    val usages = mutableSetOf<UsageDescriptor>()

    // global settings
    usages.add(getBooleanUsage("offlineWork", gradleSettings.isOfflineWork))
    usages.add(getBooleanUsage("showSelectiveImportDialogOnInitialImport", gradleSettings.showSelectiveImportDialogOnInitialImport()))

    val settingsService = GradleSettingsService.getInstance(project)
    // project settings
    for (setting in gradleSettings.linkedProjectsSettings) {
      val projectPath = setting.externalProjectPath
      usages.add(getYesNoUsage("isCompositeBuilds", setting.compositeBuild != null))
      usages.add(getEnumUsage("distributionType", setting.distributionType))
      usages.add(getEnumUsage("storeProjectFilesExternally", setting.storeProjectFilesExternally))
      usages.add(getBooleanUsage("disableWrapperSourceDistributionNotification", setting.isDisableWrapperSourceDistributionNotification))
      usages.add(getBooleanUsage("createModulePerSourceSet", setting.isResolveModulePerSourceSet))
      usages.add(UsageDescriptor("gradleJvm." + ConvertUsagesUtil.escapeDescriptorName(getGradleJvmName(setting, project) ?: "empty"), 1))
      val gradleVersion = setting.resolveGradleVersion()
      if(gradleVersion.isSnapshot) {
        usages.add(UsageDescriptor("gradleVersion." + gradleVersion.baseVersion.version + ".SNAPSHOT", 1))
      } else {
        usages.add(UsageDescriptor("gradleVersion." + gradleVersion.version, 1))
      }
      usages.add(getBooleanUsage("delegateBuildRun", settingsService.isDelegatedBuildEnabled(projectPath)))
      usages.add(getEnumUsage("preferredTestRunner", settingsService.getTestRunner(projectPath)))
    }
    return usages
  }

  private fun getGradleJvmName(setting: GradleProjectSettings, project: Project): String? {
    val jdk = ExternalSystemJdkUtil.getJdk(project, setting.gradleJvm)
    val sdkType = jdk?.sdkType
    return if (sdkType is SdkType) {
      sdkType.suggestSdkName(null, jdk.homePath)
    }
    else setting.gradleJvm
  }

  private fun getYesNoUsage(key: String, value: Boolean): UsageDescriptor {
    return UsageDescriptor(key + if (value) ".yes" else ".no", 1)
  }
}
