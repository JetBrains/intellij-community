// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.openapi.project.Project

class GitUsagesTriggerCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String = "statistics.vcs.git.usages"

  companion object {
    @JvmStatic
    fun reportUsage(project: Project, featureId: String) {
      FUSProjectUsageTrigger.getInstance(project).trigger(GitUsagesTriggerCollector::class.java, featureId)
    }
  }
}