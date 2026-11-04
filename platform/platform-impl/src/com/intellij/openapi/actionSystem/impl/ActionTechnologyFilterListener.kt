// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.extensions.TechnologyFilterListener
import com.intellij.openapi.project.Project

/** Updates the toolbars and menus when the actions that apply to a project change. */
internal class ActionTechnologyFilterListener : TechnologyFilterListener {
  override fun applicabilityChanged(project: Project) {
    ActivityTracker.getInstance().inc()
  }
}
