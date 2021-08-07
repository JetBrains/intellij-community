// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.advertiser

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserExtensionsStateService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PluginsAdvertiserTest {

  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule(preloadServices = true)

    @BeforeClass
    @JvmStatic
    fun loadExtensions() {
      val path = PlatformTestUtil.getPlatformTestDataPath() + "plugins/pluginAdvertiser/extensions.json"
      File(path).inputStream().use {
        MarketplaceRequests.Instance.deserializeExtensionsForIdes(it)
      }
    }

    @BeforeClass
    @JvmStatic
    fun loadJetBrainsPlugins() {
      val path = PlatformTestUtil.getPlatformTestDataPath() + "plugins/pluginAdvertiser/jetBrainsPlugins.json"
      File(path).inputStream().use {
        MarketplaceRequests.Instance.deserializeJetBrainsPluginsIds(it)
      }
    }
  }

  @Test
  fun testSerializeKnownExtensions() {
    val expected = PluginFeatureMap(mapOf("foo" to setOf(PluginData("foo", "Foo"))))
    PluginFeatureCacheService.instance.extensions = expected

    val state = serialize(PluginFeatureCacheService.instance.state)!!
    PluginFeatureCacheService.instance.loadState(deserialize(state))

    val actual = PluginFeatureCacheService.instance.extensions
    assertNotNull(actual, "Extensions information for PluginsAdvertiser has not been loaded")
    assertEquals("foo", actual["foo"].single().pluginIdString)
  }

  @Test
  fun suggestedIde() {
    preparePluginCache("*.js" to PluginData("JavaScript"))
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IC", "foo.js", PlainTextFileType.INSTANCE)
    assertEquals(listOf("WebStorm", "IntelliJ IDEA Ultimate"), suggestion!!.suggestedIdes.map { it.name })
  }

  @Test
  fun suggestedIdeInPyCharmCommunity() {
    preparePluginCache("*.js" to PluginData("JavaScript"))
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "PC", "foo.js", PlainTextFileType.INSTANCE)
    assertEquals(listOf("WebStorm", "PyCharm Professional"), suggestion!!.suggestedIdes.map { it.name })
  }

  @Test
  fun noSuggestionForNonPlainTextFile() {
    preparePluginCache("*.xml" to null)
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IU", "foo.xml", SupportedFileType())
    assertEquals(0, suggestion!!.suggestedIdes.size)
  }

  @Test
  fun suggestionForNonPlainTextFile() {
    preparePluginCache("build.xml" to PluginData("Ant"))
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IU", "build.xml", SupportedFileType())
    assertEquals(listOf("Ant"), suggestion!!.myThirdParty.map { it.pluginIdString })
  }

  @Test
  fun noSuggestionForUnknownExtension() {
    preparePluginCache("*.jaba" to null)
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IC", "foo.jaba", PlainTextFileType.INSTANCE)
    assertEquals(0, suggestion!!.suggestedIdes.size)
  }

  @Test
  fun suggestCLionInIU() {
    preparePluginCache("*.cpp" to null)
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IU", "foo.cpp", PlainTextFileType.INSTANCE)
    assertEquals("CLion", suggestion!!.suggestedIdes.single().name)
  }

  @Test
  fun suggestPluginByExtension() {
    preparePluginCache("*.lua" to PluginData("Lua"))
    val suggestion = PluginAdvertiserEditorNotificationProvider.getSuggestionData(projectRule.project, "IU", "foo.lua", PlainTextFileType.INSTANCE)
    assertEquals(listOf("Lua"), suggestion!!.myThirdParty.map { it.pluginIdString })
  }

  private fun preparePluginCache(vararg ext: Pair<String, PluginData?>) {
    fun PluginData?.nullableToSet() = this?.let { setOf(it) } ?: emptySet()

    PluginFeatureCacheService.instance.extensions = PluginFeatureMap(ext.associate { (extensionOrFileName, pluginData) -> extensionOrFileName to pluginData.nullableToSet() })
    for ((extensionOrFileName, pluginData) in ext) {
      PluginAdvertiserExtensionsStateService.instance.updateCache(extensionOrFileName, pluginData.nullableToSet())
    }
  }

  private class SupportedFileType : FileType {
    override fun getName(): String = "supported"
    override fun getDescription(): String = "Supported"
    override fun getDefaultExtension(): String = "sft"
    override fun getIcon(): Icon? = null
    override fun isBinary(): Boolean = false
  }
}