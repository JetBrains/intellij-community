// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestUtil.dumpInlayHints
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration.KtCompilerPluginGeneratedDeclarationsInlayHintsProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration.KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.readText

abstract class AbstractKtCompilerDeclarationsHintProviderTestBase(val treatAsThirdParty: Boolean) {
    private val myFixture by codeInsightModuleFixture(projectFixture, descriptor.moduleTypeId)

    @TestDisposable
    lateinit var disposable: Disposable

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        val module = myFixture.module
        val sdk = descriptor.sdk!!
        WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.updateModel(module) { descriptor.configureModule(module, it, it.contentEntries.single()) }
        IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.get())
    }

    private val provider
        get() = InlayHintsProviderExtension.findProviders()
            .map { it.provider }
            .filterIsInstance<KtCompilerPluginGeneratedDeclarationsInlayHintsProvider>()
            .single()

    /**
     * Runs [doTest] for [testDataFilePath] relative to the `@TestRoot` of the generated class.
     * The generated `@Test` methods call it.
     */
    protected fun runTest(testDataFilePath: String) {
        val testRoot = TestMetadataUtil.getTestRoot(javaClass) ?: error("@TestRoot annotation was not found on ${javaClass.name}")
        doTest(File(testRoot, testDataFilePath).path)
    }

    private fun doTest(testPath: String) {
        val testFile = Paths.get(testPath)
        val text = testFile.readText()
        val settings = parseSettings(text)
        val plugin = KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN

        myFixture.module.withCompilerPlugin(
            plugin,
            pluginJar = if (treatAsThirdParty) createThirdPartyCompilerPluginJar(plugin, tempDir) else plugin.bundledJarLocation,
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
        val actualText = runInEdtAndGet {
            allowAnalysisOnEdt {
                dumpInlayHints(myFixture, sourceText, provider, settings, true)
            }
        }
        KotlinTestUtils.assertEqualsToFile(testFile, actualText)
    }

    companion object {
        private const val WITH_HIDDEN_MEMBERS_DIRECTIVE = "WITH_HIDDEN_MEMBERS"
        private val projectFixture = projectFixture(openAfterCreation = true)
        private val descriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }
}

abstract class AbstractKtBundledCompilerDeclarationsHintProviderTest :
    AbstractKtCompilerDeclarationsHintProviderTestBase(treatAsThirdParty = false)

abstract class AbstractKtThirdPartyCompilerDeclarationsHintProviderTest :
    AbstractKtCompilerDeclarationsHintProviderTestBase(treatAsThirdParty = true)