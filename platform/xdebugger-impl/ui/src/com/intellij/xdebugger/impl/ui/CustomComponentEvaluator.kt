// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.AppUIUtil.invokeOnEdt
import com.intellij.ui.EditorTextField
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedTextPopupUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.CardLayout
import java.awt.Font
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Experimental
abstract class CustomComponentEvaluator(name: String) : XFullValueEvaluator() {

  open fun createComponent(fullValue: String?) : JComponent? = null

  open fun show(event: MouseEvent, project: Project, editor: Editor?) {
    val panel = JPanel(CardLayout())
    val callback = EvaluationCallback(panel, this, project)
    showValuePopup(event, project, editor, panel, callback::setObsolete)
    this.startEvaluation(callback) /*to make it really cancellable*/
  }

  protected open fun showValuePopup(event: MouseEvent,
                                    project: Project,
                                    editor: Editor?,
                                    component: JComponent,
                                    cancelCallback: Runnable?) {
    VisualizedTextPopupUtil.showValuePopup(event, project, editor, component, cancelCallback)
  }

  protected class EvaluationCallback(private val myPanel: JComponent,
                                     private val myEvaluator: CustomComponentEvaluator,
                                     private val myProject: Project) : XFullValueEvaluationCallback {
    private val myObsolete = AtomicBoolean(false)

    override fun evaluated(fullValue: String, font: Font?) {
      invokeOnEdt {
        try {
          myPanel.removeAll()
          var component = myEvaluator.createComponent(fullValue)
          if (component == null) {
            val textArea: EditorTextField = DebuggerUIUtil.createTextViewer(fullValue, myProject)
            if (font != null) {
              textArea.setFont(font)
            }
            component = textArea
          }
          myPanel.add(component)
          myPanel.revalidate()
          myPanel.repaint()
        }
        catch (e: Exception) {
          errorOccurred(e.toString())
        }
      }
    }

    override fun errorOccurred(errorMessage: String) {
      invokeOnEdt {
        myPanel.removeAll()
        val textArea: EditorTextField = DebuggerUIUtil.createTextViewer(errorMessage, myProject)
        textArea.setForeground(XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.fgColor)
        myPanel.add(textArea)
      }
    }

    fun setObsolete() {
      myObsolete.set(true)
    }

    override fun isObsolete(): Boolean {
      return myObsolete.get()
    }
  }
}