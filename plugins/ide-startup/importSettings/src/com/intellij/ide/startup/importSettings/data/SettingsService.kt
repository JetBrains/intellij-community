// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.BootstrapBundle
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.chooser.ui.SettingsImportOrigin
import com.intellij.ide.startup.importSettings.jb.IDEData
import com.intellij.ide.startup.importSettings.jb.JbImportServiceImpl
import com.intellij.ide.startup.importSettings.sync.SyncServiceImpl
import com.intellij.ide.startup.importSettings.transfer.SettingTransferService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.jetbrains.rd.util.reactive.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.time.LocalDate
import javax.swing.Icon

internal val useMockDataForStartupWizard: Boolean
  get() = SystemProperties.getBooleanProperty("intellij.startup.wizard.use-mock-data", false)

internal interface SettingsService {
  companion object {
    fun getInstance(): SettingsService = service()
  }

  fun getSyncService(): SyncService
  fun getJbService(): JbService
  fun getExternalService(): ExternalService

  suspend fun warmUp()

  suspend fun shouldShowImport(): Boolean

  val importCancelled: Signal<Unit>

  val notification: IProperty<NotificationData?>

  val jbAccount: IPropertyView<JBAccountInfoService.JBAData?>

  val isSyncEnabled: Boolean

  val hasDataToSync: IPropertyView<Boolean>

  val doClose: ISignal<Unit>

  val pluginIdsPreloaded: Boolean

  fun configChosen()
}

internal class SettingsServiceImpl(private val coroutineScope: CoroutineScope) : SettingsService, Disposable.Default {
  override fun getSyncService() =
    if (useMockDataForStartupWizard) TestSyncService.getInstance()
    else SyncServiceImpl.getInstance()

  override fun getJbService() =
    if (useMockDataForStartupWizard) TestJbService.getInstance()
    else JbImportServiceImpl.getInstance()

  override fun getExternalService(): ExternalService =
    if (useMockDataForStartupWizard) TestExternalService.getInstance()
    else SettingTransferService.getInstance()

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun warmUp() {
    coroutineScope.async {
      MarketplaceRequests.getInstance().getMarketplacePlugins(null)
    }.also { deferred ->
      deferred.invokeOnCompletion {
        if (it == null && deferred.getCompleted().isNotEmpty()) {
          logger.info("Plugin IDs from marketplace were preloaded")
          pluginIdsPreloaded = true
        }
        else if (it != null) {
          logger.warn("Couldn't preload plugin IDs from marketplace: ${it}")
        }
        else {
          logger.warn("Couldn't preload plugin IDs from marketplace")
        }
      }
    }
    coroutineScope.launch { getJbService().warmUp() }
    coroutineScope.launch { getExternalService().warmUp(coroutineScope) }
  }

  override suspend fun shouldShowImport(): Boolean {
    val startTime = System.currentTimeMillis()
    val importFromJetBrainsAvailable = coroutineScope.async {
      logger.runAndLogException { getJbService().hasDataToImport() } ?: false
    }
    val importFromExternalAvailable = coroutineScope.async {
      logger.runAndLogException { getExternalService().hasDataToImport() } ?: false
    }
    val result = select {
      importFromExternalAvailable.onAwait { it || importFromJetBrainsAvailable.await() }
      importFromJetBrainsAvailable.onAwait { it || importFromExternalAvailable.await() }
    }
    thisLogger().info("Took ${System.currentTimeMillis() - startTime}ms. to calculate shouldShowImport")
    return result
  }

  override val importCancelled: Signal<Unit> = Signal<Unit>().apply {
    advise(createLifetime()) {
      thisLogger().info("$IMPORT_SERVICE cancelImport")
    }
  }

  override val notification = Property<NotificationData?>(null)

  override val jbAccount = Property<JBAccountInfoService.JBAData?>(null)

  override val doClose = Signal<Unit>()

  @Volatile
  override var pluginIdsPreloaded: Boolean = false

  override fun configChosen() {
    if (useMockDataForStartupWizard) {
      TestJbService.getInstance().configChosen()
    } else {
      val fileChooserDescriptor = FileChooserDescriptor(true, true, false, false, false, false)
      val selectedDir = FileChooser.chooseFile(fileChooserDescriptor, null, null)
      if (selectedDir != null) {
        val prevPath = selectedDir.toNioPath()
        if (ConfigImportHelper.findConfigDirectoryByPath(prevPath) != null) {
          getJbService().importFromCustomFolder(prevPath)
        } else {
          notification.set(object : NotificationData {
            override val status = NotificationData.NotificationStatus.ERROR
            override val message = BootstrapBundle.message("import.chooser.error.unrecognized", selectedDir,
                                                           IDEData.getSelf()?.fullName ?: "Current IDE")
            override val customActionList = emptyList<NotificationData.Action>()
          })
        }
      }
    }
  }

  // override val isSyncEnabled = jbAccount.compose(unloggedSyncHide()) { account, reg -> !reg || account != null }
  override val hasDataToSync = Property(false)
  //override val hasDataToSync = jbAccount.compose(getSyncService().syncState) { account, state -> account != null && state == SyncService.SYNC_STATE.LOGGED }

  override val isSyncEnabled = System.getProperty("import.settings.sync.enabled").toBoolean()

