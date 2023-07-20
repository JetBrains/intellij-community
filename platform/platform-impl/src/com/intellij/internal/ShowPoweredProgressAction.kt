// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.Ref
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.TimerUtil
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.pow

internal class ShowPoweredProgressAction : AnAction("Show Powered Progress") {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val builder = DialogBuilder()
    builder.addAction(object : AbstractAction("Restart Indicators") {
      override fun actionPerformed(e: ActionEvent?) {

      }
    })
    builder.addCancelAction()
    val dialogPanel = JPanel()
    dialogPanel.layout = BoxLayout(dialogPanel, BoxLayout.Y_AXIS)
    val progresses = mutableListOf<JProgressBar>()
    val powers = mutableListOf<Ref<Double>>()
    val values = mutableListOf<Ref<Int>>()

    val MAX = 60000

    for (i in 0..2) {
      val progress = JProgressBar()
      progresses.add(progress)
      progress.isIndeterminate = false
      progress.minimum = 0
      progress.maximum = MAX
      dialogPanel.add(Box.createRigidArea(JBDimension(0, 10)))
      dialogPanel.add(progress)
      dialogPanel.add(Box.createRigidArea(JBDimension(0, 10)))
      val power: Ref<Double> = Ref.create((i + 1).toDouble())
      val jTextField = JTextField(power.get().toString())
      jTextField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          jTextField.text.toDoubleOrNull()?.let {
            power.set(it)
          }
        }
      })
      values.add(Ref.create(0))
      powers.add(power)
      val labeledComponent = LabeledComponent.create(jTextField, "power:")
      dialogPanel.add(labeledComponent)
      dialogPanel.add(Box.createRigidArea(JBDimension(0, 10)))
    }

    val timer = TimerUtil.createNamedTimer("progresses", 1) {
      for ((index, progress) in progresses.withIndex()) {
        values[index].set((values[index].get() + 1) % MAX)
        progress.value = (MAX * (values[index].get().toDouble() / MAX.toDouble()).pow(powers[index].get())).toInt()
      }
    }
    timer.isRepeats = true
    timer.start()
    builder.setCenterPanel(dialogPanel)
    builder.addDisposable {
      timer.stop()
    }
    builder.show()
  }
}