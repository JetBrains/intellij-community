// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.impl.CancellableTaskCancellation
import com.intellij.openapi.progress.impl.NonCancellableTaskCancellation
import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.TitlePanel
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.*
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.*
import org.jetbrains.annotations.Contract
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*

internal class ProgressDialogUI : Disposable {
  val panel: JPanel = JPanel()
  private val myTitlePanel = TitlePanel()
  private val textLabel = JLabel(" ")
  private val detailsLabel = JBLabel("")
  val progressBar: JProgressBar = JProgressBar()
  val cancelButton: JButton = JButton()
  val backgroundButton: JButton = JButton()

  init {
    myTitlePanel.setActive(true)
    detailsLabel.componentStyle = UIUtil.ComponentStyle.REGULAR
    if (SystemInfoRt.isMac) {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, detailsLabel)
    }
    detailsLabel.foreground = UIUtil.getContextHelpForeground()
    progressBar.putClientProperty("html.disable", java.lang.Boolean.FALSE)
    progressBar.maximum = 100
    cancelButton.text = CommonBundle.getCancelButtonText()
    DialogUtil.registerMnemonic(cancelButton, '&')
    backgroundButton.text = CommonBundle.message("button.background")
    DialogUtil.registerMnemonic(backgroundButton, '&')

    val progressPanel = JPanel()
    progressPanel.layout = GridLayoutManager(3, 2, JBInsets.emptyInsets(), -1, -1, false, false)
    progressPanel.preferredSize = Dimension(if (SystemInfoRt.isMac) 350 else JBUIScale.scale(450), -1)
    progressPanel.add(textLabel, gridConstraints(row = 0, minimumSize = Dimension(0, -1)))
    progressPanel.add(JLabel(" "), gridConstraints(
      row = 0, column = 1, anchor = ANCHOR_WEST, fill = FILL_NONE, HSizePolicy = SIZEPOLICY_FIXED,
    ))
    progressPanel.add(progressBar, gridConstraints(row = 1, colSpan = 2))
    progressPanel.add(detailsLabel, gridConstraints(
      row = 2, anchor = ANCHOR_NORTHWEST, minimumSize = Dimension(0, -1),
    ))
    progressPanel.add(JLabel(" "), gridConstraints(
      row = 2, column = 1, anchor = ANCHOR_WEST, fill = FILL_NONE, HSizePolicy = SIZEPOLICY_FIXED,
    ))

    val buttonPanel = JPanel()
    buttonPanel.layout = GridLayoutManager(2, 1, JBInsets.emptyInsets(), -1, -1, false, false)
    buttonPanel.add(cancelButton, gridConstraints(row = 0, HSizePolicy = SIZE_POLICY_DEFAULT))
    buttonPanel.add(backgroundButton, gridConstraints(row = 1, HSizePolicy = SIZE_POLICY_DEFAULT))

    val progressAndButtonPanel = JPanel()
    progressAndButtonPanel.layout = GridLayoutManager(1, 2, JBInsets(6, 10, 10, 10), -1, -1, false, false)
    progressAndButtonPanel.isOpaque = false
    progressAndButtonPanel.add(progressPanel, gridConstraints(row = 0, VSizePolicy = SIZEPOLICY_CAN_GROW))
    progressAndButtonPanel.add(buttonPanel, gridConstraints(
      row = 0, column = 1, HSizePolicy = SIZEPOLICY_CAN_SHRINK, VSizePolicy = SIZEPOLICY_CAN_GROW
    ))

    panel.layout = GridLayoutManager(2, 1, JBInsets.emptyInsets(), -1, -1, false, false)
    panel.add(myTitlePanel, gridConstraints(row = 0))
    panel.add(progressAndButtonPanel, gridConstraints(
      row = 1, fill = FILL_BOTH, HSizePolicy = SIZE_POLICY_DEFAULT, VSizePolicy = SIZE_POLICY_DEFAULT
    ))
    if (ExperimentalUI.isNewUI()) {
      panel.background = JBUI.CurrentTheme.Popup.BACKGROUND
      progressPanel.isOpaque = false
      progressBar.isOpaque = false
      buttonPanel.isOpaque = false
      cancelButton.isOpaque = false
      backgroundButton.isOpaque = false
    }

    val moveListener = object : WindowMoveListener(myTitlePanel) {
      override fun getView(component: Component): Component {
        return SwingUtilities.getAncestorOfClass(DialogWrapperDialog::class.java, component)
      }
    }
    myTitlePanel.addMouseListener(moveListener)
    myTitlePanel.addMouseMotionListener(moveListener)
  }

  override fun dispose() {
    UIUtil.disposeProgress(progressBar)
    UIUtil.dispose(myTitlePanel)
    UIUtil.dispose(backgroundButton)
    UIUtil.dispose(cancelButton)
  }

  fun initCancellation(cancellation: TaskCancellation, cancelAction: () -> Unit) {
    when (cancellation) {
      is NonCancellableTaskCancellation -> {
        cancelButton.isVisible = false
      }
      is CancellableTaskCancellation -> {
        cancellation.buttonText?.let {
          cancelButton.text = it
        }
        cancellation.tooltipText?.let {
          cancelButton.toolTipText = it
        }
        val buttonListener = ActionListener {
          cancelAction()
        }
        cancelButton.addActionListener(buttonListener)
        cancelButton.registerKeyboardAction(
          buttonListener,
          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
      }
    }
  }

  fun updateTitle(title: @ProgressTitle String?) {
    EDT.assertIsEdt()
    myTitlePanel.setText(title?.takeIf(String::isNotEmpty) ?: " ")
  }

  fun updateProgress(state: ProgressState) {
    EDT.assertIsEdt()
    textLabel.text = fitTextToLabel(state.text, textLabel)
    detailsLabel.text = fitTextToLabel(state.details, detailsLabel)
    if (progressBar.isShowing) {
      val fraction = state.fraction
      if (fraction < 0.0) {
        progressBar.isIndeterminate = true
      }
      else {
        progressBar.isIndeterminate = false
        progressBar.value = (fraction * 100).toInt()
      }
    }
  }
}

@Contract(pure = true)
private fun fitTextToLabel(fullText: String?, label: JLabel): String {
  if (fullText.isNullOrEmpty()) return " "
  if (fullText.startsWith("<html>") && fullText.endsWith("</html>")) {
    return fullText // Don't truncate if the text is HTML
  }
  var newFullText = StringUtil.last(fullText, 500, true).toString() // avoid super long strings
  while (label.getFontMetrics(label.font).stringWidth(newFullText) > label.width) {
    val sep = newFullText.indexOf(File.separatorChar, 4)
    if (sep < 0) return newFullText
    newFullText = "..." + newFullText.substring(sep)
  }
  return newFullText
}

private const val SIZE_POLICY_DEFAULT = SIZEPOLICY_CAN_GROW or SIZEPOLICY_CAN_SHRINK

private fun gridConstraints(
  row: Int,
  column: Int = 0,
  colSpan: Int = 1,
  anchor: Int = ANCHOR_CENTER,
  fill: Int = FILL_HORIZONTAL,
  HSizePolicy: Int = SIZE_POLICY_DEFAULT or SIZEPOLICY_WANT_GROW,
  VSizePolicy: Int = SIZEPOLICY_FIXED,
  minimumSize: Dimension? = null,
): GridConstraints {
  return GridConstraints(row, column, 1, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, null, null)
}
