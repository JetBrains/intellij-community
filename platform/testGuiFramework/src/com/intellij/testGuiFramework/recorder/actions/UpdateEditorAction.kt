package com.intellij.testGuiFramework.recorder.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.recorder.Writer
import com.intellij.testGuiFramework.recorder.components.GuiRecorderComponent
import com.intellij.testGuiFramework.recorder.ui.Notifier

/**
 * @author Sergey Karashevich
 */
class UpdateEditorAction : AnAction(null, "Update GUI Script Editor (get recorded from buffer)", AllIcons.Actions.NextOccurence) {

  override fun actionPerformed(actionEvent: AnActionEvent?) {
    val editor = GuiRecorderComponent.getFrame()!!.getEditor()
    ApplicationManager.getApplication().runWriteAction { editor.document.setText(getGuiScriptBuffer()) }
    Notifier.updateStatus("GUI script updated")
  }

  fun getGuiScriptBuffer() = Writer.getScript()

}