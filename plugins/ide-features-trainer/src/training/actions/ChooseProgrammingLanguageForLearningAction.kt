// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.lang.LangManager
import training.lang.LangSupport
import training.lang.LangSupportBean
import training.learn.LearnBundle
import training.ui.LearnToolWindow
import training.util.resetPrimaryLanguage
import javax.swing.JComponent

internal class ChooseProgrammingLanguageForLearningAction(private val learnToolWindow: LearnToolWindow) : ComboBoxAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
    val allActionsGroup = DefaultActionGroup()
    val supportedLanguagesExtensions = LangManager.getInstance().supportedLanguagesExtensions.sortedBy { it.language }
    for (langSupportExt: LangSupportBean in supportedLanguagesExtensions) {
      val languageId = langSupportExt.getLang()
      val displayName = Language.findLanguageByID(languageId)?.displayName ?: continue
      allActionsGroup.add(SelectLanguageAction(languageId, displayName))
    }
    return allActionsGroup
  }

  override fun update(e: AnActionEvent) {
    val langSupport = LangManager.getInstance().getLangSupport()
    if (langSupport != null) {
      e.presentation.text = getDisplayName(langSupport)
    }
    e.presentation.description = LearnBundle.message("learn.choose.language.description.combo.box")
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).also {
      if (ExperimentalUI.isNewUI()) {
        UIUtil.setBackgroundRecursively(it, JBUI.CurrentTheme.ToolWindow.background())
      }
    }
  }

  private inner class SelectLanguageAction(private val languageId: String, @NlsSafe displayName: String) : AnAction(displayName) {
    override fun actionPerformed(e: AnActionEvent) {
      val ep = LangManager.getInstance().supportedLanguagesExtensions.singleOrNull { it.language == languageId } ?: return
      resetPrimaryLanguage(ep.getLang())
      learnToolWindow.setModulesPanel()
    }
  }
}

@NlsSafe
private fun getDisplayName(language: LangSupport) =
  Language.findLanguageByID(language.primaryLanguage)?.displayName ?: LearnBundle.message("unknown.language.name")


