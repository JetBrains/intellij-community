package org.jetbrains.completion.full.line.settings

import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.completion.full.line.settings.ui.MLServerCompletionConfigurable
import org.jetbrains.completion.full.line.settings.ui.VerifyCloudConfigurable
import javax.swing.JComponent

class FullSettingsDialog : DialogWrapper(true) {
  init {
    init()
    title = "Full Line Settings"
  }

  override fun createCenterPanel(): JComponent {
    val a = MLServerCompletionConfigurable()
    return a.createPanel()
  }
}

class VerifyCloudDialog : DialogWrapper(true) {
  init {
    init()
    title = "Verify Full Line Cloud"
  }

  override fun createCenterPanel(): JComponent {
    val a = VerifyCloudConfigurable()
    return a.createPanel()
  }
}
