// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class NewThemeAction: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val module = e.getRequiredData(LangDataKeys.MODULE)
    val dialog = NewThemeDialog(module.project)
    dialog.show()
    if (dialog.exitCode == DialogWrapper.OK_EXIT_CODE) {
      val themeName = dialog.name.text
      val isDark = dialog.isDark.isSelected
      //todo[Yaroslav Pankratyev]: create "themename.theme.json" file with filled name, isDark and empty ui node, and register it in plugin.xml with random id
    }
  }

  override fun update(e: AnActionEvent) {
    val module = e.getData(LangDataKeys.MODULE)
    e.presentation.isEnabled = module != null && PsiUtil.isPluginModule(module)
  }

  class NewThemeDialog(project: Project) : DialogWrapper(project) {
    val name = JBTextField()
    val isDark = CheckBox(DevKitBundle.message("new.theme.dialog.is.dark.checkbox.text"), true)

    init {
      title = DevKitBundle.message("new.theme.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent? {
      return panel {
        row(DevKitBundle.message("new.theme.dialog.name.text.field.text")) {
          cell {name(growPolicy = GrowPolicy.MEDIUM_TEXT)}
        }
        row("") {
          cell { isDark() }
        }
      }
    }

    override fun getPreferredFocusedComponent(): JComponent? {
      return name
    }
  }
}
