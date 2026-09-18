// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.utils

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.PlatformUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

private const val tbePluginId = "org.jetbrains.toolbox-enterprise-client"
private const val androidPluginId = "org.jetbrains.android"
private const val thirdPartyPluginId = "some.plugin.id"

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
      .map    { PluginInfo(it, thirdPartyPluginId, "1.2.3") }
      .filter { it.isAllowedToInjectIntoFUS() }

    assertThat(allowed.map { it.type }).containsExactlyInAnyOrder(PluginType.PLATFORM, PluginType.FROM_SOURCES)
  }

  /**
   * Android Studio needs this entry. `AndroidStudioEventLogListenerProvider` keeps the FUS pipeline
   * active for the listener that maps the events to the Android Studio analytics.
   * IntelliJ IDEA also bundles the plugin, and it gets no right there.
   */
  @Test
  fun allowedPluginsAndroid() {
    PlatformTestUtil.withSystemProperty<RuntimeException>(PlatformUtils.PLATFORM_PREFIX_KEY, "AndroidStudio") {
      val (allowed, notAllowed) = PluginType.entries
        .map        { PluginInfo(it, androidPluginId, "1.2.3") }
        .partition  { it.isAllowedToInjectIntoFUS() }

      allowed
        .forEach { assertThat(it.isDevelopedByJetBrains()).isTrue() }

      notAllowed
        .forEach { assertThat(it.isDevelopedByJetBrains()).isFalse() }

      // Android Studio bundles the plugin.
      assertThat(PluginInfo(PluginType.JB_BUNDLED, androidPluginId, "1.2.3").isAllowedToInjectIntoFUS()).isTrue()
    }
  }

  /** The same plugin gets no right in another product, for example in IntelliJ IDEA. */
  @Test
  fun androidPluginNotAllowedOutsideAndroidStudio() {
    PlatformTestUtil.withSystemProperty<RuntimeException>(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.IDEA_PREFIX) {
      val allowed = PluginType.entries
        .map    { PluginInfo(it, androidPluginId, "1.2.3") }
        .filter { it.isAllowedToInjectIntoFUS() }

      assertThat(allowed.map { it.type }).containsExactlyInAnyOrder(PluginType.PLATFORM, PluginType.FROM_SOURCES)
    }
  }

  /**
   * `EventLogListenersManager.subscribe` takes a listener of a JetBrains plugin only, so the types of
   * a third-party plugin must stay outside this group.
   */
  @Test
  fun thirdPartyPluginsAreNotDevelopedByJetBrains() {
    val thirdPartyTypes = listOf(PluginType.LISTED, PluginType.NOT_LISTED, PluginType.UNKNOWN)

    thirdPartyTypes.forEach {
      assertThat(PluginInfo(it, thirdPartyPluginId, "1.2.3").isDevelopedByJetBrains())
        .describedAs("the type %s must not count as a JetBrains plugin", it)
        .isFalse()
    }

    PluginType.entries.filterNot { it in thirdPartyTypes }.forEach {
      assertThat(PluginInfo(it, thirdPartyPluginId, "1.2.3").isDevelopedByJetBrains())
        .describedAs("the type %s must count as a JetBrains plugin", it)
        .isTrue()
    }
  }
}