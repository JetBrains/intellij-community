// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.jb.JbImportServiceImpl
import com.intellij.ide.startup.importSettings.sync.SyncServiceImpl
import com.intellij.ide.startup.importSettings.transfer.SettingTransferService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.jetbrains.rd.swing.proxyProperty
import com.jetbrains.rd.util.reactive.*
import org.jetbrains.annotations.Nls
import java.time.LocalDate
import javax.swing.Icon

interface SettingsService {
  companion object {
    fun getInstance(): SettingsService = service()
  }

  fun getSyncService(): SyncService
  fun getJbService(): JbService
  fun getExternalService(): ExternalService

  suspend fun shouldShowImport(): Boolean

  val importCancelled: Signal<Unit>

  val error: ISignal<NotificationData>

  val jbAccount: IPropertyView<JBAccountInfoService.JBAData?>

  val isSyncEnabled: IPropertyView<Boolean>

  val doClose: ISignal<Unit>

  fun isLoggedIn(): Boolean = jbAccount.value != null
}

class SettingsServiceImpl : SettingsService, Disposable.Default {

  private val shouldUseMockData = SystemProperties.getBooleanProperty("intellij.startup.wizard.use-mock-data", false)

  override fun getSyncService() =
    if (shouldUseMockData) TestSyncService()
    else SyncServiceImpl.getInstance()

  override fun getJbService() =
    if (shouldUseMockData) TestJbService()
    else JbImportServiceImpl.getInstance()

  override fun getExternalService(): ExternalService =
    if (shouldUseMockData) TestExternalService()
    else SettingTransferService.getInstance()

  override suspend fun shouldShowImport(): Boolean {
    return getJbService().hasDataToImport()
           || getExternalService().hasDataToImport()
  }

  override val importCancelled = Signal<Unit>().apply {
    advise(createLifetime()) {
      thisLogger().info("$IMPORT_SERVICE cancelImport")
    }
  }

  override val error = Signal<NotificationData>()

  override val jbAccount = Property<JBAccountInfoService.JBAData?>(null)

  override val doClose = Signal<Unit>()

  private fun unloggedSyncHide(): IPropertyView<Boolean> {
    fun getValue(): Boolean = Registry.`is`("import.setting.unlogged.sync.hide")

    return proxyProperty(getValue()) { lifetime, set ->
      val listener = object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          set(value.asBoolean())
        }
      }

      Registry.get("import.setting.unlogged.sync.hide").addListener(listener, lifetime.createNestedDisposable())
    }
  }

  override val isSyncEnabled = Property(shouldUseMockData) //jbAccount.compose(unloggedSyncHide()) { account, reg -> !reg || account != null }

  init {
    if (shouldUseMockData) {
      jbAccount.set(JBAccountInfoService.JBAData("Aleksey Ivanovskii", "alex.ivanovskii", "alex.ivanovskii@gmail.com"))
    }
  }
}


interface SyncService : JbService {
  enum class SYNC_STATE {
    UNLOGGED,
    WAINING_FOR_LOGIN,
    LOGIN_FAILED,
    LOGGED,
    TURNED_OFF,
    NO_SYNC,
    GENERAL
  }

  val syncState: IPropertyView<SYNC_STATE>
  fun tryToLogin(): String?
  fun syncSettings(): DialogImportData
  fun importSyncSettings(): DialogImportData
  fun getMainProduct(): Product?
  fun generalSync()
}

interface ExternalService : BaseService {
  suspend fun hasDataToImport(): Boolean
  suspend fun warmUp()
}

interface JbService : BaseService {
  fun hasDataToImport(): Boolean
  fun getOldProducts(): List<Product>
}

interface BaseService {

  fun products(): List<Product>
  fun getSettings(itemId: String): List<BaseSetting>

  fun getProductIcon(itemId: String, size: IconProductSize = IconProductSize.SMALL): Icon?

  // sync is only possible from the same product. Otherwise, we need to show import.
  // import dialog progress looks differently, if it's importing from the same product
  fun baseProduct(id: String): Boolean = true

  fun importSettings(productId: String, data: List<DataForSave>): DialogImportData
}

enum class IconProductSize(val int: Int) {
  SMALL(20),
  MIDDLE(24),
  LARGE(48)
}


interface Product : SettingsContributor {
  val version: String?
  val lastUsage: LocalDate
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

data class DataForSave(val id: String, val childIds: List<String>? = null)

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
