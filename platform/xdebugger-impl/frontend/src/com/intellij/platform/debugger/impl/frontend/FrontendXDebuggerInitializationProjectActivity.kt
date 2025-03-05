// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class FrontendXDebuggerInitializationProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // initialize the debugger manager to start listening for backend state
    FrontendXDebuggerManager.getInstance(project)
  }
}