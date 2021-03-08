// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.advertiser

import com.intellij.testFramework.ProjectRule
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @author yole
 */
class PluginsAdvertiserTest {

  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule(preloadServices = true)
  }

  @Test
  fun testSerializeKnownExtensions() {
    val expected = KnownExtensions(mapOf("foo" to setOf(PluginData("foo", "Foo"))))
    KnownExtensionsService.instance.extensions = expected

    val actual = KnownExtensionsService.instance.extensions
    assertNotNull(actual, "Extensions information for PluginsAdvertiser has not been loaded")
    assertEquals("foo", actual["foo"].single().pluginIdString)
  }
}