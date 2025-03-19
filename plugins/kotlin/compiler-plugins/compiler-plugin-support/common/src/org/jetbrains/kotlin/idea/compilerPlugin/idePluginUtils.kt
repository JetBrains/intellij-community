// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.getInstance
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import java.io.File

fun Module.getSpecialAnnotations(prefix: String): List<String> =
    KotlinCommonCompilerArgumentsHolder.getInstance(this).pluginOptions
        ?.filter { it.startsWith(prefix) }
        ?.map { it.substring(prefix.length) }
        ?: emptyList()

class CompilerPluginSetup(val options: List<PluginOption>, val classpath: List<String>) {
    class PluginOption(val key: String, val value: String)
}

fun modifyCompilerArgumentsForPlugin(
    facet: KotlinFacet,
    setup: CompilerPluginSetup?,
    compilerPluginId: String,
    pluginName: String
) = modifyCompilerArgumentsForPluginWithFacetSettings(facet.configuration.settings, setup, compilerPluginId, pluginName)

fun modifyCompilerArgumentsForPluginWithFacetSettings(
    facetSettings: IKotlinFacetSettings,
    setup: CompilerPluginSetup?,
    compilerPluginId: String,
    pluginName: String
) {
    var shouldSetupCompilerArguments = false

    val commonArguments = facetSettings.compilerArguments ?: CommonCompilerArguments.DummyImpl().also {
        shouldSetupCompilerArguments = true
    }

    val newAllPluginOptions = getNewPluginOptionsOrNull(setup, compilerPluginId, commonArguments)
    val newPluginClasspaths = getNewPluginClasspathsOrNull(commonArguments, pluginName, setup)

    if (shouldSetupCompilerArguments)
        setupCompilerArguments(facetSettings, commonArguments, newAllPluginOptions, newPluginClasspaths)
    else
        updateCompilerArgumentsIfNeeded(facetSettings, newAllPluginOptions, newPluginClasspaths)
}

private fun getNewPluginClasspathsOrNull(
    commonArguments: CommonCompilerArguments,
    pluginName: String,
    setup: CompilerPluginSetup?
): Array<String>? {
    val oldPluginClasspaths = (commonArguments.pluginClasspaths ?: emptyArray()).filterTo(mutableListOf()) {
        val lastIndexOfFile = it.lastIndexOfAny(charArrayOf('/', File.separatorChar))
        if (lastIndexOfFile < 0) {
            return@filterTo true
        }
        !it.drop(lastIndexOfFile + 1).matches("(kotlin-)?(maven-)?$pluginName-.*\\.jar".toRegex())
    }

    val newClasspath = setup?.classpath ?: emptyList()
    val newPluginClasspaths = (oldPluginClasspaths + newClasspath).toTypedArray()

    return if (newPluginClasspaths.contentEquals(commonArguments.pluginClasspaths)) null else newPluginClasspaths
}

private fun getNewPluginOptionsOrNull(
    setup: CompilerPluginSetup?,
    compilerPluginId: String,
    commonArguments: CommonCompilerArguments,
): Array<String>? {
    // See [CommonCompilerArguments.PLUGIN_OPTION_FORMAT]
    val newOptionsForPlugin = setup?.options?.map { "plugin:$compilerPluginId:${it.key}=${it.value}" } ?: emptyList()

    val oldAllPluginOptions =
        (commonArguments.pluginOptions ?: emptyArray()).filterTo(mutableListOf()) { !it.startsWith("plugin:$compilerPluginId:") }
    val newAllPluginOptions = (oldAllPluginOptions + newOptionsForPlugin).toTypedArray()
    return if (newAllPluginOptions.contentEquals(commonArguments.pluginOptions)) null else newAllPluginOptions
}

private fun setupCompilerArguments(
    facetSettings: IKotlinFacetSettings,
    commonArguments: CommonCompilerArguments,
    newOptionsForPlugin: Array<String>?,
    newClasspath: Array<String>?,
) {
    newOptionsForPlugin?.also { commonArguments.pluginOptions = newOptionsForPlugin }
    newClasspath?.also { commonArguments.pluginClasspaths = newClasspath }
    facetSettings.compilerArguments = commonArguments
}

private fun updateCompilerArgumentsIfNeeded(
    facetSettings: IKotlinFacetSettings,
    newOptionsForPlugin: Array<String>?,
    newClasspath: Array<String>?,
) {
    if (newOptionsForPlugin == null && newClasspath == null) return
    facetSettings.updateCompilerArguments {
        newOptionsForPlugin?.also { pluginOptions = newOptionsForPlugin }
        newClasspath?.also { pluginClasspaths = newClasspath }
    }
}