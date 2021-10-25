// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.registry.Registry
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
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.TimeUnit
import javax.swing.JLabel

class LearnToolWindow internal constructor(val project: Project, private val wholeToolWindow: ToolWindow)
  : SimpleToolWindowPanel(true, true), DataProvider {
  internal val parentDisposable: Disposable = wholeToolWindow.disposable

  internal val learnPanel: LearnPanel = LearnPanel(this)
  private val modulesPanel: ModulesPanel = ModulesPanel(project)
  private val scrollPane: JBScrollPane = if (LangManager.getInstance().languages.isEmpty()) {
    JBScrollPane(JLabel(LearnBundle.message("no.supported.languages.found")))
  }
  else {
    JBScrollPane(modulesPanel)
  }

  private val stepAnimator by lazy { StepAnimator(scrollPane.verticalScrollBar, learnPanel.lessonMessagePane) }

  init {
    setChooseLanguageButton()
    reinitViewsInternal()
    if (LessonManager.instance.lessonIsRunning()) {
      setLearnPanel()
    }
    setContent(scrollPane)
    scrollPane.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (scrollPane.viewport.view == learnPanel) {
          learnPanel.updatePanelSize(getVisibleAreaWidth())
        }
      }
    })
  }

  fun getVisibleAreaWidth(): Int {
    val scrollWidth = scrollPane.verticalScrollBar?.size?.width ?: 0
    return scrollPane.viewport.extentSize.width - scrollWidth
  }

  private fun reinitViewsInternal() {
    modulesPanel.updateMainPanel()
  }

  internal fun setLearnPanel() {
    wholeToolWindow.setTitleActions(listOf(restartAction()))
    scrollPane.setViewportView(learnPanel)
    scrollPane.revalidate()
    scrollPane.repaint()
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

  internal fun reinitViews() {
    reinitViewsInternal()
    updateScrollPane()
  }

  internal fun scrollToTheEnd() {
    val vertical = scrollPane.verticalScrollBar
    if (useAnimation()) stepAnimator.startAnimation(vertical.maximum)
    else vertical.value = vertical.maximum
  }

  internal fun scrollToTheStart() {
    scrollPane.verticalScrollBar.value = 0
  }

  internal fun scrollTo(needTo: Int) {
    if (useAnimation()) stepAnimator.startAnimation(needTo)
    else {
      scrollPane.verticalScrollBar.value = needTo
    }
  }

  private fun useAnimation() = Registry.`is`("ift.use.scroll.animation", false)
}
