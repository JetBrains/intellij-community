// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserExtensionsStateService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getSuggestionData
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.isIgnoreIdeSuggestion
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
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
        MarketplaceRequests.getInstance().deserializeExtensionsForIdes(it)
      }
    }
  }

  @Test
  fun suggestedIde() = runBlocking {
    preparePluginCache("*.js" to PluginData("JavaScript"))
    val suggestion = getSuggestionData(project = projectRule.project,
                                       activeProductCode = "IC",
                                       fileName = "foo.js",
                                       fileType = PlainTextFileType.INSTANCE)
    assertEquals(listOf("IntelliJ IDEA Ultimate"), suggestion!!.suggestedIdes.map { it.name })
  }

  @Test
  fun suggestedIdeDismissed() = runBlocking {
    preparePluginCache("*.js" to PluginData("JavaScript", isBundled = true))
    isIgnoreIdeSuggestion = true
    try {
      val suggestion = getSuggestionData(projectRule.project, "IC", "foo.js", PlainTextFileType.INSTANCE)
      assertEquals(0, suggestion!!.suggestedIdes.size)
    }
    finally {
      isIgnoreIdeSuggestion = false
    }
  }

  @Test
  fun suggestedIdeInPyCharmCommunity() = runBlocking {
    preparePluginCache("*.js" to PluginData("JavaScript"))
    val suggestion = getSuggestionData(projectRule.project, "PC", "foo.js", PlainTextFileType.INSTANCE)
    assertEquals(listOf("PyCharm Professional"), suggestion!!.suggestedIdes.map { it.name })
  }

  @Test
  fun noSuggestionForNonPlainTextFile() = runBlocking {
    preparePluginCache("*.xml" to null)
    val suggestion = getSuggestionData(projectRule.project, "IU", "foo.xml", SupportedFileType())
    assertEquals(0, suggestion!!.suggestedIdes.size)
  }

  @Test
  fun suggestionForNonPlainTextFile() = runBlocking {
    preparePluginCache("build.xml" to PluginData("Ant"))
    val suggestion = getSuggestionData(projectRule.project, "IU", "build.xml", SupportedFileType())

    assertNotNull(suggestion)
    assertEquals(listOf("Ant"), suggestion.thirdParty.map { it.pluginIdString })
  }

  @Test
  fun noSuggestionForUnknownExtension() = runBlocking {
    preparePluginCache("*.jaba" to null)
    val suggestion = getSuggestionData(projectRule.project, "IC", "foo.jaba", PlainTextFileType.INSTANCE)
    assertEquals(0, suggestion!!.suggestedIdes.size)
  }

  @Test
  fun suggestCLionInIU() = runBlocking {
    preparePluginCache("*.cpp" to null)
    val suggestion = getSuggestionData(projectRule.project, "IU", "foo.cpp", PlainTextFileType.INSTANCE)
    assertEquals("CLion", suggestion!!.suggestedIdes.single().name)
  }

  @Test
  fun suggestPluginByExtension() = runBlocking {
    preparePluginCache("*.lua" to PluginData("Lua"))
    val suggestion = getSuggestionData(projectRule.project, "IU", "foo.lua", PlainTextFileType.INSTANCE)

    assertNotNull(suggestion)
    assertEquals(listOf("Lua"), suggestion.thirdParty.map { it.pluginIdString })
  }

  private suspend fun preparePluginCache(vararg ext: Pair<String, PluginData?>) {
    val featureMap = ext.associate { (extensionOrFileName, pluginData) ->
      extensionOrFileName to PluginDataSet(setOfNotNull(pluginData))
    }

    PluginFeatureCacheService.getInstance().extensions.set(PluginFeatureMap(featureMap))

    val pluginAdvertiserExtensionsStateService = PluginAdvertiserExtensionsStateService.getInstance()
    for ((extensionOrFileName, pluginDataSet) in featureMap) {
      pluginAdvertiserExtensionsStateService.updateCache(extensionOrFileName = extensionOrFileName, compatiblePlugins = pluginDataSet.dataSet)
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