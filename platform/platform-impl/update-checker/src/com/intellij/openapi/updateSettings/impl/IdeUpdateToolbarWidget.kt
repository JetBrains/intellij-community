// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.NonCustomizableAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.AlignedPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.PillButton
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.launchOnShow
import java.awt.Component
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Announces an available IDE update in the main toolbar instead of the [com.intellij.ide.actions.SettingsEntryPointAction] menu.
 *
 * Shown only for updates from the release channel and only while [IdeUpdateWidgetState.isWidgetShown] holds,
 * see [UpdateSettingsEntryPointActionProvider].
 */
internal class IdeUpdateToolbarWidget :
  DumbAwareAction(IdeBundle.message("update.toolbar.widget.text", ApplicationNamesInfo.getInstance().fullProductName)),
  CustomComponentAction, RightAlignedToolbarAction, NonCustomizableAction, ActionRemoteBehaviorSpecification.Frontend {

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
      IdeUpdateWidgetState.Status.RESTART -> {
        IdeUpdateUsageTriggerCollector.UPDATE_WIDGET_RESTART_CLICKED.log(e.project)
        PlatformUpdateDialog.restartLaterAndRunCommand(IdeUpdateWidgetState.getInstance().restartCommand!!)
      }
      else -> {}
    }
  }

  private fun showUpdatePopup(e: AnActionEvent) {
    val project = e.project ?: return

    // a PlatformUpdates.Loaded is not persisted between runs, so right after a restart it has to be loaded on demand
    val update = UpdateSettingsEntryPointActionProvider.getPlatformUpdateInfo()
                 ?: UpdateSettingsEntryPointActionProvider.reloadPlatformUpdateInfo(project)
                 ?: return

    val factory = JBPopupFactory.getInstance()
    val group = ActionManager.getInstance().getAction("IdeUpdateToolbarWidget.Popup") as ActionGroup
    // the actions are registered in XML, so the update they act on is passed through the data context
    val dataContext = CustomizedDataContext.withSnapshot(e.dataContext) { sink -> sink[UPDATE_KEY] = update }
    val step = factory.createActionsStep(group, dataContext,
                                         ActionPlaces.getPopupPlace("IdeUpdateToolbarWidget"),
                                         false, true, null, null, false, -1, false)
    // the renderer is decorated before the list wraps it into ExpandedItemListCellRendererWrapper, so the wrapping stays single
    val popup = factory.createListPopup(project, step) { base -> DownloadUpdateRenderer(update, step, base) }
    popup.isShowSubmenuOnHover = true

    val component = e.inputEvent?.component ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    if (component == null) {
      popup.showInFocusCenter()
    }
    else {
      // Don't show the popup when click with the opened popup already
      PopupUtil.setPopupToggleComponent(popup, component)
      AlignedPopup.showUnderneathWithoutAlignment(popup, component)
    }
  }
}

private class DownloadUpdateRenderer(
  private val update: PlatformUpdates.Loaded,
  private val step: ListPopupStep<Any>,
  private val base: ListCellRenderer<Any>,
) : ListCellRenderer<Any> {

  override fun getListCellRendererComponent(
    list: JList<out Any>?,
    value: Any?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val action = (value as? AnActionHolder)?.action
    if (action !is DownloadUpdateAction) {
      return base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
    val title = action.templatePresentation.text.orEmpty()
    val enabledItem = step.isSelectable(value)
    lateinit var titleLabel: JLabel
    lateinit var versionsLabel: JLabel

    val content = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          icon(AllIcons.Ide.Notification.PluginUpdate)
            .align(AlignY.TOP)
            .customize(UnscaledGaps(right = 6))

          panel {
            row {
              titleLabel = label(title)
                .applyToComponent {
                  foreground = UIUtil.getListForeground(isSelected, false)
                }
                .component
            }
            row {
              versionsLabel = label(IdeBundle.message("update.toolbar.widget.download.versions",
                                                      ApplicationInfo.getInstance().fullVersion, update.newBuild.version))
                .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
                .component
            }
          }
        }.enabled(enabledItem)
      }
    }
    content.isOpaque = false
    content.border = JBUI.Borders.empty(6, 0)

    val result = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
    PopupUtil.configListRendererFlexibleHeight(result)
    if (isSelected && enabledItem) {
      result.selectionColor = UIUtil.getListSelectionBackground(true)
    }

    AccessibleContextUtil.setCombinedName(result, titleLabel, " - ", versionsLabel)
    return result
  }
}

