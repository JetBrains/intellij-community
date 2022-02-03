// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

internal class SmartModeReadConstraint(private val project: Project) : ReadConstraint {

  override fun isSatisfied(): Boolean {
    return !DumbService.isDumb(project)
  }

  override fun schedule(runnable: Runnable) {
    DumbService.getInstance(project).runWhenSmart(runnable)
  }
}
