// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.KotlinFacetTestCase
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.junit.jupiter.api.Assertions
import kotlin.collections.contains

class IdePluginUtilsTest : KotlinFacetTestCase() {
    val facet
        get() = getKotlinFacet()
    val facetSettings
        get() = facet.configuration.settings
    val compilerPluginId = "pluginId"
    val otherCompilerPluginId = "pluginId2"
    val pluginName = "pluginName"

    fun `test modify empty compiler arguments for plugin with new classpath and new options`() {
        val compilerPluginSetup = CompilerPluginSetup(
            listOf(CompilerPluginSetup.PluginOption("optionKey", "optionValue")),
            listOf("newClassPath")
        )

        modifyCompilerArgumentsForPlugin(facet, compilerPluginSetup, compilerPluginId, pluginName)
        val commonCompilerArguments = facetSettings.compilerArguments
        Assertions.assertNotNull(commonCompilerArguments)
        Assertions.assertTrue(commonCompilerArguments?.pluginClasspaths?.contains("newClassPath") ?: false)
        Assertions.assertTrue(commonCompilerArguments?.pluginOptions?.contains("plugin:$compilerPluginId:optionKey=optionValue") ?: false)
    }

    fun `test modify empty compiler arguments for plugin with null setup`() {
        modifyCompilerArgumentsForPlugin(facet, null, "pluginId", "pluginName")
        val commonCompilerArguments = facetSettings.compilerArguments
        Assertions.assertNotNull(commonCompilerArguments)
        Assertions.assertEquals(commonCompilerArguments?.pluginClasspaths?.joinToString(", "), emptyArray<String>().joinToString(", "))
        Assertions.assertEquals(commonCompilerArguments?.pluginOptions?.joinToString(", "), emptyArray<String>().joinToString(", "))
    }

    fun `test modify filled compiler arguments for plugin with new options`() {
        facetSettings.compilerArguments = CommonCompilerArguments.DummyImpl()
        facetSettings.updateCompilerArguments {
            pluginClasspaths = arrayOf("oldClassPath")
            pluginOptions = arrayOf("plugin:$otherCompilerPluginId:optionKey=oldOption")
        }
        fireFacetChangedEvent(facet)
        Assertions.assertTrue(facetSettings.compilerArguments?.pluginClasspaths?.contains("oldClassPath") ?: false)
        Assertions.assertTrue(facetSettings.compilerArguments?.pluginOptions?.contains("plugin:$otherCompilerPluginId:optionKey=oldOption") ?: false)

        val compilerPluginSetup = CompilerPluginSetup(
            listOf(CompilerPluginSetup.PluginOption("optionKey", "optionValue")),
            listOf("newClassPath")
        )

        modifyCompilerArgumentsForPlugin(facet, compilerPluginSetup, compilerPluginId, pluginName)
        val commonCompilerArguments = facetSettings.compilerArguments
        Assertions.assertNotNull(commonCompilerArguments)
        Assertions.assertTrue(commonCompilerArguments?.pluginClasspaths?.contains("newClassPath") ?: false)
        Assertions.assertTrue(commonCompilerArguments?.pluginOptions?.contains("plugin:$compilerPluginId:optionKey=optionValue") ?: false)
        Assertions.assertTrue(commonCompilerArguments?.pluginClasspaths?.contains("oldClassPath") ?: false)
        Assertions.assertTrue(commonCompilerArguments?.pluginOptions?.contains("plugin:$otherCompilerPluginId:optionKey=oldOption") ?: false)
    }
}