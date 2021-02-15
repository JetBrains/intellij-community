// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.fileChooser.actions.RefreshFileChooserAction
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickButtonWhenEnabled
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.actionButtonByClass
import com.intellij.testGuiFramework.impl.waitUntilFound
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.util.waitFor
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JDialog
import javax.swing.JTextField

class FileChooserDialogFixture constructor(robot: Robot, fileChooserDialog: JDialog) : JDialogFixture(robot, fileChooserDialog) {

  fun setPath(pluginPath: String) {
    step("specify file path") {
      waitFor {
        val pluginPathTextField: JTextField =
          waitUntilFound(target(), JTextField::class.java, Timeouts.defaultTimeout) { it.isEnabled && it.isShowing }
        clickRefresh()
        JTextComponentFixture(robot(), pluginPathTextField).deleteText().enterText(pluginPath)
        pluginPathTextField.text == pluginPath
      }
    }
  }

  fun clickRefresh() = actionButtonByClass(RefreshFileChooserAction::class.java.simpleName).click()

  fun clickOk() = findAndClickButtonWhenEnabled(this, "OK")
}
