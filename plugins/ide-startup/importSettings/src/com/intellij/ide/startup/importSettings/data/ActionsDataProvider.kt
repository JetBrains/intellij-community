// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider.Companion.toRelativeFormat
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.swing.Icon

interface ActionsDataProvider<T : BaseService> {
  enum class popUpPlace {
    MAIN,
    OTHER
  }

  companion object {

    fun prepareMap(service: JbService): Map<popUpPlace, List<Product>> {
      val fresh = service.products()
      val old = service.getOldProducts()

      if (fresh.isEmpty()) {
        return mutableMapOf<popUpPlace, List<Product>>().apply {
          this[popUpPlace.MAIN] = old
        }
      }

      return mutableMapOf<popUpPlace, List<Product>>().apply {
        this[popUpPlace.MAIN] = fresh
        this[popUpPlace.OTHER] = old
      }
    }

    fun LocalDate.toRelativeFormat(suffix: String): String {
      val now = LocalDate.now()
      val daysBetween = ChronoUnit.DAYS.between(this, now)

      return if (daysBetween == 0L) {
        ImportSettingsBundle.message("date.format.today.$suffix")
      }
      else if (daysBetween == 1L) {
        ImportSettingsBundle.message("date.format.yesterday.$suffix")
      }
      else if (daysBetween <= 6) {
        ImportSettingsBundle.message("date.format.n.days.ago.$suffix", daysBetween)
      }
      else if (daysBetween <= 27) {
        val weeks = daysBetween / 7
        if (weeks == 1L) ImportSettingsBundle.message("date.format.n.weeks.ago.one.$suffix")
        else
          ImportSettingsBundle.message("date.format.n.weeks.ago.$suffix", weeks)
      }
      else if (daysBetween <= 345) {
        val months = daysBetween / 30
        if(months == 1L) {
          ImportSettingsBundle.message("date.format.n.months.ago.one.$suffix")
        } else ImportSettingsBundle.message("date.format.n.months.ago.$suffix", months)
      }
      else {
        val years = daysBetween / 365
        if(years == 1L) {
          ImportSettingsBundle.message("date.format.n.years.ago.one.$suffix")
        } else {
          ImportSettingsBundle.message("date.format.n.years.ago.$suffix", years)
        }
      }
    }

  }

  val settingsService
    get() = SettingsService.getInstance()

  val productService: T
  fun getProductIcon(productId: String, size: IconProductSize = IconProductSize.SMALL): Icon?
  fun getText(contributor: SettingsContributor): @Nls String
  val title: String

  fun getComment(contributor: SettingsContributor): @Nls String?
  val main: List<Product>?
  val other: List<Product>?
}

class JBrActionsDataProvider private constructor() : ActionsDataProvider<JbService> {
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

  override fun getText(contributor: SettingsContributor): String {
    return contributor.name
  }

  override val title: @Nls String
    get() = ImportSettingsBundle.message("jetbrains.ides")

  override fun getComment(contributor: SettingsContributor): String? {
    if (contributor is Config) {
      return contributor.path
    }
    if (contributor is Product) {
      return contributor.lastUsage.toRelativeFormat("used")
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
    updateSyncMap()
  }

  private fun updateSyncMap() {
    val service = settingsService.getSyncService()
    if (!settingsService.isLoggedIn()) {
      map = null
      return
    }

    service.getMainProduct()?.let {
      map = mutableMapOf<ActionsDataProvider.popUpPlace, List<Product>>().apply {
        this[ActionsDataProvider.popUpPlace.MAIN] = listOf(it)
        val products = service.products()
        if (products.isNotEmpty()) {
          this[ActionsDataProvider.popUpPlace.OTHER] = products
        }
      }
      return
    }

    map = ActionsDataProvider.prepareMap(service)
  }

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return productService.getProductIcon(productId, size)
  }

  override fun getText(contributor: SettingsContributor): String {
    return "${contributor.name} Setting Sync"
  }

  override fun getComment(contributor: SettingsContributor): String? {
    if (contributor is Product) {
      return contributor.lastUsage.toRelativeFormat("synced")
    }
    return null
  }

  override val title: String
    get() = "Setting Sync"

  override val main: List<Product>?
    get() {
      if (map == null) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.MAIN)
    }
  override val other: List<Product>?
    get() {
      if (map == null) {
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

  override fun getText(contributor: SettingsContributor): String {
    return contributor.name
  }

  override fun getComment(contributor: SettingsContributor): String? {
    return null
  }

  override val title: String
    get() = ""
  override val main: List<Product>?
    get() = productService.products()
  override val other: List<Product>?
    get() = null

}

