// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.codeInsight.AutoPopupController
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke

object GHPRSearchPanel {

  fun create(project: Project, model: SingleValueModel<String>, completionProvider: TextCompletionProvider): JComponent {
    val searchField = object : TextFieldWithCompletion(project, completionProvider, "", true, true, false, false) {
      override fun setupBorder(editor: EditorEx) {
        editor.setBorder(JBUI.Borders.empty(6, 5))
      }

      override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
        if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
          model.value = text
          return true
        }
        return super.processKeyBinding(ks, e, condition, pressed)
      }
    }.apply {
      addSettingsProvider {
        it.putUserData(AutoPopupController.NO_ADS, true)
        UIUtil.setNotOpaqueRecursively(it.component)
      }
    }

    val icon = JLabel(AllIcons.Actions.Find).apply {
      border = JBUI.Borders.emptyLeft(5)
    }

    model.addAndInvokeValueChangedListener {
      searchField.text = model.value
    }

    return JBUI.Panels.simplePanel(searchField).addToLeft(icon).withBackground(UIUtil.getListBackground())
  }
}