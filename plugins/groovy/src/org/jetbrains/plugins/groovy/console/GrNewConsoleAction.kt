// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

class GrNewConsoleAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let(::hasAnyApplicableModule) ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val module = e.getData(LangDataKeys.MODULE)?.takeIf(::isApplicableModule)
    val contentFile = ConsoleHistoryController.getContentFile(
      GroovyConsoleRootType.getInstance(),
      GroovyConsoleRootType.CONTENT_ID,
      ScratchFileService.Option.create_new_always
    ) ?: return
    GroovyConsoleStateService.getInstance(project).setFileModule(contentFile, module)
    FileEditorManager.getInstance(project).openFile(contentFile, true)
  }
}
