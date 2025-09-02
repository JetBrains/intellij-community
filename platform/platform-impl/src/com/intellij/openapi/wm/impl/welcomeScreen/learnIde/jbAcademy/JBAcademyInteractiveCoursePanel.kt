// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.InteractiveCoursePanel
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.getBrowseCoursesAction
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.InstallJBAcademyTask.JB_ACADEMY_PLUGIN_ID
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val BUTTON_ID = "Button"
private const val PROGRESS_ID = "Progress"

class JBAcademyInteractiveCoursePanel(data: InteractiveCourseData) : InteractiveCoursePanel(data) {
  private lateinit var cardLayoutPanel: JPanel
  private lateinit var cardLayout: CardLayout
  private lateinit var progressBarPanel: ProgressBarPanel
  private var job: Job? = null

  override fun createSouthPanel(): JPanel {
    cardLayout = CardLayout()
    cardLayoutPanel = JPanel(cardLayout).apply {
      isOpaque = false
    }

    cardLayoutPanel.add(createButtonPanel(), BUTTON_ID)

    progressBarPanel = ProgressBarPanel(CancelPluginActionListener())
    cardLayoutPanel.add(progressBarPanel, PROGRESS_ID)

    return cardLayoutPanel
  }

  override fun getButtonAction(): Action = InstallEduToolsAction()

  private inner class CancelPluginActionListener : MouseAdapter() {

    override fun mouseReleased(mouseEvent: MouseEvent?) {
      mouseEvent ?: return
      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        job?.cancel()
        job = null
        showButtonAndUpdateText()
      }
    }
  }

  private fun showButtonAndUpdateText() {
    startLearningButton.text = data.getActionButtonName()
    startLearningButton.revalidate()
    startLearningButton.repaint()
    cardLayout.show(cardLayoutPanel, BUTTON_ID)
  }

  private inner class InstallEduToolsAction : AbstractAction(data.getActionButtonName()) {

    override fun actionPerformed(e: ActionEvent?) {
      if (PluginManager.isPluginInstalled(JB_ACADEMY_PLUGIN_ID) && !PluginManagerCore.isDisabled(JB_ACADEMY_PLUGIN_ID)) {
        val event = AnActionEvent.createFromDataContext(ActionPlaces.WELCOME_SCREEN, null, DataContext.EMPTY_CONTEXT)
        val action = getBrowseCoursesAction()
        action?.actionPerformed(event)

        return
      }

      showProgress()
      job = service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        val progressPipe = createProgressPipe()
        
        val progressUpdater = launch {
          progressPipe.progressUpdates().throttle(50).collect {
            val fraction = it.fraction ?: 0.0
            withContext(Dispatchers.EDT) {
              progressBarPanel.updateProgressBar(fraction)
            }
          }
        }
        
        try {
          progressPipe.collectProgressUpdates { InstallJBAcademyTask.install() }
        } finally {
          progressUpdater.cancel()
          withContext(Dispatchers.EDT) {
            showButtonAndUpdateText()
          }
        }
      }
    }
  }

  private fun showProgress() {
    cardLayout.show(cardLayoutPanel, PROGRESS_ID)
  }

}

private class ProgressBarPanel(listener: MouseAdapter) : JPanel() {
  val projectCancelButton = JLabel(AllIcons.Actions.DeleteTag).apply {
    border = JBUI.Borders.empty(0, 8, 0, 14)
  }

  private val progressBar = JProgressBar().apply {
    isOpaque = false
    isIndeterminate = false
  }

  init {
    isOpaque = false
    layout = BorderLayout()
    add(progressBar, BorderLayout.CENTER)

    val buttonWrapper = Wrapper().apply {
      addMouseListener(listener)
      addMouseMotionListener(listener)
      setContent(projectCancelButton)
    }
    add(buttonWrapper, BorderLayout.EAST)
  }

  fun updateProgressBar(fraction: Double) {
    progressBar.value = (fraction * 100).toInt()
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    size.width = 175
    return size
  }
}

