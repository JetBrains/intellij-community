// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.console.GroovyConsoleUtil.getDisplayGroovyVersion
import org.jetbrains.plugins.groovy.console.GroovyConsoleUtil.getTitle
import org.jetbrains.plugins.groovy.util.createSelectModulePopup

class GrSelectModuleAction(
  private val project: Project,
  private val file: VirtualFile
) : AnAction(
  message("select.module.action.text"),
  message("select.module.action.description"),
  AllIcons.Nodes.Module
) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private val consoleService by lazy {
    GroovyConsoleStateService.getInstance(project)
  }

  override fun update(e: AnActionEvent) {
    val module = consoleService.getSelectedModule(file)
    if (module == null || module.isDisposed) return
    e.presentation.text = getTitle(module)
  }

  override fun displayTextInToolbar(): Boolean = true

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent?.component ?: return
    val popup = createSelectModulePopup(project, getApplicableModules(project), ::getDisplayGroovyVersion, ::moduleSelected)
    popup.showUnderneathOf(component)
  }

  private fun moduleSelected(module: Module) {
    if (consoleService.getSelectedModule(file) == module) return
    GroovyConsole.stopConsole(file)
    consoleService.setFileModule(file, module)
  }
}
