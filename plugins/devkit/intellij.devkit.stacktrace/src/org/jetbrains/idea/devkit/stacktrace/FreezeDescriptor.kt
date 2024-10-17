// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.stacktrace

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.freezeAnalyzer.FreezeAnalyzer
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.unscramble.StacktraceTabContentProvider
import com.intellij.unscramble.AnalyzeStacktraceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitIcons

object FreezeDescriptor {
  suspend fun getFreezeRunDescriptor(text: String, project: Project): RunContentDescriptor? = withContext(Dispatchers.Default) {
    withBackgroundProgress(project, DevKitStackTraceBundle.message("progress.title.freeze.analysis")) {
      FreezeAnalyzer.analyzeFreeze(text)?.let { result ->
        withContext(Dispatchers.EDT) {
          AnalyzeStacktraceUtil.addConsole(
            project, null,
            DevKitStackTraceBundle.message("tab.title.freeze.analyzer"),
            "${result.message}\n${result.additionalMessage ?: ""}\n======= Stack Trace: ========= \n${result.threads.joinToString { it -> it.stackTrace }}",
            DevKitIcons.Freeze, false
          )
        }
      }
    }
  }
}

class FreezeTabContentProvider : StacktraceTabContentProvider {
  override fun createRunTabDescriptor(project: Project, text: String): RunContentDescriptor? {
    return runWithModalProgressBlocking(project, DevKitStackTraceBundle.message("progress.title.freeze.analysis")) {
      FreezeDescriptor.getFreezeRunDescriptor(text, project)
    }
  }
}