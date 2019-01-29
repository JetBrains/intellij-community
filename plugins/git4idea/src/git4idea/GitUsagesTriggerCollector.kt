// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project

class GitUsagesTriggerCollector  {
  companion object {
    @JvmStatic
    fun reportUsage(project: Project, featureId: String) {
      FUCounterUsageLogger.getInstance().logEvent(project, "vcs.git.usages", featureId)
    }
  }
}