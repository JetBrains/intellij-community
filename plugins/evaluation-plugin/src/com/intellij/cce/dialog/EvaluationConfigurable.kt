package com.intellij.cce.dialog

import com.intellij.cce.workspace.Config
import javax.swing.JPanel

interface EvaluationConfigurable {
  fun createPanel(previousState: Config): JPanel
  fun configure(builder: Config.Builder)
}
