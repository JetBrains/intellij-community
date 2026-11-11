// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinInlayHintsProviderTest
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.KtCompilerSupertypesHintProvider
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

abstract class AbstractKtCompilerSupertypesHintProviderTestBase(val treatAsThirdParty: Boolean) : AbstractKotlinInlayHintsProviderTest() {
    @TempDir
    lateinit var tempDir: Path

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtCompilerSupertypesHintProvider()

    override fun doTest(testPath: String) {
        val plugin = KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN

        myFixture.module.withCompilerPlugin(
            plugin,
            pluginJar = if (treatAsThirdParty) createThirdPartyCompilerPluginJar(plugin, tempDir) else plugin.bundledJarLocation,
        ) {
            super.doTest(testPath)
        }
    }
}

abstract class AbstractKtBundledCompilerSupertypesHintProviderTest :
    AbstractKtCompilerSupertypesHintProviderTestBase(treatAsThirdParty = false)

abstract class AbstractKtThirdPartyCompilerSupertypesHintProviderTest :
    AbstractKtCompilerSupertypesHintProviderTestBase(treatAsThirdParty = true)