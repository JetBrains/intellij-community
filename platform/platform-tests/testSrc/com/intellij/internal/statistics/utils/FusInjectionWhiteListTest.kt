// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.utils

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.PluginType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

private const val tbePluginId = "org.jetbrains.toolbox-enterprise-client"

class FusInjectionWhiteListTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun setUp() {
      PluginManagerCore.isUnitTestMode = true
    }
  }

  @Test
  fun allowedPluginsTBE() {
    val (allowed, notAllowed) = PluginType.values()
      .map        { PluginInfo(it, tbePluginId, "1.2.3") }
      .partition  { it.isAllowedToInjectIntoFUS() }

    allowed
      .forEach { assertThat(it.isDevelopedByJetBrains()).isTrue() }

    notAllowed
      .forEach { assertThat(it.isDevelopedByJetBrains()).isFalse() }
  }

  @Test
  fun unknownPluginsNotAllowed() {
    val allowed = PluginType.values()
      .map    { PluginInfo(it, "some.plugin.id", "1.2.3") }
      .filter { it.isAllowedToInjectIntoFUS() }

    assertThat(allowed.map { it.type }).containsExactlyInAnyOrder(PluginType.PLATFORM, PluginType.FROM_SOURCES)
  }
}