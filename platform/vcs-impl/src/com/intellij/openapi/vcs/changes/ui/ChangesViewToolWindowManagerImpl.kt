// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager

internal class ChangesViewToolWindowManagerImpl(private val project: Project) : ChangesViewToolWindowManager {
  override fun shouldBeAvailable(): Boolean = ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
}