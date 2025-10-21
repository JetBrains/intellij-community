// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.compiler.configuration.versionWithFallback

interface KotlinCompilerVersionProvider {
    /**
     * Returns the Kotlin compiler version used in this [module].
     */
    fun getKotlinCompilerVersion(module: Module): IdeKotlinVersion?

    /**
     * Returns whether this plugin provider can be used to get the Kotlin version for this [module].
     */
    fun isAvailable(module: Module): Boolean

    companion object {
        val EP_NAME: ExtensionPointName<KotlinCompilerVersionProvider> = ExtensionPointName.create<KotlinCompilerVersionProvider>("org.jetbrains.kotlin.kotlinCompilerVersionProvider")

        /**
         * Returns the Kotlin compiler version used in the [module], or null if it could not be determined.
         */
        fun getVersion(module: Module): IdeKotlinVersion? {
            return EP_NAME.extensionList.firstOrNull { it.isAvailable(module) }?.getKotlinCompilerVersion(module)
        }

        /**
         * Returns the version or the bundled JPS version if the provider did not return a valid version
         */
        fun getVersionWithFallback(module: Module): IdeKotlinVersion = getVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    }
}

/**
 * Provides the JPS version used in the corresponding module.
 */
private class DefaultKotlinCompilerVersionProvider : KotlinCompilerVersionProvider {
    override fun getKotlinCompilerVersion(module: Module): IdeKotlinVersion? {
        return IdeKotlinVersion.opt(KotlinJpsPluginSettings.getInstance(module.project).settings.versionWithFallback)
    }

    override fun isAvailable(module: Module): Boolean = true
}