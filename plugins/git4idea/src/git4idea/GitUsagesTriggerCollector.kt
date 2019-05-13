// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.utils.createData
import com.intellij.openapi.project.Project

private val GROUP = FeatureUsageGroup("statistics.vcs.git.usages",1)
class GitUsagesTriggerCollector  {
  companion object {
    @JvmStatic
    fun reportUsage(project: Project, featureId: String) {
      FeatureUsageLogger.log(GROUP, featureId, createData(project, null))
    }
  }
}