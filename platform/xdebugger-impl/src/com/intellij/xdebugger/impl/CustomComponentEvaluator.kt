package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.AppUIUtil.invokeOnEdt
import com.intellij.ui.EditorTextField
import com.intellij.ui.ScreenUtil
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants
import org.jetbrains.annotations.ApiStatus
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.*
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

  private fun showValuePopup(event: MouseEvent,
                             project: Project,
                             editor: Editor?,
                             component: JComponent,
                             cancelCallback: Runnable?) {
    var size = DimensionService.getInstance().getSize(DebuggerUIUtil.FULL_VALUE_POPUP_DIMENSION_KEY, project)
    if (size == null) {
      val frameSize = Objects.requireNonNull(WindowManager.getInstance().getFrame(project))!!.size
      size = Dimension(frameSize.width / 2, frameSize.height / 2)
    }
    component.preferredSize = size
    val popup = DebuggerUIUtil.createValuePopup(project, component, cancelCallback)
    if (editor == null) {
      val bounds = Rectangle(event.locationOnScreen, size)
      ScreenUtil.fitToScreenVertical(bounds, 5, 5, true)
      if (size.width != bounds.width || size.height != bounds.height) {
        size = bounds.size
        component.preferredSize = size
      }
      popup.showInScreenCoordinates(event.component, bounds.location)
    }
    else {
      popup.showInBestPositionFor(editor)
    }
  }

  protected class EvaluationCallback(private val myPanel: JComponent,
                                     private val myEvaluator: CustomComponentEvaluator,
                                     private val myProject: Project) : XFullValueEvaluationCallback {
    private val myObsolete = AtomicBoolean(false)
    override fun evaluated(fullValue: String) {
      evaluated(fullValue, null)
    }

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