// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar

internal class DefaultParcelizeFirCompilerPluginConfigurationForIdeProvider: KotlinFirCompilerPluginConfigurationForIdeProvider {
    @ExperimentalCompilerApi
    override fun isConfigurationProviderForCompilerPlugin(registrar: CompilerPluginRegistrar): Boolean {
        return registrar::class == ParcelizeComponentRegistrar::class
    }

    override fun provideCompilerConfigurationWithCustomOptions(original: CompilerConfiguration): CompilerConfiguration {
        return original.copy().apply { put(CommonConfigurationKeys.USE_FIR, true) }
    }
}
