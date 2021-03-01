// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ExecutionException
import javax.swing.JOptionPane
import javax.swing.KeyStroke

object PerformActionUtil {
  private fun getInputEvent(actionName: String): InputEvent {
    val shortcuts: Array<Shortcut> = KeymapManager.getInstance().activeKeymap.getShortcuts(actionName)
    var keyStroke: KeyStroke? = null
    for (each in shortcuts) {
      if (each is KeyboardShortcut) {
        keyStroke = each.firstKeyStroke
        break
      }
    }
    return if (keyStroke != null) {
      KeyEvent(JOptionPane.getRootFrame(),
               KeyEvent.KEY_PRESSED,
               System.currentTimeMillis(),
               keyStroke.modifiers,
               keyStroke.keyCode,
               keyStroke.keyChar,
               KeyEvent.KEY_LOCATION_STANDARD)
    }
    else {
      MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1)
    }
  }

  @Throws(InterruptedException::class, ExecutionException::class)
  fun performAction(actionName: String, editor: Editor, project: Project, withWriteAccess: Boolean = true, callback: () -> Unit = {}) {
    val am: ActionManager = ActionManager.getInstance()
    val targetAction = am.getAction(actionName)
    val inputEvent = getInputEvent(actionName)
    val runAction = {
      am.tryToExecute(targetAction, inputEvent, editor.contentComponent, null, true).doWhenDone(callback)
    }
    ApplicationManager.getApplication().invokeLater {
      if (withWriteAccess) {
        WriteCommandAction.runWriteCommandAction(project) {
          runAction()
        }
      }
      else runAction()
    }
  }
}