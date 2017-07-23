/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.console.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.console.GroovyConsole
import org.jetbrains.plugins.groovy.console.GroovyConsoleStateService
import org.jetbrains.plugins.groovy.console.GroovyConsoleUtil.getTitle
import org.jetbrains.plugins.groovy.console.getApplicableModules
import org.jetbrains.plugins.groovy.util.createSelectModulePopup
import org.jetbrains.plugins.groovy.util.removeUserData

class GrSelectModuleAction(
  private val project: Project,
  private val file: VirtualFile
) : AnAction(message("select.module.title"), message("select.module.description"), AllIcons.Nodes.Module) {

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
    val popup = createSelectModulePopup(project, getApplicableModules(project), ::getTitle, { moduleSelected(it) })
    popup.showUnderneathOf(component)
  }

  private fun moduleSelected(module: Module) {
    if (consoleService.getSelectedModule(file) == module) return
    file.removeUserData(GroovyConsole.GROOVY_CONSOLE)?.stop()
    consoleService.setFileModule(file, module)
    ProjectView.getInstance(project).refresh()
  }
}
