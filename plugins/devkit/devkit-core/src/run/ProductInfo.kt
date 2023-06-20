// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * @return `null` if no `product-info.json` could be found in given IDE installation or on errors
 */
@Suppress("UNCHECKED_CAST")
internal fun loadProductInfo(ideaJdkHome: String): ProductInfo? {
  val ideHomePath = Path.of(ideaJdkHome)
  val productInfoJsonPath = when {
    SystemInfo.isMac -> ideHomePath.resolve(ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC)
    else -> ideHomePath.resolve(ApplicationEx.PRODUCT_INFO_FILE_NAME)
  }
  if (Files.notExists(productInfoJsonPath)) return null

  try {
    val jsonRoot = JsonUtil.nextObject(JsonReaderEx(Files.readString(productInfoJsonPath)))
    val launch = jsonRoot["launch"] as? MutableList<*> ?: return null
    val launchMap = launch.firstOrNull() as? MutableMap<*, *> ?: return null
    val bootClassPathJarNames = launchMap["bootClassPathJarNames"] as? MutableList<String> ?: return null

    var additionalJvmArguments = launchMap["additionalJvmArguments"] as? MutableList<String> ?: return null
    additionalJvmArguments = additionalJvmArguments.map { s: String ->
      s.replace("\$APP_PACKAGE", ideaJdkHome)
        .replace("\$IDE_HOME", ideaJdkHome)
        .replace("%IDE_HOME%", ideaJdkHome)
        .replace("Contents/Contents", "Contents")
    }.toMutableList()

    return ProductInfo(bootClassPathJarNames, additionalJvmArguments)
  }
  catch (e: Throwable) {
    logger<ProductInfo>().error("error parsing '$productInfoJsonPath'", e)
    return null
  }
}

internal data class ProductInfo(val bootClassPathJarNames: List<String>, val additionalJvmArguments: List<String>)
