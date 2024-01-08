// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.io.File

abstract class AbstractAutoImportTestBase : KotlinLightCodeInsightFixtureTestCase() {

    fun doTest(unused: String) = doTest()

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val testDataDirectory: File
        get() = File(TestMetadataUtil.getTestDataPath(javaClass), fileName())

    override fun mainFile(): File =
        File(testDataDirectory, "${fileName()}.kt")


    protected open fun setupAutoImportEnvironment(settings: KotlinCodeInsightSettings, withAutoImport: Boolean) {
        settings.addUnambiguousImportsOnTheFly = withAutoImport
    }

    private fun doTest() {
        val mainFile = mainFile().also { check(it.exists()) { "$it should exist" } }
        val disableTestDirective = if (isFirPlugin) IgnoreTests.DIRECTIVES.IGNORE_K2 else IgnoreTests.DIRECTIVES.IGNORE_K1

        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), disableTestDirective) {
            val base = mainFile.parentFile
            val afterFile = File(base, mainFile.name + ".after").also { check(it.exists()) { "$it should exist" } }

            base.walkTopDown()
                .filter { it.isFile && (it != mainFile && it != afterFile) }
                .forEach {
                    val name = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(base, it)!!)
                    val loadFile = FileUtil.loadFile(it, true)
                    myFixture.addFileToProject(name, loadFile)
                }

            val file = myFixture.configureByText(mainFile.name, FileUtil.loadFile(mainFile, true))
            val originalText = file.text

            val withAutoImport = InTextDirectivesUtils.findStringWithPrefixes(originalText, "// WITHOUT_AUTO_IMPORT") == null

            withCustomCompilerOptions(originalText, project, module) {
                val settings = KotlinCodeInsightSettings.getInstance()
                val oldValue = settings.addUnambiguousImportsOnTheFly
                ConfigLibraryUtil.configureLibrariesByDirective(module, originalText)

                val addKotlinRuntime = InTextDirectivesUtils.findStringWithPrefixes(originalText, "// WITH_STDLIB") != null
                if (addKotlinRuntime) {
                    ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
                }

                try {
                    setupAutoImportEnvironment(settings, withAutoImport)

                    val nameCountToUseStarImport = InTextDirectivesUtils.getPrefixedInt(originalText, "// NAME_COUNT_TO_USE_STAR_IMPORT:")
                    val runCount = InTextDirectivesUtils.getPrefixedInt(originalText, "// RUN_COUNT:") ?: 1

                    nameCountToUseStarImport?.let { file.kotlinCustomSettings.NAME_COUNT_TO_USE_STAR_IMPORT = it }
                    repeat(runCount) {
                        // same as myFixture.doHighlighting() but allow to change PSI during highlighting (due to addUnambiguousImportsOnTheFly)
                        CodeInsightTestFixtureImpl.instantiateAndRun(
                            getFile(),
                            editor, intArrayOf(),
                            /* canChange */ true
                        )
                    }
                    AppExecutorUtil.getAppExecutorService().submit {
                        DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.file, myFixture.editor)
                    }.get()

                } finally {
                    ConfigLibraryUtil.unconfigureLibrariesByDirective(module, originalText)

                    if (addKotlinRuntime) {
                        ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
                    }
                    settings.addUnambiguousImportsOnTheFly = oldValue
                }
            }

            val expectedResult = if (withAutoImport) {
                FileUtil.loadFile(afterFile, true)
            } else {
                originalText
            }

            myFixture.checkResult(expectedResult)
        }
    }
}

abstract class AbstractK1AutoImportTest : AbstractAutoImportTestBase()