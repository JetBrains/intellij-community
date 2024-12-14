// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.productInfo

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.buildData.productInfo.ProductInfoData
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@TestApplication
class IdeProductInfoTest {
  private val basePath = PathManagerEx.findFileUnderCommunityHome("platform/platform-tests/testData/productInfo").toPath()
  
  @Test
  fun `idea 2024_3_1`() {
    assumeTrue(SystemInfo.isLinux || SystemInfo.isWindows)
    val productInfo = loadProductInfo("idea-2024.3.1-Linux")
    assertEquals("2024.3.1", productInfo.version)
    assertEquals("IDEA", productInfo.envVarBaseName)
    assertEquals("JetBrains", productInfo.productVendor)
    assertThat(productInfo.launch.single().customCommands).hasSize(2)
    assertThat(productInfo.modules).contains("com.intellij.modules.java")
  }
  
  @Test
  fun `idea 2024_3_1 on macOS`() {
    assumeTrue(SystemInfo.isMac)
    assertEquals("2024.3.1", loadProductInfo("idea-2024.3.1-macOS").version)
    assertEquals("2024.3.1", loadProductInfo("idea-2024.3.1-macOS/Contents").version)
  }
  
  @Test
  fun `idea community 2022_1_4`() {
    assumeTrue(SystemInfo.isLinux || SystemInfo.isWindows)
    val productInfo = loadProductInfo("idea-ic-2022.1.4-Linux-no-jbr")
    assertEquals("2022.1.4", productInfo.version)
  }

  @Test
  fun `idea 2020_1_4`() {
    assumeTrue(SystemInfo.isLinux || SystemInfo.isWindows)
    val productInfo = loadProductInfo("idea-2020.1.4-Linux")
    assertEquals("2020.1.4", productInfo.version)
  }
  
  @Test
  fun `idea future version with unknown property`() {
    assumeTrue(SystemInfo.isLinux || SystemInfo.isWindows)
    val productInfo = loadProductInfo("idea-2050.1-Linux")
    assertEquals("2050.1", productInfo.version)
  }

  private fun loadProductInfo(dirName: String): ProductInfoData {
    return IdeProductInfo.getInstance().loadProductInfo(basePath.resolve(dirName)) ?: error("Product info is not loaded from $dirName")
  }
}
