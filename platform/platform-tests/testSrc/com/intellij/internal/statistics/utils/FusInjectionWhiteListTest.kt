// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.utils

import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.PluginType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FusInjectionWhiteListTest {

  @Test
  fun allowedPluginsTBE() {
    val (allowed, notAllowed) = PluginType.values()
      .map        { PluginInfo(it, tbePluginId, "1.2.3") }
      .partition  { it.isAllowedToInjectIntoFUS() }

    allowed
      .forEach { assertTrue(it.isDevelopedByJetBrains()) }

    notAllowed
      .forEach { assertFalse(it.isDevelopedByJetBrains()) }
  }

  @Test
  fun unknownPluginsNotAllowed() {
    val allowed = PluginType.values()
      .map    { PluginInfo(it, "some.plugin.id", "1.2.3") }
      .filter { it.isAllowedToInjectIntoFUS() }

    assertEquals(1, allowed.size)
    assertEquals(PluginType.PLATFORM, allowed.first().type)
  }

  companion object {
    private const val tbePluginId = "org.jetbrains.toolbox-enterprise-client"
  }
}