// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.productInfo

import com.intellij.openapi.components.service
import com.intellij.platform.buildData.productInfo.ProductInfoData
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Provides access to information from product-info.json from the IDE installation.
 */
@ApiStatus.Internal
interface IdeProductInfo {
  /**
   * Returns data for the current IDE process.
   * 
   * If the IDE is started from source code, real product-info.json won't be generated, so a synthetic instance where some fields are
   * missing will be returned.
   */
  val currentProductInfo: ProductInfoData

  /**
   * Loads data from product-info.json file for the IDE installed at [ideHome] or `null` if the data cannot be loaded.
   */
  fun loadProductInfo(ideHome: Path): ProductInfoData?
  
  companion object {
    @JvmStatic
    fun getInstance(): IdeProductInfo = service<IdeProductInfo>() 
  }
}