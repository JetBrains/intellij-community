// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType.EXECUTE_ACTION
import java.util.function.Function
import javax.swing.JComponent

class GroovyConsoleEditorDecorator : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val consoleService = GroovyConsoleStateService.getInstance(project)
    if (!consoleService.isProjectConsole(file)) return null

    return Function {
      val actionGroup = DefaultActionGroup(EXECUTE_ACTION, GrSelectModuleAction(project, file))
       ActionManager.getInstance().createActionToolbar("GroovyConsole", actionGroup, true).component
    }
  }
}
