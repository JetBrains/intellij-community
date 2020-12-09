// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.actions

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.util.NlsSafe
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.LearnBundle
import training.ui.LearnToolWindow
import training.util.resetPrimaryLanguage
import javax.swing.JComponent

class ChooseProgrammingLanguageForLearningAction(private val learnToolWindow: LearnToolWindow) : ComboBoxAction() {
  override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
    val allActionsGroup = DefaultActionGroup()
    val supportedLanguagesExtensions = LangManager.getInstance().supportedLanguagesExtensions.sortedBy { it.language }
    for (langSupportExt: LanguageExtensionPoint<LangSupport> in supportedLanguagesExtensions) {
      val languageId = langSupportExt.language
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

  private inner class SelectLanguageAction(private val languageId: String, @NlsSafe displayName: String) : AnAction(displayName) {
    override fun actionPerformed(e: AnActionEvent) {
      val ep = LangManager.getInstance().supportedLanguagesExtensions.singleOrNull { it.language == languageId } ?: return
      resetPrimaryLanguage(ep.instance)
      learnToolWindow.setModulesPanel()
    }
  }
}

@NlsSafe
private fun getDisplayName(language: LangSupport) =
  Language.findLanguageByID(language.primaryLanguage)?.displayName ?: LearnBundle.message("unknown.language.name")


