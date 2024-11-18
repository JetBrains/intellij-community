// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar

/**
 * An extension point which allows us to set up custom options to [CompilerConfiguration] for compiler plugins.
 * On IDE, this will be called before registering compiler plugin extensions to set up [CompilerConfiguration]
 * based on the registered [K2CompilerPluginConfigurationProviderOnIDE].
 */
interface K2CompilerPluginConfigurationProviderOnIDE {
    /**
     * Returns whether this [K2CompilerPluginConfigurationProviderOnIDE] is supposed to set up options for
     * the compiler plugin containing [registrar] or not.
     */
    @OptIn(ExperimentalCompilerApi::class)
    fun isConfigurationProviderForCompilerPlugin(registrar: CompilerPluginRegistrar): Boolean

    /**
     * This function has to copy [original], set up custom plugin options, and return it.
     */
    fun provideCompilerConfigurationWithCustomOptions(original: CompilerConfiguration): CompilerConfiguration

    companion object {
        private val EP_NAME: ExtensionPointName<K2CompilerPluginConfigurationProviderOnIDE> =
            ExtensionPointName.create("org.jetbrains.kotlin.firCompilerPluginConfigurationProvider")

        @OptIn(ExperimentalCompilerApi::class)
        fun getCompilerConfigurationWithCustomOptions(
            registrar: CompilerPluginRegistrar, configuration: CompilerConfiguration
        ): CompilerConfiguration? {
            var configurationProvider = EP_NAME.findFirstSafe { it.isConfigurationProviderForCompilerPlugin(registrar) }
            return configurationProvider?.provideCompilerConfigurationWithCustomOptions(configuration)
        }
    }
}

internal class DefaultParcelizeCompilerPluginConfigurationProvider: K2CompilerPluginConfigurationProviderOnIDE {
    @ExperimentalCompilerApi
    override fun isConfigurationProviderForCompilerPlugin(registrar: CompilerPluginRegistrar): Boolean {
        return registrar::class == ParcelizeComponentRegistrar::class
    }

    override fun provideCompilerConfigurationWithCustomOptions(original: CompilerConfiguration): CompilerConfiguration {
        return original.copy().apply { put(CommonConfigurationKeys.USE_FIR, true) }
    }
}