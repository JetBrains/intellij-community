// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.imports

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import java.io.File

abstract class AbstractAutoImportTest : KotlinLightCodeInsightFixtureTestCase() {

    fun doTest(unused: String) = doTest(true)

    fun doTestWithoutAutoImport(unused: String) = doTest(false)

    override val testDataDirectory: File
        get() = File(TestMetadataUtil.getTestDataPath(javaClass), fileName())

    override fun mainFile(): File =
        File(testDataDirectory, "${fileName()}.kt")


    protected open fun setupAutoImportEnvironment(settings: KotlinCodeInsightSettings, withAutoImport: Boolean) {
        settings.addUnambiguousImportsOnTheFly = withAutoImport
    }

    private fun doTest(withAutoImport: Boolean) {
        val mainFile = mainFile().also { check(it.exists()) { "$it should exist" } }
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

        val settings = KotlinCodeInsightSettings.getInstance()
        val oldValue = settings.addUnambiguousImportsOnTheFly
        try {
            setupAutoImportEnvironment(settings, withAutoImport)

            // same as myFixture.doHighlighting() but allow to change PSI during highlighting (due to addUnambiguousImportsOnTheFly)
            CodeInsightTestFixtureImpl.instantiateAndRun(
                getFile(),
                editor, intArrayOf(),
                /* canChange */ true
            )
        } finally {
            settings.addUnambiguousImportsOnTheFly = oldValue
        }

        val expectedResult = if (withAutoImport) {
            FileUtil.loadFile(afterFile, true)
        } else {
            originalText
        }

        myFixture.checkResult(expectedResult)
    }
}