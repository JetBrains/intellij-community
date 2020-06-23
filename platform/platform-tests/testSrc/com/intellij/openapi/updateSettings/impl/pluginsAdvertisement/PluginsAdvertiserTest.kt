// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.testFramework.UsefulTestCase

/**
 * @author yole
 */
class PluginsAdvertiserTest : UsefulTestCase() {
  fun testSerializeKnownExtensions() {
    val pluginMap = mutableMapOf<String, Set<PluginsAdvertiser.Plugin>>()
    val plugin = PluginsAdvertiser.Plugin("foo", "Foo", false)
    pluginMap["foo"] = setOf(plugin)
    PluginsAdvertiser.saveExtensions(pluginMap)
    val knownExtensions = PluginsAdvertiser.loadExtensions()
    assertNotNull("Extensions information for PluginsAdvertiser has not been loaded", knownExtensions)
    assertEquals("foo", knownExtensions!!.find("foo")!!.single().myPluginId)
  }
}