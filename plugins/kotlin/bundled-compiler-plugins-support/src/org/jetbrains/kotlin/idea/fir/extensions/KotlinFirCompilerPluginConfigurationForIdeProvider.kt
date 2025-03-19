// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * An extension point which allows us to set up custom options to [CompilerConfiguration] for FIR compiler plugins.
 * On IDE, this will be called before registering compiler plugin extensions to set up [CompilerConfiguration]
 * based on the registered [KotlinFirCompilerPluginConfigurationForIdeProvider]s.
 * 
 * Since this EP uses [CompilerPluginRegistrar] which is potentially not stable, 
 * it also is considered experimental and should be used with care.
 */
@ApiStatus.Experimental
interface KotlinFirCompilerPluginConfigurationForIdeProvider {
    /**
     * Returns whether this [KotlinFirCompilerPluginConfigurationForIdeProvider] is supposed to set up options for
     * the compiler plugin containing [registrar] or not.
     */
    @OptIn(ExperimentalCompilerApi::class)
    fun isConfigurationProviderForCompilerPlugin(registrar: CompilerPluginRegistrar): Boolean

    /**
     * This function has to copy [original], set up custom plugin options, and return it.
     */
    fun provideCompilerConfigurationWithCustomOptions(original: CompilerConfiguration): CompilerConfiguration

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinFirCompilerPluginConfigurationForIdeProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.firCompilerPluginConfigurationProvider")

        @OptIn(ExperimentalCompilerApi::class)
        fun getCompilerConfigurationWithCustomOptions(
            registrar: CompilerPluginRegistrar, 
            configuration: CompilerConfiguration,
        ): CompilerConfiguration? {
            var configurationProvider = EP_NAME.findFirstSafe { it.isConfigurationProviderForCompilerPlugin(registrar) }
            return configurationProvider?.provideCompilerConfigurationWithCustomOptions(configuration)
        }
    }
}
