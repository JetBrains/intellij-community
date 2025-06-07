// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinInlayHintsProviderTest
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.KtCompilerPluginModalityHintProvider

abstract class AbstractKtCompilerPluginModalityHintProviderTest : AbstractKotlinInlayHintsProviderTest() {
    override fun inlayHintsProvider(): InlayHintsProvider =
        KtCompilerPluginModalityHintProvider()

    override fun doTest(testPath: String) {
        myFixture.configureByText("Open.kt", "annotation class Open")
        module.withCompilerPlugin(
            KotlinK2BundledCompilerPlugins.ALL_OPEN_COMPILER_PLUGIN,
            "plugin:org.jetbrains.kotlin.allopen:annotation=Open"
        ) {
            super.doTest(testPath)
        }
    }
}