/**
 * The popup items are hidden outside the button popup, where [UPDATE_KEY] is not provided.
 */
internal abstract class UpdatePopupAction : DumbAwareAction() {

  protected val AnActionEvent.update: PlatformUpdates.Loaded?
    get() = getData(UPDATE_KEY)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.update != null
  }

  final override fun actionPerformed(e: AnActionEvent) {
    actionPerformed(e, e.update ?: return)
  }

  protected abstract fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded)
}

internal class DownloadUpdateAction : UpdatePopupAction() {

  override fun update(e: AnActionEvent) {
    super.update(e)

    val update = e.update ?: return

    e.presentation.isVisible = update.patches != null || update.newBuild.downloadUrl != null
    e.presentation.isEnabled = !PlatformUpdateDialog.isPatchWriteProtected(update)
  }

  override fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded) {
    val downloadUrl = update.newBuild.downloadUrl
    when {
      update.patches != null -> PlatformUpdateDialog.startPatchTask(e.project, update, null, emptyList())
      downloadUrl != null -> BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(downloadUrl))
    }
  }
}

internal class WhatsNewAction : UpdatePopupAction() {

  override fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded) {
    UpdateSettingsEntryPointActionProvider.showPlatformUpdateDialog(e.project, update)
  }
}

internal class SkipThisUpdateAction : UpdatePopupAction() {

  override fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded) {
    UpdateSettings.getInstance().ignoredBuildNumbers.add(update.newBuild.number.asStringWithoutProductCode())
    UpdateSettingsEntryPointActionProvider.clearUpdatesInfo()
  }
}

internal class ConfigureUpdatesAction : UpdatePopupAction() {

  override fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded) {
    ShowSettingsUtil.getInstance().editConfigurable(e.getData(PlatformDataKeys.CONTEXT_COMPONENT), UpdateSettingsConfigurable(false))
  }
}

internal class RemindMeLaterAction : UpdatePopupAction() {

  override fun actionPerformed(e: AnActionEvent, update: PlatformUpdates.Loaded) {
    IdeUpdateWidgetState.getInstance().remindMeLater()
    UpdateSettingsEntryPointActionProvider.clearUpdatesInfo()
  }
}

private val UPDATE_KEY = DataKey.create<PlatformUpdates.Loaded>("IdeUpdateToolbarWidget.update")

private fun IdeUpdateWidgetState.Status.buttonText(): @NlsContexts.Button String {
  return when (this) {
    IdeUpdateWidgetState.Status.NONE -> ""
    IdeUpdateWidgetState.Status.AVAILABLE -> IdeBundle.message("update.toolbar.widget.text",
                                                               ApplicationNamesInfo.getInstance().fullProductName)
    IdeUpdateWidgetState.Status.DOWNLOADING -> IdeBundle.message("update.toolbar.widget.downloading.text")
    IdeUpdateWidgetState.Status.RESTART -> IdeBundle.message("update.toolbar.widget.restart.text")
  }
}

private fun IdeUpdateWidgetState.Status.buttonTooltip(): @NlsContexts.Tooltip String? {
  return when (this) {
    IdeUpdateWidgetState.Status.NONE -> null
    IdeUpdateWidgetState.Status.AVAILABLE -> IdeBundle.message("update.toolbar.widget.tooltip")
    IdeUpdateWidgetState.Status.DOWNLOADING -> null
    IdeUpdateWidgetState.Status.RESTART -> IdeBundle.message("update.toolbar.widget.restart.tooltip")
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
    button.prototypeTexts = IdeUpdateWidgetState.Status.entries.map { it.buttonText() }

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
    button.text = status.buttonText()
    button.toolTipText = status.buttonTooltip()
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
