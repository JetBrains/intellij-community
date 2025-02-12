package com.intellij.settingsSync.core.config

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.UpdateResult.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
//import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

internal class SettingsSyncOldConfigurable : BoundConfigurable(message("title.settings.sync")),
                                             SettingsSyncEnabler.Listener,
                                             SettingsSyncStatusTracker.Listener {

  private lateinit var configPanel: DialogPanel
  private lateinit var enableButton: Cell<JButton>
  private lateinit var statusLabel: JLabel

  @Volatile
  private var marketplacePluginInstalled = false

  private val syncEnabler = SettingsSyncEnabler()
  private val MARKETPLACE_PLUGIN_ID = PluginId.getId("com.intellij.marketplace")


  init {
    syncEnabler.addListener(this)
    SettingsSyncStatusTracker.getInstance().addListener(this)
  }

  inner class LoggedInPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) =
      SettingsSyncEvents.getInstance().addListener(
        object : SettingsSyncEventListener {
          override fun loginStateChanged() {
            listener(invoke())
          }
        },
        disposable!!)

    override fun invoke() = RemoteCommunicatorHolder.getCurrentUserData() != null
  }

  inner class EnabledPredicate : ComponentPredicate() {
    override fun addListener(listener: (Boolean) -> Unit) {
      SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
        override fun enabledStateChanged(syncEnabled: Boolean) {
          listener(invoke())
          configPanel.reset()
        }
      }, disposable!!)
    }

    override fun invoke() = SettingsSyncSettings.getInstance().syncEnabled

  }

  inner class SyncEnablerRunning : ComponentPredicate() {
    private var isRunning = false

    override fun addListener(listener: (Boolean) -> Unit) {
      syncEnabler.addListener(object : SettingsSyncEnabler.Listener {
        override fun serverRequestStarted() {
          updateRunning(listener, true)
        }

        override fun serverRequestFinished() {
          updateRunning(listener, false)
        }
      })
    }

    private fun updateRunning(listener: (Boolean) -> Unit, isRunning: Boolean) {
      this.isRunning = isRunning
      listener(invoke())
    }

    override fun invoke(): Boolean = isRunning
  }

  inner class AuthServiceRestartPredicate : ComponentPredicate() {
    init {
      marketplacePluginInstalled = InstalledPluginsState.getInstance().wasInstalled(MARKETPLACE_PLUGIN_ID)
    }

    override fun addListener(listener: (Boolean) -> Unit) {
      PluginStateManager.addStateListener { descriptor ->
        if (descriptor.pluginId == MARKETPLACE_PLUGIN_ID) {
          // InstalledPluginsState.getInstance().wasInstalled(MARKETPLACE_PLUGIN_ID) is still false at that time,
          // so we just cache the value
          marketplacePluginInstalled = true
          listener(marketplacePluginInstalled)
        }
      }
    }

    override fun invoke(): Boolean {
      return marketplacePluginInstalled
    }
  }

  override fun createPanel(): DialogPanel {
    val syncConfigPanel = SettingsSyncPanelFactory.createCombinedSyncSettingsPanel(
      message("configurable.what.to.sync.label"),
      SettingsSyncSettings.getInstance(),
      SettingsSyncLocalSettings.getInstance(),
    )
    val authService = RemoteCommunicatorHolder.getAuthService()
    val authAvailable = true
    configPanel = panel {
      val isSyncEnabled = LoggedInPredicate().and(EnabledPredicate())
      if (settingsRepositoryIsEnabled()) {
        row {
          label(message("settings.warning.sync.cannot.be.enabled.label")).applyToComponent {
            icon = AllIcons.General.Warning
          }
          bottomGap(BottomGap.MEDIUM)
        }
      }

      // authService is not available without restart
      if (authAvailable) {
        row {
          val statusCell = label("")
          statusCell
            .visibleIf(LoggedInPredicate())
            .enabled(!settingsRepositoryIsEnabled())
          statusLabel = statusCell.component
          updateStatusInfo()
          label(message("sync.status.login.message"))
            .visibleIf(LoggedInPredicate().not())
            .enabled(!settingsRepositoryIsEnabled())
        }
        row {
          button(message("config.button.login")) {
            runBlockingCancellable {
              authService?.login(configPanel)
            }
          }.visibleIf(LoggedInPredicate().not())
            .enabled(!settingsRepositoryIsEnabled())
          enableButton = button(message("config.button.enable")) {
            syncEnabler.checkServerStateAsync()
          }.visibleIf(LoggedInPredicate().and(EnabledPredicate().not()))
            .enabledIf(SyncEnablerRunning().not())
            .enabled(!settingsRepositoryIsEnabled())

          button(message("config.button.disable")) {
            LoggedInPredicate().and(EnabledPredicate())
            disableSync()
          }.visibleIf(isSyncEnabled)
          bottomGap(BottomGap.MEDIUM)
        }
      }
      else {
        val authServiceRestartPredicate = AuthServiceRestartPredicate()
        row {
          label(message("sync.status.login.not.available")).gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")
          link("JetBrains Marketplace Licensing Support") {
            val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(it.source as ActionLink))
            val pluginManager = settings?.find("preferences.pluginManager")
            if (pluginManager is PluginManagerConfigurable) {
              settings.select(pluginManager).doWhenDone {
                pluginManager.openMarketplaceTab("/organization:JetBrains Marketplace Licensing")
              }
            }
          }
        }.visibleIf(authServiceRestartPredicate.not())
        row {
          label(message("sync.status.restart.required", ApplicationNamesInfo.getInstance().fullProductName))
        }.visibleIf(authServiceRestartPredicate)
        row {
          button(message("sync.status.restart.ide.button")) {
            val app = ApplicationManager.getApplication() as ApplicationEx
            app.restart(true)
          }
        }.visibleIf(authServiceRestartPredicate)
      }
      row {
        comment(message("settings.sync.info.message"), 80)
          .visibleIf(isSyncEnabled.not())
      }

      row {
        cell(syncConfigPanel)
          .visibleIf(LoggedInPredicate().and(EnabledPredicate()))
          .onApply {
            syncConfigPanel.apply()

            SettingsSyncEvents.getInstance().fireCategoriesChanged()
            SettingsSyncEvents.getInstance().fireSettingsChanged(
              SyncSettingsEvent.CrossIdeSyncStateChanged(SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled))
          }
          .onReset(syncConfigPanel::reset)
          .onIsModified(syncConfigPanel::isModified)
      }
    }
    SettingsSyncEvents.getInstance().addListener(
      object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          if (RemoteCommunicatorHolder.getCurrentUserData() != null
              && !SettingsSyncSettings.getInstance().syncEnabled) {
            syncEnabler.checkServerStateAsync()
          }
          reset()
        }
      },
      disposable!!
    )
    return configPanel
  }

  private fun settingsRepositoryIsEnabled(): Boolean {
    return !SettingsSyncSettings.getInstance().syncEnabled &&
           (ApplicationManager.getApplication().stateStore.storageManager).streamProvider.let { it.enabled && it.isExclusive }
  }

  override fun serverStateCheckFinished(state: UpdateResult) {
    when (state) {
      NoFileOnServer, FileDeletedFromServer -> showEnableSyncDialog(null, null)
      is Success -> showEnableSyncDialog(
        state.settingsSnapshot.getState(),
        SettingsSyncLocalStateHolder(state.isCrossIdeSyncEnabled),
      )
      is Error -> {
        if (state != SettingsSyncEnabler.State.CANCELLED) {
          showError(message("notification.title.update.error"), state.message)
        }
      }
    }
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    when (result) {
      is Success -> {
        reset()
        SettingsSyncSettings.getInstance().syncEnabled = true
      }
      NoFileOnServer, FileDeletedFromServer -> {
        showError(message("notification.title.update.error"), message("notification.title.update.no.such.file"))
      }
      is Error -> {
        showError(message("notification.title.update.error"), result.message)
      }
    }
    updateStatusInfo()
  }

  private fun showEnableSyncDialog(remoteSettings: SettingsSyncState?, remoteSyncScopeSettings: SettingsSyncLocalStateHolder?) {
    val dialog = EnableSettingsSyncDialog(configPanel, remoteSettings, remoteSyncScopeSettings)

    dialog.show()

    val dialogResult = dialog.getResult()

    if (dialogResult != null) {
      when (dialogResult) {
        EnableSettingsSyncDialog.Result.GET_FROM_SERVER -> {
          syncEnabler.getSettingsFromServer(dialog.syncSettings)

          SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.GET_FROM_SERVER)
        }

        EnableSettingsSyncDialog.Result.PUSH_LOCAL -> {
          SettingsSyncSettings.getInstance().syncEnabled = true

          syncEnabler.pushSettingsToServer()

          if (remoteSettings != null) {
            SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.PUSH_LOCAL)
          }
          else {
            SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.PUSH_LOCAL_WAS_ONLY_WAY)
          }
        }
      }
    }
    else {
      SettingsSyncEventsStatistics.ENABLED_MANUALLY.log(SettingsSyncEventsStatistics.EnabledMethod.CANCELED)
    }

    reset()
    configPanel.reset()
  }

  companion object DisableResult {
    const val RESULT_CANCEL = 0
    const val RESULT_REMOVE_DATA_AND_DISABLE = 1
    const val RESULT_DISABLE = 2
  }

  private fun disableSync() {
    @Suppress("DialogTitleCapitalization")
    val result = Messages.showCheckboxMessageDialog( // TODO<rv>: Use AlertMessage instead
      message("disable.dialog.text"),
      message("disable.dialog.title"),
      arrayOf(Messages.getCancelButton(), message("disable.dialog.disable.button")),
      message("disable.dialog.remove.data.box"),
      false,
      1,
      1,
      Messages.getInformationIcon()
    ) { index: Int, checkbox: JCheckBox ->
      if (index == 1) {
        if (checkbox.isSelected) RESULT_REMOVE_DATA_AND_DISABLE else RESULT_DISABLE
      }
      else {
        RESULT_CANCEL
      }
    }

    when (result) {
      RESULT_DISABLE -> {
        SettingsSyncSettings.getInstance().syncEnabled = false
        updateStatusInfo()
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_ONLY)
      }
      RESULT_REMOVE_DATA_AND_DISABLE -> {
        disableAndRemoveData()
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(
          SettingsSyncEventsStatistics.ManualDisableMethod.DISABLED_AND_REMOVED_DATA_FROM_SERVER)
      }
      RESULT_CANCEL -> {
        SettingsSyncEventsStatistics.DISABLED_MANUALLY.log(SettingsSyncEventsStatistics.ManualDisableMethod.CANCEL)
      }
    }
  }

  private fun disableAndRemoveData() {
    val modality = ModalityState.current();

    object : Task.Modal(null, message("disable.remove.data.title"), false) {
      override fun run(indicator: ProgressIndicator) {
        val cdl = CountDownLatch(1)
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.DeleteServerData { result ->
          cdl.countDown()

          when (result) {
            is DeleteServerDataResult.Error -> {
              runInEdt {
                showError(message("disable.remove.data.failure"), result.error)
              }
            }
            DeleteServerDataResult.Success -> {
              runInEdt(modality) {
                updateStatusInfo()
              }
            }
          }
        })

        cdl.await(1, TimeUnit.MINUTES)
      }
    }.queue()
  }

  private fun showError(message: @Nls String, details: @Nls String) {
    val messageBuilder = StringBuilder()
    messageBuilder.append(message("sync.status.failed"))
    statusLabel.icon = AllIcons.General.Error
    messageBuilder.append(' ').append("$message: $details")
    @Suppress("HardCodedStringLiteral")
    statusLabel.text = messageBuilder.toString()
  }

  private fun updateStatusInfo() {
    if (::statusLabel.isInitialized) {
      val messageBuilder = StringBuilder()
      statusLabel.icon = null
      if (SettingsSyncSettings.getInstance().syncEnabled) {
        val statusTracker = SettingsSyncStatusTracker.getInstance()
        if (statusTracker.isSyncSuccessful()) {
          messageBuilder
            .append(message("sync.status.enabled"))
          if (statusTracker.isSynced()) {
            messageBuilder
              .append(". ")
              .append(message("sync.status.last.sync.message", getReadableSyncTime()))
          }
        }
        else {
          messageBuilder.append(message("sync.status.failed"))
          statusLabel.icon = AllIcons.General.Error
          messageBuilder.append(' ').append(statusTracker.getErrorMessage())
        }
      }
      else {
        messageBuilder.append(message("sync.status.disabled"))
      }
      @Suppress("HardCodedStringLiteral") // The above strings are localized
      statusLabel.text = messageBuilder.toString()
    }
  }

    private fun getReadableSyncTime(): String {
      return DateFormatUtil.formatPrettyDateTime(SettingsSyncStatusTracker.getInstance().getLastSyncTime()).lowercase()
    }

  private fun getUserName(): String {
    return RemoteCommunicatorHolder.getCurrentUserData()?.name ?: "?"
  }

  override fun syncStatusChanged() {
    updateStatusInfo()
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    SettingsSyncStatusTracker.getInstance().removeListener(this)
  }

  override fun getHelpTopic(): String = "cloud-config.plugin-dialog"
}