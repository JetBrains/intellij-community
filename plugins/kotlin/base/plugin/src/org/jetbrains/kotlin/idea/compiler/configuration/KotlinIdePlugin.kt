// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.extensions.PluginId

object KotlinIdePlugin {
    val id: PluginId

    /**
     * If `true`, the installed Kotlin IDE plugin is post-processed to be included in some other IDE, such as AppCode.
     */
    val isPostProcessed: Boolean

    /**
     * If `true`, the plugin version was patched in runtime, using the `kotlin.plugin.version` Java system property.
     */
    val hasPatchedVersion: Boolean

    /**
     * An original (non-patched) plugin version from the plugin descriptor.
     */
    val originalVersion: String

    /**
     * Kotlin IDE plugin version (the patched version if provided, the version from the plugin descriptor otherwise).
     */
    val version: String

    val isSnapshot: Boolean

    val isRelease: Boolean
        get() = !isSnapshot && KotlinPluginLayout.standaloneCompilerVersion.isRelease

    val isPreRelease: Boolean
        get() = !isRelease

    val isDev: Boolean
        get() = !isSnapshot && KotlinPluginLayout.standaloneCompilerVersion.isDev

    fun getPluginInfo(): PluginInfo = getPluginInfoById(id)

    init {
        val mainPluginId = "org.jetbrains.kotlin"

        val allPluginIds = listOf(
            mainPluginId,
            "com.intellij.appcode.kmm",
            "org.jetbrains.kotlin.native.appcode"
        )

        val pluginDescriptor = PluginManagerCore.getPlugins().firstOrNull { it.pluginId.idString in allPluginIds }
            ?: error("Kotlin IDE plugin not found above the active plugins: " + PluginManagerCore.getPlugins().contentToString())

        val patchedVersion = System.getProperty("kotlin.plugin.version", null)

        id = pluginDescriptor.pluginId
        isPostProcessed = id.idString == mainPluginId
        hasPatchedVersion = patchedVersion != null
        originalVersion = pluginDescriptor.version
        version = patchedVersion ?: originalVersion
        isSnapshot = version == "@snapshot@" || version.contains("-SNAPSHOT")
    }
}