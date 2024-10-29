// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.application
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

class GradleProjectResolverResultHandler(
  private val context: ProjectResolverContext
) {

  fun onResolveProjectInfoStarted() {
    require(!application.isWriteAccessAllowed) {
      "Must not execute inside write action"
    }
    runBlockingCancellable {
      GradleSyncProjectConfigurator.performSyncContributors(context, "RESOLVE_PROJECT_INFO_STARTED") {
        onResolveProjectInfoStarted(context, it)
      }
    }
  }
}