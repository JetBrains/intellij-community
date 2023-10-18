// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.sync.SyncServiceImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.SystemProperties
import com.jetbrains.rd.swing.proxyProperty
import com.jetbrains.rd.util.reactive.*
import java.util.*
import javax.swing.Icon

interface SettingsService {
  companion object {
    fun getInstance(): SettingsService = service()
  }
  fun getSyncService(): SyncService
  fun getJbService(): JbService
  fun getExternalService(): ExternalService

  fun skipImport()

  val error: ISignal<NotificationData>

  val jbAccount: IPropertyView<JBAccountInfoService.JBAData?>

  val isSyncEnabled: IPropertyView<Boolean>
}

class SettingsServiceImpl : SettingsService {

  private val shouldUseMockData = SystemProperties.getBooleanProperty("intellij.startup.wizard.use-mock-data", true)

  override fun getSyncService() =
    if (shouldUseMockData) TestSyncService()
    else SyncServiceImpl.getInstance()

  override fun getJbService() = TestJbService()

  override fun getExternalService() = TestExternalService()

  override fun skipImport() = thisLogger().info("$IMPORT_SERVICE skipImport")

  override val error = Signal<NotificationData>()

  override val jbAccount = Property<JBAccountInfoService.JBAData?>(null)

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

  override val isSyncEnabled = jbAccount.compose(unloggedSyncHide()) { account, reg -> !reg || account != null }

  init {
    jbAccount.set(JBAccountInfoService.JBAData("Aleksey Ivanovskii", "alex.ivanovskii", "alex.ivanovskii@gmail.com"))
  }
}


interface SyncService : BaseJbService {
  enum class SYNC_STATE {
    UNLOGGED,
    WAINING_FOR_LOGIN,
    LOGIN_FAILED,
    LOGGED,
    TURNED_OFF,
    NO_SYNC,
    GENERAL
  }

  @Deprecated("Use getJbAccount from SettingsService", ReplaceWith("jbAccount", "com.intellij.ide.startup.importSettings.data.SettingsService"))
  fun isLoggedIn(): Boolean {
    return syncState.value != SYNC_STATE.UNLOGGED
  }

  val syncState: IPropertyView<SYNC_STATE>
  fun tryToLogin(): String?
  fun syncSettings(): DialogImportData
  fun importSyncSettings(): DialogImportData
  fun getMainProduct(): Product?
  fun generalSync()
}

interface ExternalService : BaseService
interface JbService: BaseJbService {
  fun getConfig(): Config
}

interface BaseJbService : BaseService {
  fun getOldProducts(): List<Product>
}

interface BaseService {

  fun products(): List<Product>
  fun getSettings(itemId: String): List<BaseSetting>

  fun getProductIcon(itemId: String, size: IconProductSize = IconProductSize.SMALL): Icon?

  fun baseProduct(id: String): Boolean = true /* синк возможет только из того же продукта. в противном случае мне нужно показать импорт
  диалог прогресса импорта выглядит иначе если импорт того же продукта */

  fun importSettings(productId: String, data: List<DataForSave>): DialogImportData
}

enum class IconProductSize(val int: Int) {
  SMALL(20),
  MIDDLE(24),
  LARGE(48)
}


interface Product : SettingsContributor {
  val version: String
  val lastUsage: Date
}

interface Config : SettingsContributor {
  val path: String /* /IntelliJ IDEA Ultimate 2023.2.1 */
}

interface SettingsContributor {
  val id: String
  val name: String
}


interface BaseSetting {
  val id: String

  val icon: Icon
  val name: String
  val comment: String?
}

interface Configurable : Multiple {
  /* https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=1420%3A237610&mode=dev */
}

interface Multiple : BaseSetting {
  /* это список с настройками данного сеттинга. например кеймапа с ключами. плагины. этот интерфейс обозначает только наличие дочерних настроек.
  для этого интерфейса есть расширение Configurable которое применимо, если В ТЕОРИИ эти настройки можно выбирать.
  в теории потому что для того чтобы показалась выпадашка с выбором нужно чтобы самый верхний сервис предоставлял возможность редактирования.
  например ImportService позвозяет выбирать\редактировать, SettingsService - нет. в случае если редактирование невозможно Configurable -ы
  в диалоге будут выглядеть как Multiple
   https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169735&mode=dev */
  val list: List<List<ChildSetting>>
}

interface ChildSetting {
  val id: String
  val name: String
  val leftComment: String? /* built-in скетч: https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169853&mode=dev */
  val rightComment: String? /* hotkey скетч https://www.figma.com/file/7lzmMqhEETFIxMg7E2EYSF/Import-settings-and-Settings-Sync-UX-2507?node-id=961%3A169735&mode=dev*/
}

data class DataForSave(val id: String, val childIds: List<String>? = null)

interface  ImportFromProduct: DialogImportData {
  val from: DialogImportItem
  val to: DialogImportItem
}

interface DialogImportData {
  val message: String?
  val progress : ImportProgress
}

interface ImportProgress {
  val progressMessage: IPropertyView<String?>
  val progress: IOptPropertyView<Int>
}


data class DialogImportItem(val item: SettingsContributor, val icon: Icon)

