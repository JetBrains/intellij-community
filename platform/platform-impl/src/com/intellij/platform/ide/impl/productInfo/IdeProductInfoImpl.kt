// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.productInfo

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.buildData.productInfo.ProductInfoData
import com.intellij.platform.ide.productInfo.IdeProductInfo
import com.intellij.util.system.OS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name

internal class IdeProductInfoImpl : IdeProductInfo {
  private val json = Json { ignoreUnknownKeys = true }

  override val currentProductInfo: ProductInfoData by lazy { 
    if (PluginManagerCore.isRunningFromSources() || AppMode.isDevServer()) {
      createProductInfoFromApplicationInfo()
    }
    else {
      tryLoadingProductInfo(PathManager.getHomeDir())
    }
  }

  override fun loadProductInfo(ideHome: Path): ProductInfoData? = try {
    if (OS.CURRENT == OS.macOS && ideHome.name != "Contents" && ideHome.resolve("Contents").isDirectory()) {
      tryLoadingProductInfo(ideHome.resolve("Contents"))
    }
    else {
      tryLoadingProductInfo(ideHome)
    }
  }
  catch (e: Exception) {
    logger<IdeProductInfoImpl>().warn("Cannot load product info from ${ideHome}", e)
    null
  }

  @OptIn(ExperimentalSerializationApi::class)
  private fun tryLoadingProductInfo(ideHome: Path): ProductInfoData {
    val productInfoRelativePath =
      // the cross-platform distribution used for plugin development has the product-info.json file at the root even on macOS
      if (OS.CURRENT == OS.macOS && ideHome.resolve(ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC).exists()) ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC
      else ApplicationEx.PRODUCT_INFO_FILE_NAME
    val productInfoPath = ideHome.resolve(productInfoRelativePath)
    return productInfoPath.inputStream().buffered().use {
      json.decodeFromStream(ProductInfoData.serializer(), it)
    }
  }

  private fun createProductInfoFromApplicationInfo(): ProductInfoData {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    return ProductInfoData.create(
      name = appInfo.fullApplicationName,
      version = appInfo.fullVersion,
      versionSuffix = null,
      buildNumber = appInfo.build.asStringWithoutProductCode(),
      productCode = appInfo.build.productCode,
      envVarBaseName = "UNKNOWN",
      dataDirectoryName = "UNKNOWN",
      svgIconPath = null,
      productVendor = appInfo.shortCompanyName,
      majorVersionReleaseDate = appInfo.majorReleaseBuildDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
      launch = emptyList(),
      customProperties = emptyList(),
      bundledPlugins = emptyList(),
      modules = emptyList(),
      fileExtensions = emptyList(),
      flavors = emptyList(),
      layout = emptyList(),
    )
  }
}
