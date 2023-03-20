// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.project.Project

interface ToolWindowStripeManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ToolWindowStripeManager = project.getService(ToolWindowStripeManager::class.java)
  }

  fun allowToShowOnStripe(id: String, isDefaultLayout: Boolean, isNewUi: Boolean): Boolean
}