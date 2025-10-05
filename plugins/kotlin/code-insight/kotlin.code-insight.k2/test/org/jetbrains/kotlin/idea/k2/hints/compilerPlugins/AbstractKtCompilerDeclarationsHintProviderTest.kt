// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration.KtCompilerPluginGeneratedDeclarationsInlayHintsProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration.KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.readText

abstract class AbstractKtCompilerDeclarationsHintProviderTest : InlayHintsProviderTestCase(), ExpectedPluginModeProvider {
    private val provider
        get() = InlayHintsProviderExtension.findProviders()
            .map { it.provider }
            .filterIsInstance<KtCompilerPluginGeneratedDeclarationsInlayHintsProvider>()
            .single()


    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun doTest(testPath: String) {
        val testFile = Paths.get(testPath)
        val text = testFile.readText()
        val settings = parseSettings(text)

        module.withCompilerPlugin(
            KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN,
        ) {
            performTest(testFile, text, settings)
        }
    }

    private fun parseSettings(text: String): KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings {
        return KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings().apply {
            val directives = KotlinTestUtils.parseDirectives(text)
            showHiddenMembers = WITH_HIDDEN_MEMBERS_DIRECTIVE in directives
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun performTest(testFile: Path, fileText: String, settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings) {
        val sourceText = InlayDumpUtil.removeInlays(fileText)
        myFixture.configureByText(testFile.name, sourceText)
        val actualText = allowAnalysisOnEdt {
            dumpInlayHints(
                sourceText,
                provider,
                settings,
                renderBelowLineBlockInlaysBelowTheLine = true,
            )
        }
        KotlinTestUtils.assertEqualsToFile(testFile, actualText)
    }

    companion object {
        private const val WITH_HIDDEN_MEMBERS_DIRECTIVE = "WITH_HIDDEN_MEMBERS"
    }
}