// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications.Provider
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType.EXECUTE_ACTION
import javax.swing.JComponent

class GroovyConsoleEditorDecorator : Provider<JComponent>() {

  companion object {
    private val myKey = Key.create<JComponent>("groovy.console.toolbar")
  }

  override fun getKey(): Key<JComponent> = myKey

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): JComponent? {
    val consoleService = GroovyConsoleStateService.getInstance(project)
    if (!consoleService.isProjectConsole(file)) return null
    val actionGroup = DefaultActionGroup(EXECUTE_ACTION, GrSelectModuleAction(project, file))
    val menu = ActionManager.getInstance().createActionToolbar("GroovyConsole", actionGroup, true)
    return menu.component
  }
}
