// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import training.actions.ChooseProgrammingLanguageForLearningAction
import training.lang.LangManager
import training.learn.lesson.LessonManager
import training.ui.views.LanguageChoosePanel
import training.ui.views.LearnPanel
import training.ui.views.ModulesPanel


class LearnToolWindow internal constructor(val project: Project, private val wholeToolWindow: ToolWindow) : SimpleToolWindowPanel(true, true), DataProvider {
  val parentDisposable: Disposable = wholeToolWindow.disposable

  private var scrollPane: JBScrollPane
  var learnPanel: LearnPanel? = null
    private set
  private val modulesPanel: ModulesPanel = ModulesPanel(this)

  init {
    setChooseLanguageButton()
    reinitViewsInternal()
    scrollPane = if (LangManager.getInstance().isLangUndefined()) {
      JBScrollPane(LanguageChoosePanel(this))
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
    wholeToolWindow.setTitleActions(listOf(ActionManager.getInstance().getAction("RestartLessonAction")))
    scrollPane.setViewportView(learnPanel)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  fun setModulesPanel() {
    setChooseLanguageButton()
    modulesPanel.updateMainPanel()
    scrollPane.setViewportView(modulesPanel)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  /** May be a temporary solution */
  private fun setChooseLanguageButton() {
    wholeToolWindow.setTitleActions(listOf(ChooseProgrammingLanguageForLearningAction(this)))
  }

  fun setChooseLanguageView() {
    scrollPane.setViewportView(LanguageChoosePanel(this))
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  fun updateScrollPane() {
    scrollPane.viewport.revalidate()
    scrollPane.viewport.repaint()
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  fun reinitViews() {
    reinitViewsInternal()
    updateScrollPane()
  }

  fun scrollToTheEnd() {
    val vertical = scrollPane.verticalScrollBar
    vertical.value = vertical.maximum
  }
}
