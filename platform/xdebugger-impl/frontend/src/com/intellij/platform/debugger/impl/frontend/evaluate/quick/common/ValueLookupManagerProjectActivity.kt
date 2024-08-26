// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.coroutineScope

private class ValueLookupManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    coroutineScope {
      val cs = this@coroutineScope
      subscribeForDebuggingStart(cs) {
        ValueLookupManager.getInstance(project).startListening()
      }

      subscribeForValueHintHideRequest(cs) {
        ValueLookupManager.getInstance(project).hideHint()
      }
    }
  }
}