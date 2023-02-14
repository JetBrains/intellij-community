// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.edutools

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.org.PluginManagerFilters
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.InteractiveCoursePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private val EDU_TOOLS_PLUGIN_ID = PluginId.getId("com.jetbrains.edu")
private const val BUTTON_ID = "Button"
private const val PROGRESS_ID = "Progress"

class EduToolsInteractiveCoursePanel(data: InteractiveCourseData) : InteractiveCoursePanel(data) {
  private lateinit var cardLayoutPanel: JPanel
  private lateinit var cardLayout: CardLayout
  private lateinit var progressBarPanel: ProgressBarPanel
  private var progressIndicator: ProgressIndicator = StandardProgressIndicatorBase().apply {
    isIndeterminate = false
  }

  override fun createSouthPanel(): JPanel {
    cardLayout = CardLayout()
    cardLayoutPanel = JPanel(cardLayout).apply {
      isOpaque = false
    }

    cardLayoutPanel.add(createButtonPanel(InstallEduToolsAction()), BUTTON_ID)

    progressBarPanel = ProgressBarPanel(CancelPluginActionListener())
    cardLayoutPanel.add(progressBarPanel, PROGRESS_ID)

    return cardLayoutPanel
  }

  private inner class CancelPluginActionListener : MouseAdapter() {

    override fun mouseReleased(mouseEvent: MouseEvent?) {
      mouseEvent ?: return
      if (mouseEvent.clickCount == 1 && SwingUtilities.isLeftMouseButton(mouseEvent)) {
        progressIndicator.cancel()
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
      if (PluginManager.isPluginInstalled(EDU_TOOLS_PLUGIN_ID) && !PluginManagerCore.isDisabled(EDU_TOOLS_PLUGIN_ID)) {
        val event = AnActionEvent.createFromDataContext(ActionPlaces.WELCOME_SCREEN, null, DataContext.EMPTY_CONTEXT)
        val action = ActionManager.getInstance().getAction("Educational.BrowseCourses")
        action.actionPerformed(event)

        return
      }

      showProgress()
      progressIndicator = StandardProgressIndicatorBase()
      ApplicationManager.getApplication().executeOnPooledThread {
        InstallEduToolsTask().run(progressIndicator)
      }
    }
  }

  private fun showProgress() {
    cardLayout.show(cardLayoutPanel, PROGRESS_ID)
  }

  /**
   * Inspired by [com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallPluginTask]
   */
  private inner class InstallEduToolsTask : Task.Backgroundable(null, data.getActionButtonName(), true) {

    override fun run(indicator: ProgressIndicator) {
      try {
        progressBarPanel.updateProgressBar(0.0)
        val marketplacePlugins = MarketplaceRequests.loadLastCompatiblePluginDescriptors(setOf(EDU_TOOLS_PLUGIN_ID))
        val descriptors: MutableList<IdeaPluginDescriptor> = ArrayList(RepositoryHelper.mergePluginsFromRepositories(marketplacePlugins,
                                                                                                                     emptyList(), true))
        PluginManagerCore.getPlugins().filterTo(descriptors) {
          !it.isEnabled && PluginManagerCore.isCompatible(it) && PluginManagerFilters.getInstance().allowInstallingPlugin(it)
        }
        indicator.checkCanceled()
        progressBarPanel.updateProgressBar(0.2)

        val downloader = PluginDownloader.createDownloader(descriptors.first())
        val nodes = mutableListOf<PluginNode>()
        val plugin = downloader.descriptor
        if (plugin.isEnabled) {
          nodes.add(downloader.toPluginNode())
        }
        PluginEnabler.HEADLESS.enable(listOf(plugin))
        indicator.checkCanceled()

        progressBarPanel.updateProgressBar(0.4)
        if (nodes.isNotEmpty()) {
          downloadPlugins(nodes, indicator)
        }
      }
      finally {
        showButtonAndUpdateText()
      }
    }

    private fun downloadPlugins(plugins: List<PluginNode>, indicator: ProgressIndicator) {
      val operation = PluginInstallOperation(plugins, emptyList(), PluginEnabler.HEADLESS, indicator)
      indicator.checkCanceled()
      operation.setAllowInstallWithoutRestart(true)
      operation.run()
      progressBarPanel.updateProgressBar(0.7)
      if (operation.isSuccess) {
        ApplicationManager.getApplication().invokeAndWait {
          progressBarPanel.updateProgressBar(0.8)
          for ((file, pluginDescriptor) in operation.pendingDynamicPluginInstalls) {
            indicator.checkCanceled()
            PluginInstaller.installAndLoadDynamicPlugin(file, pluginDescriptor)
          }
        }
      }
    }
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

