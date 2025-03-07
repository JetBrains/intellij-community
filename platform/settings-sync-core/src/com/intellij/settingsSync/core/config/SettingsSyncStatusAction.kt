package com.intellij.settingsSync.core.config

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.ui.BadgeIconSupplier
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.SettingsSyncIcons
import org.jetbrains.annotations.Nls
import javax.swing.Icon

private enum class SyncStatus {ON, OFF, FAILED, PENDING_ACTION}

private fun getStatus() : SyncStatus {
  if (SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired) {
    return SyncStatus.PENDING_ACTION
  }
  if (SettingsSyncSettings.getInstance().syncEnabled &&
      RemoteCommunicatorHolder.getCurrentUserData() != null) {
    return if (SettingsSyncStatusTracker.getInstance().isSyncSuccessful()) SyncStatus.ON
    else SyncStatus.FAILED
  }
  else
    return SyncStatus.OFF
}

internal class SettingsSyncStatusAction : SettingsSyncOpenSettingsAction(),
                                          SettingsEntryPointAction.NoDots,
                                          SettingsSyncStatusTracker.Listener {

  init {
    SettingsSyncStatusTracker.getInstance().addListener(this)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val p = e.presentation
    val status = getStatus()
    when (status) {
      SyncStatus.ON ->
        p.icon = SettingsSyncIcons.StatusEnabled
      SyncStatus.OFF ->
        p.icon = SettingsSyncIcons.StatusDisabled
      SyncStatus.FAILED ->
        p.icon = AllIcons.General.Error
      SyncStatus.PENDING_ACTION ->
        p.icon = AllIcons.General.Warning
    }
    p.text = getStyledStatus(status)
  }

  private fun getStyledStatus(status: SyncStatus): @Nls String {
    val builder = StringBuilder()
    builder.append("<html>")
      .append(message("status.action.settings.sync")).append(" ")
      .append("<font color='#")
    val hexColor = UIUtil.colorToHex(JBUI.CurrentTheme.Popup.mnemonicForeground())
    builder.append(hexColor).append("'>")
    when (status) {
      SyncStatus.ON -> builder.append(message("status.action.settings.sync.is.on"))
      SyncStatus.OFF -> builder.append(message("status.action.settings.sync.is.off"))
      SyncStatus.FAILED -> builder.append(message("status.action.settings.sync.failed"))
      SyncStatus.PENDING_ACTION -> builder.append(message("status.action.settings.sync.pending.action"))
    }
    builder
      .append("</font>")
    return "$builder"
  }

  class IconCustomizer : SettingsEntryPointAction.IconCustomizer {
    override fun getCustomIcon(supplier: BadgeIconSupplier): Icon? {
      return if (getStatus() == SyncStatus.FAILED) {
        supplier.getErrorIcon(true)
      }
      else if (getStatus() == SyncStatus.PENDING_ACTION) {
        supplier.getWarningIcon(true)
      } else
        null
    }
  }

  override fun syncStatusChanged() {
    SettingsEntryPointAction.updateState()
  }
}