// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBScrollPane
import org.fest.swing.timing.Timeout
import training.actions.ChooseProgrammingLanguageForLearningAction
import training.lang.LangManager
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.ui.views.LearnPanel
import training.ui.views.ModulesPanel
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

class LearnToolWindow internal constructor(val project: Project, private val wholeToolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {
  val parentDisposable: Disposable = wholeToolWindow.disposable

  private var scrollPane: JBScrollPane
  var learnPanel: LearnPanel? = null
    private set
  private val modulesPanel: ModulesPanel = ModulesPanel()

  init {
    setChooseLanguageButton()
    reinitViewsInternal()
    scrollPane = if (LangManager.getInstance().languages.isEmpty()) {
      JBScrollPane(JLabel(LearnBundle.message("no.supported.languages.found")))
    }
    else {
      JBScrollPane(modulesPanel)
    }
    if (LessonManager.instance.lessonIsRunning()) {
      setLearnPanel()
    }
    setContent(scrollPane)
  }

  private fun reinitViewsInternal() {
    learnPanel = LearnPanel(this)
    modulesPanel.updateMainPanel()
  }

  fun setLearnPanel() {
    wholeToolWindow.setTitleActions(listOf(restartAction()))
    scrollPane.setViewportView(learnPanel)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  fun showGotItAboutRestart() {
    val gotIt = GotItTooltip("reset.lesson.got.it",
                             LearnBundle.message("completed.lessons.got.it"),
                             parentDisposable)
    if (gotIt.canShow()) {
      val needToFindButton = restartAction() ?: return
      ApplicationManager.getApplication().executeOnPooledThread {
        val button = LearningUiUtil.findShowingComponentWithTimeout(
          null, ActionButton::class.java, Timeout.timeout(500, TimeUnit.MILLISECONDS)
        ) { it.action == needToFindButton }
        invokeLater {
          gotIt.show(button, GotItTooltip.BOTTOM_MIDDLE)
        }
      }
    }
  }

  private fun restartAction() = ActionManager.getInstance().getAction("RestartLessonAction")

  fun setModulesPanel() {
    setChooseLanguageButton()
    modulesPanel.updateMainPanel()
    scrollPane.setViewportView(modulesPanel)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  /** May be a temporary solution */
  private fun setChooseLanguageButton() {
    if (LangManager.getInstance().supportedLanguagesExtensions.isNotEmpty() && LangManager.getInstance().supportedLanguagesExtensions.size > 1) {
      wholeToolWindow.setTitleActions(listOf(ChooseProgrammingLanguageForLearningAction(this)))
    }
  }

  private fun updateScrollPane() {
    scrollPane.viewport.revalidate()
    scrollPane.viewport.repaint()
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  fun reinitViews() {
    reinitViewsInternal()
    updateScrollPane()
  }
}
