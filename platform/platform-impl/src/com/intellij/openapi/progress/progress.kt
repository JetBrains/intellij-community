// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

inline fun runModalTask(@Nls(capitalization = Nls.Capitalization.Title) title: String,
                        project: Project? = null,
                        cancellable: Boolean = true,
                        crossinline task: (indicator: ProgressIndicator) -> Unit) {
  ProgressManager.getInstance().run(createModalTask(title = title, project = project, cancellable = cancellable, task = task))
}

inline fun createModalTask(@Nls(capitalization = Nls.Capitalization.Title) title: String,
                           project: Project? = null,
                           cancellable: Boolean = true,
                           crossinline task: (indicator: ProgressIndicator) -> Unit): Task.Modal {
  return object : Task.Modal(project, title, cancellable) {
    override fun run(indicator: ProgressIndicator) {
      task(indicator)
    }
  }
}

inline fun runBackgroundableTask(@NlsContexts.ProgressTitle title: String,
                                 project: Project? = null,
                                 cancellable: Boolean = true,
                                 crossinline task: (indicator: ProgressIndicator) -> Unit) {
  ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
    override fun run(indicator: ProgressIndicator) {
      task(indicator)
    }
  })
}