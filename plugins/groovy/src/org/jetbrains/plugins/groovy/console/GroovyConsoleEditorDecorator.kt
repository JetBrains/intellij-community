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
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications.Provider
import org.jetbrains.plugins.groovy.console.GroovyConsoleRootType.EXECUTE_ACTION
import org.jetbrains.plugins.groovy.console.actions.GrSelectModuleAction
import javax.swing.JComponent

class GroovyConsoleEditorDecorator(private val project: Project) : Provider<JComponent>() {

  companion object {
    private val myKey = Key.create<JComponent>("groovy.console.toolbar")
  }

  override fun getKey(): Key<JComponent> = myKey

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): JComponent? {
    val consoleService = GroovyConsoleStateService.getInstance(project)
    if (!consoleService.isProjectConsole(file)) return null
    val actionGroup = DefaultActionGroup(EXECUTE_ACTION, GrSelectModuleAction(project, file))
    val menu = ActionManager.getInstance().createActionToolbar("GroovyConsole", actionGroup, true)
    return EditorHeaderComponent().apply {
      add(menu.component)
    }
  }
}