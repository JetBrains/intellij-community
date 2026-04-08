// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class RestrictionsLoadingActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    SplitModeApiRestrictionsService.getInstance().scheduleLoadRestrictions()
  }
}
