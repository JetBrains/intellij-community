// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.collectors.fus.os.SystemRuntimeCollector
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.testFramework.PlatformTestCase
import org.junit.Assert
import org.junit.Test

class SystemRuntimeCollectorTest : PlatformTestCase() {


  @Test
  fun `test round down method`() {
    Assert.assertEquals(0, SystemRuntimeCollector.roundDown(0, 10, 30, 50, 100, 200))
    Assert.assertEquals(0, SystemRuntimeCollector.roundDown(9, 10, 30, 50, 100, 200))
    Assert.assertEquals(10, SystemRuntimeCollector.roundDown(10, 10, 30, 50, 100, 200))
    Assert.assertEquals(10, SystemRuntimeCollector.roundDown(15, 10, 30, 50, 100, 200))
    Assert.assertEquals(100, SystemRuntimeCollector.roundDown(100, 10, 30, 50, 100, 200))
    Assert.assertEquals(100, SystemRuntimeCollector.roundDown(105, 10, 30, 50, 100, 200))
    Assert.assertEquals(100, SystemRuntimeCollector.roundDown(150, 10, 30, 50, 100, 200))
    Assert.assertEquals(50, SystemRuntimeCollector.roundDown(99, 10, 30, 50, 100, 200))
    Assert.assertEquals(200, SystemRuntimeCollector.roundDown(200, 10, 30, 50, 100, 200))
    Assert.assertEquals(200, SystemRuntimeCollector.roundDown(201, 10, 30, 50, 100, 200))
    Assert.assertEquals(200, SystemRuntimeCollector.roundDown(2010, 10, 30, 50, 100, 200))
    Assert.assertEquals(200, SystemRuntimeCollector.roundDown(Long.MAX_VALUE, 10, 30, 50, 100, 200))

    Assert.assertEquals(0, SystemRuntimeCollector.roundDown(4, 0, 30, 50, 100, 200))
    Assert.assertEquals(30, SystemRuntimeCollector.roundDown(30, 0, 30, 50, 100, 200))
    Assert.assertEquals(30, SystemRuntimeCollector.roundDown(31, 0, 30, 50, 100, 200))

    Assert.assertEquals(-1, SystemRuntimeCollector.roundDown(31, -1, 30, 50, 100, 200))
    Assert.assertEquals(-1, SystemRuntimeCollector.roundDown(31, -10, 30, 50, 100, 200))
  }

  @Test
  fun `test convert xmx option`() {
    //512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000
    assertMb("Xmx", 0, SystemRuntimeCollector.convertOptionToData("-Xmx511m"))
    assertMb("Xmx", 0, SystemRuntimeCollector.convertOptionToData("-Xmx10m"))

    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx512m"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx550M"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx530"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx524288K"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx524289k"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx536870913B"))
    assertMb("Xmx", 512, SystemRuntimeCollector.convertOptionToData("-Xmx536870923b"))

    assertMb("Xmx", 750, SystemRuntimeCollector.convertOptionToData("-Xmx750m"))
    assertMb("Xmx", 750, SystemRuntimeCollector.convertOptionToData("-Xmx750M"))
    assertMb("Xmx", 750, SystemRuntimeCollector.convertOptionToData("-Xmx999m"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1024m"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1050M"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1073"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1048576k"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1048576K"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1048576K"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1073741824b"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1073741824B"))
    assertMb("Xmx", 1024, SystemRuntimeCollector.convertOptionToData("-Xmx1G"))
    assertMb("Xmx", 2048, SystemRuntimeCollector.convertOptionToData("-Xmx2g"))
    assertMb("Xmx", 2048, SystemRuntimeCollector.convertOptionToData("-Xmx2G"))

    assertMb("Xmx", 8000, SystemRuntimeCollector.convertOptionToData("-Xmx8000M"))
    assertMb("Xmx", 8000, SystemRuntimeCollector.convertOptionToData("-Xmx8G"))
    assertMb("Xmx", 8000, SystemRuntimeCollector.convertOptionToData("-Xmx9000m"))
    assertMb("Xmx", 8000, SystemRuntimeCollector.convertOptionToData("-Xmx10G"))

    assertMb("Xmx", 8000, SystemRuntimeCollector.convertOptionToData("-Xmx100G"))
  }

  @Test
  fun `test convert xms option`() {
    //64, 128, 256, 512
    assertMb("Xms", 64, SystemRuntimeCollector.convertOptionToData("-Xms64m"))
    assertMb("Xms", 128, SystemRuntimeCollector.convertOptionToData("-Xms129m"))
    assertMb("Xms", 128, SystemRuntimeCollector.convertOptionToData("-Xms136"))
    assertMb("Xms", 128, SystemRuntimeCollector.convertOptionToData("-Xms132096k"))
    assertMb("Xms", 512, SystemRuntimeCollector.convertOptionToData("-Xms750m"))
    assertMb("Xms", 512, SystemRuntimeCollector.convertOptionToData("-Xms750M"))
    assertMb("Xms", 512, SystemRuntimeCollector.convertOptionToData("-Xms2G"))
  }

  @Test
  fun `test convert SoftRefLRUPolicyMSPerMB option`() {
    //50, 100
    assertMb("SoftRefLRUPolicyMSPerMB", 0, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=10m"))
    assertMb("SoftRefLRUPolicyMSPerMB", 50, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=50m"))
    assertMb("SoftRefLRUPolicyMSPerMB", 50, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=55M"))
    assertMb("SoftRefLRUPolicyMSPerMB", 50, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB55M"))
    assertMb("SoftRefLRUPolicyMSPerMB", 50, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=101376k"))
    assertMb("SoftRefLRUPolicyMSPerMB", 100, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=104857600"))
    assertMb("SoftRefLRUPolicyMSPerMB", 100, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=104857600b"))
    assertMb("SoftRefLRUPolicyMSPerMB", 100, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=204857600b"))
    assertMb("SoftRefLRUPolicyMSPerMB", 100, SystemRuntimeCollector.convertOptionToData("-XX:SoftRefLRUPolicyMSPerMB=200G"))
  }

  @Test
  fun `test convert ReservedCodeCacheSize option`() {
    //240, 300, 400, 500
    assertMb("ReservedCodeCacheSize", 0, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=10"))
    assertMb("ReservedCodeCacheSize", 240, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=250"))
    assertMb("ReservedCodeCacheSize", 240, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=250M"))
    assertMb("ReservedCodeCacheSize", 500, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=500m"))
    assertMb("ReservedCodeCacheSize", 500, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=1500m"))
    assertMb("ReservedCodeCacheSize", 500, SystemRuntimeCollector.convertOptionToData("-XX:ReservedCodeCacheSize=1536000K"))

  }

  private fun assertMb(name: String, sizeMb: Long, actual: FeatureUsageData?) {
    Assert.assertNotNull(actual)
    Assert.assertEquals(FeatureUsageData().addData("name", name).addData("value", sizeMb), actual)
  }
}