// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModel

internal var moduleLoadingActivity: Activity? = null

fun finishModuleLoadingActivity() {
  moduleLoadingActivity?.end()
  moduleLoadingActivity = null
}

fun recordModuleLoadingActivity() {
  if (moduleLoadingActivity == null) {
    moduleLoadingActivity = StartUpMeasurer.startMainActivity("module loading")
  }
}