  init {
    if (useMockDataForStartupWizard) {
      jbAccount.set(JBAccountInfoService.JBAData("Aleksey Ivanovskii", "alex.ivanovskii", "alex.ivanovskii@gmail.com", "Alex Ivanovskii"))
    }
  }
}

private val logger = logger<SettingsServiceImpl>()

interface SyncService : JbService {
  enum class SYNC_STATE {
    UNLOGGED,
    LOGGED,
    TURNED_OFF,
    NO_SYNC
  }

  val syncState: IPropertyView<SYNC_STATE>
  fun tryToLogin()
  fun syncSettings(): DialogImportData
  fun importSyncSettings(): DialogImportData
  fun getMainProduct(): Product?
}

interface ExternalService {
  val productServices: List<ExternalProductService>
  suspend fun hasDataToImport(): Boolean
  fun warmUp(scope: CoroutineScope)
}

interface ExternalProductService : BaseService {
  val productId: TransferableIdeId
  val productTitle: @NlsSafe String
}

interface JbService : BaseService {
  suspend fun hasDataToImport(): Boolean
  suspend fun warmUp()
  fun getOldProducts(): List<Product>
  fun importFromCustomFolder(folderPath: Path)
}

interface BaseService {

  fun products(): List<Product>
  fun getSettings(itemId: String): List<BaseSetting>
  fun getImportablePluginIds(itemId: String): List<String>

  fun getProductIcon(itemId: String, size: IconProductSize = IconProductSize.SMALL): Icon?

  // sync is only possible from the same product. Otherwise, we need to show import.
  // import dialog progress looks differently, if it's importing from the same product
  fun baseProduct(id: String): Boolean = true

  fun importSettings(productId: String, data: DataToApply): DialogImportData
}

class DataToApply(
  val importSettings: List<DataForSave>,
  val featuredPluginIds: List<String>
)

enum class IconProductSize(val int: Int) {
  SMALL(20),
  MIDDLE(24),
  LARGE(48)
}


interface Product : SettingsContributor {
  val version: String?
  val lastUsage: LocalDate
  val origin: SettingsImportOrigin
}

interface Config : SettingsContributor {
  val path: String
}

interface SettingsContributor {
  val id: String
  val name: @Nls String
}


interface BaseSetting {
  val id: String
  val icon: Icon
  val name: @Nls String
  val comment: @Nls String?

  /**
   * Whether the user should be allowed to turn the setting on and off.
   */
  val isConfigurable: Boolean
    get() = true
}

interface Configurable : Multiple {
}

interface Multiple : BaseSetting {
  val list: List<List<ChildSetting>>
}

interface ChildSetting {
  val id: String
  val name: @Nls String
  val leftComment: @Nls String?
  val rightComment: @Nls String?
}

data class DataForSave(val id: String, val selectedChildIds: List<String>? = null, val unselectedChildIds: List<String>? = null)

interface ImportFromProduct : DialogImportData {
  val from: DialogImportItem
  val to: DialogImportItem
}

interface DialogImportData {
  val message: @Nls String?
  val progress: ImportProgress
}

data class DialogImportItem(val item: SettingsContributor, val icon: Icon) {

  companion object {

    fun self() = DialogImportItem(
      object : SettingsContributor {
        override val id = "DialogImportItem.self"
        override val name = ApplicationNamesInfo.getInstance().getFullProductName()
      },
      getCurrentProductIcon()
    )

    @Suppress("DEPRECATION")
    private fun getCurrentProductIcon() = when {
      PlatformUtils.isAppCode() -> StartupImportIcons.IdeIcons.AC_48
      PlatformUtils.isAqua() -> StartupImportIcons.IdeIcons.Aqua_48
      PlatformUtils.isCLion() -> StartupImportIcons.IdeIcons.CL_48
      PlatformUtils.isDataGrip() -> StartupImportIcons.IdeIcons.DG_48
      PlatformUtils.isDataSpell() -> StartupImportIcons.IdeIcons.DS_48
      PlatformUtils.isGoIde() -> StartupImportIcons.IdeIcons.GO_48
      PlatformUtils.isIdeaCommunity() -> StartupImportIcons.IdeIcons.IC_48
      PlatformUtils.isIdeaUltimate() -> StartupImportIcons.IdeIcons.IU_48
      PlatformUtils.isPhpStorm() -> StartupImportIcons.IdeIcons.PS_48
      PlatformUtils.isPyCharmCommunity() -> StartupImportIcons.IdeIcons.PC_48
      PlatformUtils.isPyCharmPro() -> StartupImportIcons.IdeIcons.PY_48
      PlatformUtils.isRider() -> StartupImportIcons.IdeIcons.RD_48
      PlatformUtils.isRubyMine() -> StartupImportIcons.IdeIcons.RM_48
      PlatformUtils.isRustRover() -> StartupImportIcons.IdeIcons.RR_48
      PlatformUtils.isWebStorm() -> StartupImportIcons.IdeIcons.WS_48
      // TODO: StartupImportIcons.IdeIcons.MPS_48
      else -> {
        logger<DialogImportItem>().error("Unknown IDE: ${PlatformUtils.getPlatformPrefix()}.")
        StartupImportIcons.IdeIcons.IC_48 // fall back to IDEA Community
      }
    }
  }
}
