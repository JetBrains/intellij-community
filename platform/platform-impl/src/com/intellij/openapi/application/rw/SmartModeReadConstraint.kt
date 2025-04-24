// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

internal class SmartModeReadConstraint(private val project: Project) : ReadConstraint {

  override fun isSatisfied(): Boolean {
    return DumbService.getInstance(project).canRunSmart()
  }

  override suspend fun awaitConstraint() {
    val service = blockingContext {
      DumbService.getInstance(project)
    }
    yieldUntilRun {
      service.runWhenSmart(it)
    }
  }
}
