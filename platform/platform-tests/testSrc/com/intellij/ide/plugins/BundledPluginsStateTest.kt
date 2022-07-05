// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test


class BundledPluginsStateTest : LightPlatformTestCase() {

  @Test
  fun testSaving() {
    val pluginIds = listOf(
      "foo",
      "bar",
    ).map { PluginId.getId(it) }
      .toSet()

    BundledPluginsState.writePluginIdsToFile(pluginIds)
    assertThat(BundledPluginsState.readPluginIdsFromFile())
      .hasSameElementsAs(pluginIds)
  }

  @Test
  fun testSavingState() {
    assertThat(BundledPluginsState.readPluginIdsFromFile())
      .hasSameElementsAs(BundledPluginsState.loadedPluginIds)

    val savedBuildNumber = PropertiesComponent.getInstance().savedBuildNumber
    assertThat(savedBuildNumber).isNotNull
    assertThat(savedBuildNumber).isGreaterThanOrEqualTo(ApplicationInfo.getInstance().build)
  }
}