// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.beans.ConvertUsagesUtil
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getEnumUsage
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings

class GradleSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "statistics.build.gradle.state"

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

    // project settings
    for (setting in gradleSettings.linkedProjectsSettings) {
      usages.add(getBooleanUsage("useCompositeBuilds", setting.compositeBuild != null))
      usages.add(getEnumUsage("distributionType", setting.distributionType))
      usages.add(getEnumUsage("storeProjectFilesExternally", setting.storeProjectFilesExternally))
      usages.add(getBooleanUsage("disableWrapperSourceDistributionNotification", setting.isDisableWrapperSourceDistributionNotification))
      usages.add(getBooleanUsage("createModulePerSourceSet", setting.isResolveModulePerSourceSet))
      usages.add(UsageDescriptor("gradleJvm." + ConvertUsagesUtil.escapeDescriptorName(setting.gradleJvm ?: "empty"), 1))
      usages.add(UsageDescriptor("gradleVersion." + setting.resolveGradleVersion().version, 1))
    }

    // running settings
    val runningSettings = GradleSystemRunningSettings.getInstance()
    usages.add(getBooleanUsage("delegateBuildRun", runningSettings.isUseGradleAwareMake))
    usages.add(getEnumUsage("preferredTestRunner", runningSettings.preferredTestRunner))
    return usages
  }
}
