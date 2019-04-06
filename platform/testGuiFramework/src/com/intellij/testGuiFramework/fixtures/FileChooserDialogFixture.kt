/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.fileChooser.actions.RefreshFileChooserAction
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickButtonWhenEnabled
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickOkButton
import com.intellij.testGuiFramework.impl.actionButtonByClass
import com.intellij.testGuiFramework.impl.waitUntilFound
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.util.waitFor
import org.fest.reflect.core.Reflection.field
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause.pause
import org.fest.util.Strings.quote
import org.junit.Assert.assertNotNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.Nonnull
import javax.swing.JDialog
import javax.swing.JTextField
import javax.swing.tree.TreePath

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
