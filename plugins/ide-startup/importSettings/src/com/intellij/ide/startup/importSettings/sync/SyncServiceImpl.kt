package com.intellij.ide.startup.importSettings.sync

import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.settingsSync.SettingsSnapshot
import com.intellij.settingsSync.SettingsSyncMain
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*
import javax.swing.Icon

private val logger = logger<SyncServiceImpl>()

private data class ProductInfo(
  override val version: String,
  override val lastUsage: Date,
  override val id: String,
  override val name: String
) : Product {

  constructor(metaInfo: SettingsSnapshot.MetaInfo) : this(
    version = metaInfo.appInfo?.buildNumber?.asStringWithoutProductCodeAndSnapshot() ?: "",
    lastUsage = Date.from(metaInfo.dateCreated),
    id = metaInfo.appInfo?.applicationId.toString(),
    name = metaInfo.appInfo?.buildNumber?.productCode ?: ""
  )
}

@Service
internal class SyncServiceImpl(private val coroutineScope: CoroutineScope) : SyncService {

  companion object {
    fun getInstance(): SyncServiceImpl = service()
  }

  private val syncStateProperty = Property(SyncService.SYNC_STATE.UNLOGGED)
  override val syncState = syncStateProperty

  init {
    loadAmbientSyncState()
  }

  private val accountInfoService: JBAccountInfoService?
    get() = JBAccountInfoService.getInstance()

  private val settingSyncControls: SettingsSyncMain.SettingsSyncControls
    get() = SettingsSyncMain.getInstance().controls

  override fun tryToLogin(): String? {
    accountInfoService?.invokeJBALogin({ loadAmbientSyncState() }, ::loadAmbientSyncState)
    return null
  }

  override fun syncSettings(): DialogImportData {
    val progress = SettingsSyncProgress()
    coroutineScope.launch {
      performSync(settingSyncControls, progress)
    }

    return object : DialogImportData {
      override val progress = progress
    }
  }

  override fun importSyncSettings(): DialogImportData {
    TODO("Not yet implemented")
  }

  private suspend fun getSettingsSnapshot(): SettingsSnapshot? {
    return getRemoteSettingsSnapshot(settingSyncControls.remoteCommunicator)
  }

  override fun getMainProduct(): Product? {
    return logger.runAndLogException {
      // TODO: Async
      runBlockingCancellable block@{
        val snapshot = getSettingsSnapshot() ?: return@block null
        if (snapshot.metaInfo.appInfo?.buildNumber?.productCode == ApplicationInfo.getInstance().build.productCode) {
          return@block ProductInfo(snapshot.metaInfo)
        }

        null
      }
    }
  }

  override fun generalSync() {
    TODO("Not yet implemented")
  }

  override fun getOldProducts(): List<Product> {
    // TODO: Figure out what are these
    return emptyList()
  }

  override fun products(): List<Product> {
    val oneProduct = logger.runAndLogException {
      // TODO: Async
      runBlockingCancellable block@{
        val snapshot = getSettingsSnapshot() ?: return@block null
        val productCodeInCloud = snapshot.metaInfo.appInfo?.buildNumber?.productCode ?: return@block null
        if (productCodeInCloud != ApplicationInfo.getInstance().build.productCode) {
          return@block ProductInfo(snapshot.metaInfo)
        }

        null
      }
    } ?: return emptyList()

    // TODO: Figure out the server API
    return listOf(oneProduct)
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    TODO("Not yet implemented")
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon? {
    TODO("Not yet implemented")
  }

  override fun importSettings(productId: String, data: List<DataForSave>): DialogImportData {
    TODO("Not yet implemented")
  }

  private fun loadAmbientSyncState() {
    syncStateProperty.value = logger.runAndLogException block@{
      val service = accountInfoService ?: return@block SyncService.SYNC_STATE.NO_SYNC
      when {
        service.userData != null -> SyncService.SYNC_STATE.LOGGED
        else -> SyncService.SYNC_STATE.UNLOGGED
      }
    } ?: SyncService.SYNC_STATE.NO_SYNC
  }
}