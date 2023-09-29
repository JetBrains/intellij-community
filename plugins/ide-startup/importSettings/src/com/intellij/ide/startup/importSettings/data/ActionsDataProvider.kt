// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface ActionsDataProvider<T : BaseService> {
  enum class popUpPlace {
    MAIN,
    OTHER
  }

  companion object {

    fun prepareMap(service: BaseJbService): Map<popUpPlace, List<Product>> {
      val fresh = service.products()
      val old = service.getOldProducts()

      if(fresh.isEmpty()) {
        return mutableMapOf<popUpPlace, List<Product>>().apply {
          this[popUpPlace.MAIN] = old
        }
      }

      return mutableMapOf<popUpPlace, List<Product>>().apply {
        this[popUpPlace.MAIN] = fresh
        this[popUpPlace.OTHER] = old
      }
    }
  }
  val settingsService
    get() = SettingsService.getInstance()

  val productService: T
  fun getProductIcon(productId: String, size: IconProductSize = IconProductSize.SMALL): Icon?
  fun getText(importItem: ImportItem): String
  val title: String

  fun getComment(importItem: ImportItem): String?
  val main: List<Product>?
  val other: List<Product>?
}

class JBrActionsDataProvider private constructor(): ActionsDataProvider<JbService> {
  companion object {
    private val provider = JBrActionsDataProvider()
    fun getInstance() = provider
  }

  override val productService = settingsService.getJbService()
  private var map: Map<ActionsDataProvider.popUpPlace, List<Product>?>? = null

  init {
    map = ActionsDataProvider.prepareMap(productService)
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return productService.getProductIcon(productId, size)
  }

  override fun getText(importItem: ImportItem): String {
    return importItem.name
  }

  override val title: @Nls String
    get() = ImportSettingsBundle.message("jetbrains.ides")

  override fun getComment(importItem: ImportItem): String? {
    if(importItem is Config) {
      return importItem.path
    }
    if(importItem is Product) {
      return importItem.lastUsage.toString()
    }
    return null
  }

  override val main: List<Product>?
    get() = map?.get(ActionsDataProvider.popUpPlace.MAIN)
  override val other: List<Product>?
    get() = map?.get(ActionsDataProvider.popUpPlace.OTHER)

}

class SyncActionsDataProvider private constructor() : ActionsDataProvider<SyncService> {
  companion object {
    private val provider = SyncActionsDataProvider()
    fun getInstance() = provider
  }

  override val productService = settingsService.getSyncService()
  private var map: Map<ActionsDataProvider.popUpPlace, List<Product>?>? = null

  init {
    map = ActionsDataProvider.prepareMap(productService)
  }

  private fun updateSyncMap() {
    val service = settingsService.getSyncService()
    if (service.syncState != SyncService.SYNC_STATE.LOGGED) {
      map = null
      return
    }

    service.getMainProduct()?.let {
      map = mutableMapOf<ActionsDataProvider.popUpPlace, List<Product>>().apply {
        this[ActionsDataProvider.popUpPlace.MAIN] = listOf(it)
        val products = service.products()
        if(products.isNotEmpty()) {
          this[ActionsDataProvider.popUpPlace.OTHER] = products
        }
      }
      return
    }

    map = ActionsDataProvider.prepareMap(service)
  }

  override fun getProductIcon(itemId: String, size: IconProductSize): Icon? {
    return productService.getProductIcon(itemId, size)
  }

  override fun getText(importItem: ImportItem): String {
    return "${importItem.name} Setting Sync"
  }

  override fun getComment(importItem: ImportItem): String? {
    if(importItem is Product) {
      return importItem.lastUsage.toString()
    }
    return null
  }

  override val title: String
    get() = "Setting Sync"

  override val main: List<Product>?
    get() {
      if(map == null) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.MAIN)
    }
  override val other: List<Product>?
    get() {
      if(map == null) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.OTHER)
    }

}

class ExtActionsDataProvider private constructor() : ActionsDataProvider<ExternalService> {
  companion object {
    private val provider = ExtActionsDataProvider()
    fun getInstance() = provider
  }
  override val productService = settingsService.getExternalService()

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return productService.getProductIcon(productId, size)
  }

  override fun getText(importItem: ImportItem): String {
    return importItem.name
  }

  override fun getComment(importItem: ImportItem): String? {
    return null
  }

  override val title: String
    get() = ""
  override val main: List<Product>?
    get() = productService.products()
  override val other: List<Product>?
    get() = null

}

