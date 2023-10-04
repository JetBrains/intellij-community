package com.intellij.ide.startup.importSettings.sync

import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.settingsSync.SettingsSyncMain
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*
import javax.swing.Icon

private val logger = logger<SyncServiceImpl>()

class SyncServiceImpl(private val coroutineScope: CoroutineScope) : SyncService {

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

  override fun getMainProduct(): Product? {
    return logger.runAndLogException {
      // TODO: Async
      runBlockingCancellable {
        val result = getRemoteProductInfo(settingSyncControls.remoteCommunicator)
        result?.let {
          object : Product {
            override val version: String
              get() = result.metaInfo.appInfo?.buildNumber?.asStringWithoutProductCodeAndSnapshot() ?: ""
            override val lastUsage: Date
              get() = Date.from(result.metaInfo.dateCreated)
            override val id: String
              get() = result.metaInfo.appInfo?.applicationId.toString()
            override val name: String
              get() = result.metaInfo.appInfo?.buildNumber?.productCode ?: ""
          }
        }
      }
    }
  }

  override fun generalSync() {
    TODO("Not yet implemented")
  }

  override fun getOldProducts(): List<Product> {
    TODO("Not yet implemented")
  }

  override fun products(): List<Product> {
    TODO("Not yet implemented")
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