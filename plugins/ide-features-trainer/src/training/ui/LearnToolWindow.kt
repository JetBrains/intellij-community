// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.assertj.swing.timing.Timeout
import training.actions.ChooseProgrammingLanguageForLearningAction
import training.lang.LangManager
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.ui.views.LearnPanel
import training.ui.views.ModulesPanel
import training.util.enableLessonsAndPromoters
import training.util.getActionById
import java.awt.Color
import java.util.concurrent.TimeUnit
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class LearnToolWindow internal constructor(
  val project: Project,
  private val wholeToolWindow: ToolWindow
) : SimpleToolWindowPanel(true, true) {
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

  override fun updateUI() {
    super.updateUI()
    if (parent != null) {
      reinitViews()
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
  JBScrollPane(modulesPanel?.let { adjustModulesPanel(it) } ?: JLabel(LearnBundle.message("no.supported.languages.found")))


private fun adjustModulesPanel(contentPanel: JPanel): JPanel {
  if (enableLessonsAndPromoters) {
    return contentPanel
  }
  return JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(JPanel().apply {
      alignmentX = SimpleToolWindowPanel.LEFT_ALIGNMENT
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      border = MatteBorder(0, 0, JBUI.scale(1), 0, UIUtil.CONTRAST_BORDER_COLOR)

      add(object : JPanel() {
        // It seems the new UI toolwindows redefine background recursively, so force background
        override fun getBackground(): Color = JBUI.CurrentTheme.Validator.warningBackgroundColor()
      }.apply<JPanel> {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = JBUI.Borders.empty(12)
        isOpaque = true
        add(JBLabel().apply {
          icon = AllIcons.General.Warning
          text = LearnBundle.message("modules.panel.new.ui.warning")
        })
      })
    })
    add(contentPanel)
  }
}
