// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer.startActivity

internal var moduleLoadingActivity: Activity? = null

fun finishModuleLoadingActivity() {
  moduleLoadingActivity?.end()
  moduleLoadingActivity = null
}

fun recordModuleLoadingActivity() {
  if (moduleLoadingActivity == null) {
    moduleLoadingActivity = startActivity("moduleLoading")
  }
}
