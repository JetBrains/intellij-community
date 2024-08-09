// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider.Companion.toRelativeFormat
import com.intellij.ide.startup.importSettings.jb.JbProductInfo
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.ide.startup.importSettings.transfer.ExternalProductInfo
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.swing.Icon

interface ActionsDataProvider<T : BaseService> {
  enum class popUpPlace {
    MAIN,
    OTHER
  }

  companion object {

    fun prepareMap(service: JbService): Map<popUpPlace, List<Product>>? {
      val fresh = service.products()
      val old = service.getOldProducts()

      if (fresh.isEmpty()) {
        return if (old.isEmpty()) {
          null
        } else
          mutableMapOf<popUpPlace, List<Product>>().apply {
            this[popUpPlace.MAIN] = old }
      }

      return mutableMapOf<popUpPlace, List<Product>>().apply {
        if (fresh.isNotEmpty()) {
          this[popUpPlace.MAIN] = fresh
        }
        if (old.isNotEmpty()) {
          this[popUpPlace.OTHER] = old
        }
      }
    }

    fun LocalDate.toRelativeFormat(suffix: String): String {
      val daysBetween = ChronoUnit.DAYS.between(this, LocalDate.now())
      if (daysBetween == 0L) {
        return ImportSettingsBundle.message("date.format.today.$suffix")
      }
      else if (daysBetween == 1L) {
        return ImportSettingsBundle.message("date.format.yesterday.$suffix")
      }
      val (chronoUnit, cnt) = getRelativeInterval(daysBetween)
      return if (chronoUnit == ChronoUnit.DAYS) {
        ImportSettingsBundle.message("date.format.n.days.ago.$suffix", daysBetween)
      }
      else if (chronoUnit == ChronoUnit.WEEKS) {
        if (cnt == 1L) {
          ImportSettingsBundle.message("date.format.n.weeks.ago.one.$suffix")
        }
        else {
          ImportSettingsBundle.message("date.format.n.weeks.ago.$suffix", cnt)
        }
      }
      else if (chronoUnit == ChronoUnit.MONTHS) {
        if (cnt == 1L) {
          ImportSettingsBundle.message("date.format.n.months.ago.one.$suffix")
        }
        else {
          ImportSettingsBundle.message("date.format.n.months.ago.$suffix", cnt)
        }
      }
      else if (chronoUnit == ChronoUnit.YEARS) {
        if (cnt == 1L) {
          ImportSettingsBundle.message("date.format.n.years.ago.one.$suffix")
        }
        else {
          ImportSettingsBundle.message("date.format.n.years.ago.$suffix", cnt)
        }
      }
      else {
        ImportSettingsBundle.message("date.format.n.days.ago.$suffix", daysBetween)
      }
    }

    private fun getRelativeInterval(daysBetween: Long): Pair<ChronoUnit, Long> {
      return when {
        daysBetween < 7 -> ChronoUnit.DAYS to daysBetween
        daysBetween < 30 -> ChronoUnit.WEEKS to (daysBetween / 7)
        daysBetween < 365 -> ChronoUnit.MONTHS to (daysBetween / 30)
        else -> ChronoUnit.YEARS to (daysBetween / 365)
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

  // callback actions
  fun productSelected(contributor: SettingsContributor)
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

  override fun productSelected(contributor: SettingsContributor) {
    val actual = main?.contains(contributor) == true
    val productCodeName = (contributor as? JbProductInfo)?.codeName ?: ""
    ImportSettingsEventsCollector.jbIdeSelected(productCodeName, actual)
  }
}

class SyncActionsDataProvider private constructor(lifetime: Lifetime) : ActionsDataProvider<SyncService> {
  companion object {
    private var provider: SyncActionsDataProvider? = null
    fun createProvider(lifetime: Lifetime): SyncActionsDataProvider {
      val inst = provider ?: SyncActionsDataProvider(lifetime)
      provider = inst
      return inst
    }
  }

  override val productService = settingsService.getSyncService()
  private var map: Map<ActionsDataProvider.popUpPlace, List<Product>?>? = null

  init {
    productService.syncState.advise(lifetime) {
      updateSyncMap()
    }
    updateSyncMap()
  }

  private fun updateSyncMap() {
    val service = settingsService.getSyncService()
    if (!settingsService.isSyncEnabled || !settingsService.hasDataToSync.value) {
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
      if (map.isNullOrEmpty()) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.MAIN)
    }
  override val other: List<Product>?
    get() {
      if (map.isNullOrEmpty()) {
        updateSyncMap()
      }
      return map?.get(ActionsDataProvider.popUpPlace.OTHER)
    }

  override fun productSelected(contributor: SettingsContributor) {
    // TODO implement with the sync
  }
}

class ExtActionsDataProvider(override val productService: ExternalProductService) : ActionsDataProvider<ExternalProductService> {

  override fun getProductIcon(productId: String, size: IconProductSize): Icon? {
    return productService.getProductIcon(productId, size)
  }

  override fun getText(contributor: SettingsContributor): String {
    return contributor.name
  }

  override fun getComment(contributor: SettingsContributor): String? =
    (contributor as? ExternalProductInfo)?.comment

  override val title: String
    get() = productService.productTitle
  override val main: List<Product>
    get() = productService.products()
  override val other: List<Product>? = null
  override fun productSelected(contributor: SettingsContributor) {
    val product = contributor as? ExternalProductInfo ?: return
    ImportSettingsEventsCollector.externalSelected(product.transferableId)
  }
}

