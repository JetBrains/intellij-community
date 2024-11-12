// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle

@Service(Service.Level.PROJECT)
class DependencyResolutionService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
    var reporter: SequentialProgressReporter? = null
        private set

    fun resolveInBackground(block: suspend CoroutineScope.() -> Unit) {
        cs.launch {
          withBackgroundProgress(project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution")) {
            reportSequentialProgress { theReporter ->
              try {
                reporter = theReporter
                block()
              }
              finally {
                reporter = null
              }
            }
          }
        }
    }
}