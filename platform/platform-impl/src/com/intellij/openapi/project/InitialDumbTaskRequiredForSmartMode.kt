// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbServiceImpl.REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY


class InitialDumbTaskRequiredForSmartMode(private val project: Project) : DumbModeTask() {
  override fun performInDumbMode(indicator: ProgressIndicator) {
    val activities = REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY.extensionList
    for (activity in activities) {
      ProgressManager.checkCanceled()
      activity.runActivity(project)
    }
  }
}