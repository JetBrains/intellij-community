// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.sync

import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.CoroutineScope
import java.time.Duration
import java.time.LocalDate
import javax.swing.Icon

private val logger = logger<SyncServiceImpl>()

private data class ProductInfo(
  override val version: String,
  override val lastUsage: LocalDate,
  override val id: String,
  override val name: String
) : Product {

  //constructor(metaInfo: SettingsSnapshot.MetaInfo) : this(
  //  version = metaInfo.appInfo?.buildNumber?.asStringWithoutProductCodeAndSnapshot() ?: "",
  //  lastUsage = Date.from(metaInfo.dateCreated),
  //  id = metaInfo.appInfo?.applicationId.toString(),
  //  name = metaInfo.appInfo?.buildNumber?.productCode ?: ""
  //)
}

private val serverRequestTimeout = Duration.ofMinutes(2L)

@Service
internal class SyncServiceImpl(private val coroutineScope: CoroutineScope) : SyncService {

  companion object {
    fun getInstance(): SyncServiceImpl = service()
  }

  override fun hasDataToImport() = syncState.value == SyncService.SYNC_STATE.LOGGED

  override val syncState = Property(SyncService.SYNC_STATE.UNLOGGED)

  init {
    loadAmbientSyncState()
  }

  private val accountInfoService: JBAccountInfoService?
    get() = JBAccountInfoService.getInstance()

  //private val settingSyncControls: SettingsSyncMain.SettingsSyncControls
  //  get() = SettingsSyncMain.getInstance().controls

  override fun tryToLogin(): String? {
    accountInfoService?.invokeJBALogin({ loadAmbientSyncState() }, ::loadAmbientSyncState)
    return null
  }

  override fun syncSettings(): DialogImportData {
    TODO()
    //val progress = SettingsSyncProgress()
    //coroutineScope.launch {
    //  performSync(settingSyncControls, progress)
    //}
    //
    //return object : DialogImportData {
    //  override val progress = progress
    //}
  }

  override fun importSyncSettings(): DialogImportData {
    TODO("Not yet implemented")
  }

  //private suspend fun getSettingsSnapshot(): SettingsSnapshot? {
  //  return getRemoteSettingsSnapshot(settingSyncControls.remoteCommunicator)
  //}

  override fun getMainProduct(): Product? {
    return null
    //return logger.runAndLogException {
    //  @Suppress("SSBasedInspection") // TODO: Async
    //  runBlocking {
    //    withTimeout(serverRequestTimeout) block@{
    //      val snapshot = getSettingsSnapshot() ?: return@block null
    //      if (snapshot.metaInfo.appInfo?.buildNumber?.productCode == ApplicationInfo.getInstance().build.productCode) {
    //        return@block ProductInfo(snapshot.metaInfo)
    //      }
    //
    //      null
    //    }
    //  }
    //}
  }

  override fun generalSync() {
    TODO("Not yet implemented")
  }

  override fun getOldProducts(): List<Product> {
    // TODO: Figure out what are these
    return emptyList()
  }

  override fun products(): List<Product> {
    return emptyList()
    //val oneProduct = logger.runAndLogException {
    //  // TODO: Async
    //  runBlockingCancellable block@{
    //    val snapshot = getSettingsSnapshot() ?: return@block null
    //    val productCodeInCloud = snapshot.metaInfo.appInfo?.buildNumber?.productCode ?: return@block null
    //    if (productCodeInCloud != ApplicationInfo.getInstance().build.productCode) {
    //      return@block ProductInfo(snapshot.metaInfo)
    //    }
    //
    //    null
    //  }
    //} ?: return emptyList()
    //
    //// TODO: Figure out the server API
    //return listOf(oneProduct)
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
    syncState.value = logger.runAndLogException block@{
      val service = accountInfoService ?: return@block SyncService.SYNC_STATE.NO_SYNC
      when {
        service.userData != null -> SyncService.SYNC_STATE.LOGGED
        else -> SyncService.SYNC_STATE.UNLOGGED
      }
    } ?: SyncService.SYNC_STATE.NO_SYNC
  }
}