package com.intellij.ide.startup.importSettings.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.rd.util.reactive.Property
import javax.swing.Icon

private val logger = logger<SyncServiceImpl>()

class SyncServiceImpl : SyncService {

  private val syncStateProperty = Property(SyncService.SYNC_STATE.UNLOGGED)
  override val syncState = syncStateProperty

  init {
    loadAmbientSyncState()
  }

  private val accountInfoService: JBAccountInfoService?
    get() = JBAccountInfoService.getInstance()

  override fun tryToLogin(): String? {
    accountInfoService?.invokeJBALogin({ loadAmbientSyncState() }, ::loadAmbientSyncState)
    return null
  }

  override fun syncSettings(): DialogImportData {
    TODO("Not yet implemented")
  }

  override fun importSyncSettings(): DialogImportData {
    TODO("Not yet implemented")
  }

  override fun getMainProduct(): Product? {
    TODO("Not yet implemented")
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