// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBScrollPane
import org.assertj.swing.timing.Timeout
import training.actions.ChooseProgrammingLanguageForLearningAction
import training.lang.LangManager
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.ui.views.LearnPanel
import training.ui.views.ModulesPanel
import training.util.getActionById
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

class LearnToolWindow internal constructor(val project: Project, private val wholeToolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {
  internal val parentDisposable: Disposable = wholeToolWindow.disposable

  internal val learnPanel: LearnPanel = LearnPanel(this)
  private val modulesPanel = ScrollModulesPanel(if (LangManager.getInstance().languages.isEmpty()) null else ModulesPanel(project))

  init {
    setChooseLanguageButton()
    reinitViews()
    if (LessonManager.instance.lessonIsRunning()) {
      setLearnPanel()
    } else {
      setContent(modulesPanel)
    }
  }

  internal fun reinitViews() {
    modulesPanel.modulesPanel?.updateMainPanel()
  }

  internal fun setLearnPanel() {
    wholeToolWindow.setTitleActions(listOf(restartAction()))
    setContent(learnPanel)
  }

  internal fun showGotItAboutRestart() {
    val gotIt = GotItTooltip("reset.lesson.got.it",
                             LearnBundle.message("completed.lessons.got.it"),
                             parentDisposable)
    if (gotIt.canShow()) {
      val needToFindButton = restartAction()
      ApplicationManager.getApplication().executeOnPooledThread {
        val button = LearningUiUtil.findShowingComponentWithTimeout(
          project, ActionButton::class.java, Timeout.timeout(500, TimeUnit.MILLISECONDS)
        ) { it.action == needToFindButton }
        invokeLater {
          gotIt.show(button, GotItTooltip.BOTTOM_MIDDLE)
        }
      }
    }
  }

  private fun restartAction() = getActionById("RestartLessonAction")

  internal fun setModulesPanel() {
    setChooseLanguageButton()
    modulesPanel.modulesPanel?.updateMainPanel()
    setContent(modulesPanel)
  }

  /** May be a temporary solution */
  private fun setChooseLanguageButton() {
    if (LangManager.getInstance().supportedLanguagesExtensions.isNotEmpty() && LangManager.getInstance().supportedLanguagesExtensions.size > 1) {
      wholeToolWindow.setTitleActions(listOf(ChooseProgrammingLanguageForLearningAction(this)))
    }
  }
}


private class ScrollModulesPanel(val modulesPanel: ModulesPanel?) :
  JBScrollPane(modulesPanel ?: JLabel(LearnBundle.message("no.supported.languages.found")))
