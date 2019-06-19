// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.execution.console.ConsoleHistoryController
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil

class GrNewConsoleAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getModule(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val module = getModule(e)
    if (project == null || module == null) return

    val contentFile = ConsoleHistoryController.getContentFile(
      GroovyConsoleRootType.getInstance(),
      GroovyConsoleRootType.CONTENT_ID,
      ScratchFileService.Option.create_new_always
    ) ?: return
    GroovyConsole.createConsole(project, contentFile, module)
    FileEditorManager.getInstance(project).openFile(contentFile, true)
  }

  protected fun getModule(e: AnActionEvent): Module? {
    val project = e.project ?: return null

    var module = e.getData(LangDataKeys.MODULE)
    if (!GroovyFacetUtil.isSuitableModule(module)) {
      module = getAnyApplicableModule(project)
    }
    return module
  }
}