// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.NonCustomizableAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.PillButton
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.ui.launchOnShow
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Announces an available IDE update in the main toolbar instead of the [com.intellij.ide.actions.SettingsEntryPointAction] menu.
 *
 * Shown only for updates from the release channel and only while [IdeUpdateWidgetState.isWidgetShown] holds,
 * see [UpdateSettingsEntryPointActionProvider].
 */
internal class IdeUpdateToolbarWidget : DumbAwareAction(), CustomComponentAction, RightAlignedToolbarAction, NonCustomizableAction,
                                        ActionRemoteBehaviorSpecification.Frontend {

  private val status: IdeUpdateWidgetState.Status
    get() = IdeUpdateWidgetState.getInstance().status.value

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.place == ActionPlaces.MAIN_TOOLBAR && IdeUpdateWidgetState.isWidgetShown()
    e.presentation.isEnabled = status != IdeUpdateWidgetState.Status.DOWNLOADING
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return UpdateButtonWrapper { component ->
      ActionManager.getInstance().tryToExecute(this@IdeUpdateToolbarWidget, null, component, place, false)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    when (status) {
      IdeUpdateWidgetState.Status.AVAILABLE -> showUpdatePopup(e)
      IdeUpdateWidgetState.Status.RESTART -> PlatformUpdateDialog.restartLaterAndRunCommand(IdeUpdateWidgetState.getInstance().restartCommand!!)
      else -> {}
    }
  }

  private fun showUpdatePopup(e: AnActionEvent) {
    // a PlatformUpdates.Loaded is not persisted between runs, so right after a restart it has to be loaded on demand
    val update = UpdateSettingsEntryPointActionProvider.getPlatformUpdateInfo()
                 ?: UpdateSettingsEntryPointActionProvider.reloadPlatformUpdateInfo(e.project)
                 ?: return

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, createPopupGroup(update), e.dataContext, null, true,
                                                                    ActionPlaces.getPopupPlace("IdeUpdateToolbarWidget"))
    popup.isShowSubmenuOnHover = true

    val component = e.inputEvent?.component ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    if (component == null) {
      popup.showInFocusCenter()
    }
    else {
      popup.showUnderneathOf(component)
    }
  }
}

private fun createPopupGroup(update: PlatformUpdates.Loaded): DefaultActionGroup {
  val options = DefaultActionGroup(IdeBundle.message("update.toolbar.widget.options.group"),
                                   listOf(SkipThisUpdateAction(update), Separator.getInstance(), ConfigureUpdatesAction()))
  options.templatePresentation.isPopupGroup = true

  return DefaultActionGroup(DownloadUpdateAction(update), WhatsNewAction(update), options)
}

private class DownloadUpdateAction(private val update: PlatformUpdates.Loaded) :
  DumbAwareAction(IdeBundle.message("update.toolbar.widget.download.action")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = update.patches != null || update.newBuild.downloadUrl != null
    e.presentation.isEnabled = !PlatformUpdateDialog.isPatchWriteProtected(update)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val downloadUrl = update.newBuild.downloadUrl
    when {
      update.patches != null -> PlatformUpdateDialog.startPatchTask(e.project, update, null, emptyList())
      downloadUrl != null -> BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(downloadUrl))
    }
  }
}

private class WhatsNewAction(private val update: PlatformUpdates.Loaded) :
  DumbAwareAction(IdeBundle.message("update.toolbar.widget.whats.new.action")) {

  override fun actionPerformed(e: AnActionEvent) {
    UpdateSettingsEntryPointActionProvider.showPlatformUpdateDialog(e.project, update)
  }
}

private class SkipThisUpdateAction(private val update: PlatformUpdates.Loaded) :
  DumbAwareAction(IdeBundle.message("update.toolbar.widget.skip.action")) {

  override fun actionPerformed(e: AnActionEvent) {
    UpdateSettings.getInstance().ignoredBuildNumbers.add(update.newBuild.number.asStringWithoutProductCode())
  }
}

private class ConfigureUpdatesAction : DumbAwareAction(IdeBundle.message("update.toolbar.widget.configure.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().editConfigurable(e.getData(PlatformDataKeys.CONTEXT_COMPONENT), UpdateSettingsConfigurable(false))
  }
}

/**
 * Prevent button vertical stretching
 *
 * @see com.intellij.platform.trialPromotion.idesWithFreeTier.TrialStateButtonWrapper
 */
private class UpdateButtonWrapper(private val onClick: (JComponent) -> Unit) : JPanel(GridLayout()) {

  private val button: PillButton = PillButton()
  private var status: IdeUpdateWidgetState.Status = IdeUpdateWidgetState.Status.AVAILABLE

  init {
    isOpaque = false

    button.addActionListener { onClick(button) }

    // applied once synchronously, so the toolbar lays the button out with its final text right away, and then tracked while shown
    applyStatus(IdeUpdateWidgetState.getInstance().status.value)
    button.launchOnShow("IdeUpdateButton") {
      IdeUpdateWidgetState.getInstance().status.collect(::applyStatus)
    }

    RowsGridBuilder(this)
      .resizableRow()
      .cell(button, verticalAlign = VerticalAlign.CENTER, gaps = UnscaledGaps(left = 10, right = 10))
  }

  private fun applyStatus(status: IdeUpdateWidgetState.Status) {
    this.status = status
    button.isEnabled = status != IdeUpdateWidgetState.Status.DOWNLOADING
    button.text = when (status) {
      IdeUpdateWidgetState.Status.DOWNLOADING -> IdeBundle.message("update.toolbar.widget.downloading.text")
      IdeUpdateWidgetState.Status.RESTART -> IdeBundle.message("update.toolbar.widget.restart.text")
      else -> IdeBundle.message("update.toolbar.widget.text", ApplicationNamesInfo.getInstance().fullProductName)
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleUpdateButtonWrapper()
    }
    return accessibleContext
  }

  private inner class AccessibleUpdateButtonWrapper : AccessibleJPanel(), AccessibleAction {

    override fun getAccessibleName(): String {
      return button.text ?: IdeBundle.message("update.toolbar.widget.accessible.name")
    }

    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON

    override fun getAccessibleAction(): AccessibleAction = this

    override fun getAccessibleActionCount(): Int = if (button.isEnabled) 1 else 0

    override fun getAccessibleActionDescription(i: Int): String? {
      return when {
        i != 0 -> null
        status == IdeUpdateWidgetState.Status.RESTART -> IdeBundle.message("update.toolbar.widget.accessible.action.restart")
        else -> IdeBundle.message("update.toolbar.widget.accessible.action.click")
      }
    }

    override fun doAccessibleAction(i: Int): Boolean {
      return if (i == 0 && button.isEnabled) {
        onClick(button)
        true
      }
      else {
        false
      }
    }
  }
}
