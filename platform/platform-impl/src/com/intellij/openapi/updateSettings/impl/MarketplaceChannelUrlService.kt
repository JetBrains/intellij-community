// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 *  Service for checking if a given URL is a marketplace channel URL.
 *  Introduced solely to simplify testing
 */
@ApiStatus.Internal
interface MarketplaceChannelUrlService {
  companion object {
    fun getInstance(): MarketplaceChannelUrlService = service()

    @TestOnly
    fun createInstance(marketplaceBaseUrl: String): MarketplaceChannelUrlService {
      return MarketplaceChannelUrlServiceImpl(marketplaceBaseUrl)
    }
  }

  fun isMarketplaceChannelUrl(url: String): Boolean
}


private const val BEGINNING_OF_MARKETPLACE_URL = "https://plugins.jetbrains.com/plugins/"

internal class MarketplaceChannelUrlServiceImpl(val baseUrl: String = BEGINNING_OF_MARKETPLACE_URL) : MarketplaceChannelUrlService {

  override fun isMarketplaceChannelUrl(url: String): Boolean {
    if (!url.startsWith(baseUrl)) return false
    var numberOfDigits = 0
    var index = url.length - 1
    while (url[index].isDigit()) {
      numberOfDigits++
      index--
    }
    if (numberOfDigits == 0) return false
    if (url[index] != '/') return false
    val channelName = url.subSequence(baseUrl.length + 1, index)
    return !channelName.isEmpty() && !channelName.contains("/")
  }
}
