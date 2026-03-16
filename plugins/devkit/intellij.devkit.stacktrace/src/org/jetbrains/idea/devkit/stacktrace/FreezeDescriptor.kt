// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.stacktrace

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.freezeAnalyzer.FreezeAnalyzer
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.unscramble.StacktraceTabContentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun getFreezeRunDescriptor(text: String, project: Project): RunContentDescriptor? = withContext(Dispatchers.Default) {
  FreezeAnalyzer.analyzeFreeze(text)?.let { result ->
    withContext(Dispatchers.EDT) {
      AnalyzeStacktraceUtil.addConsole(
        project, null,
        DevKitStackTraceBundle.message("tab.title.freeze.analyzer"),
        "${result.message}\n${result.additionalMessage ?: ""}\n======= Stack Trace: ========= \n${result.threads.joinToString { it -> it.stackTrace }}",
        AllIcons.Debugger.Freeze, false
      )
    }
  }
}

internal class FreezeTabContentProvider : StacktraceTabContentProvider {
  override suspend fun createRunTabDescriptor(project: Project, text: String): RunContentDescriptor? {
    return getFreezeRunDescriptor(text, project)
  }
}