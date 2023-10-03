package com.intellij.ide.startup.importSettings.data

class SettingsServiceImpl : SettingsService {

  private val syncService = SyncServiceImpl()
  private val jbService = TestJbService()
  private val externalService = TestExternalService()

  override fun getSyncService() = syncService
  override fun getJbService() = jbService
  override fun getExternalService() = externalService

  override fun skipImport() {
    TODO("Not yet implemented")
  }
}