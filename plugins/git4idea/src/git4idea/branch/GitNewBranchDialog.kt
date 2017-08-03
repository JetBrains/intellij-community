/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.util.ui.UIUtil.DEFAULT_VGAP
import git4idea.validators.GitNewBranchNameValidator
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent

internal data class GitNewBranchOptions(val name: String, @get:JvmName("shouldCheckout") val checkout: Boolean)

internal class GitNewBranchDialog(project: Project, dialogTitle: String, initialName: String?, validator: GitNewBranchNameValidator) :
  Messages.InputDialog(project, "New branch name:", dialogTitle, null, initialName, validator) {

  private lateinit var checkoutCheckbox : JBCheckBox

  fun showAndGetOptions(): GitNewBranchOptions? {
    return if (showAndGet()) GitNewBranchOptions(inputString!!.trim(), checkoutCheckbox.isSelected) else null
  }

  override fun createCenterPanel(): JComponent? {
    checkoutCheckbox = JBCheckBox("Checkout branch", true)
    checkoutCheckbox.mnemonic = KeyEvent.VK_C

    val panel = JBUI.Panels.simplePanel(DEFAULT_HGAP, DEFAULT_VGAP)
    panel.add(checkoutCheckbox, BorderLayout.WEST)
    return panel
  }
}
