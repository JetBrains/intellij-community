// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import java.io.File

abstract class AbstractFormatterTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTestInverted(expectedFileNameWithExtension: String) {
        doTest(expectedFileNameWithExtension, true, false)
    }

    fun doTestInvertedCallSite(expectedFileNameWithExtension: String) {
        doTest(expectedFileNameWithExtension, true, false)
    }

    fun doTestCallSite(expectedFileNameWithExtension: String) {
        doTest(expectedFileNameWithExtension, false, true)
    }

    @JvmOverloads
    fun doTest(expectedFileNameWithExtension: String, inverted: Boolean = false, callSite: Boolean = false) {
        val file = File(expectedFileNameWithExtension)
        val fileName = file.name
        val testFileName = fileName.substring(0, fileName.indexOf("."))
        val testFileExtension = ".${file.extension}"
        val originalFile = File(file.parent, testFileName + testFileExtension)
        val originalFileText = FileUtil.loadFile(originalFile, true)
        val psiFile = myFixture.configureByText("A$testFileExtension", originalFileText)

        configureCodeStyleAndRun(project) {
            val codeStyleSettings = CodeStyle.getSettings(project)
            val customSettings = codeStyleSettings.kotlinCustomSettings
            val rightMargin = InTextDirectivesUtils.getPrefixedInt(originalFileText, "// RIGHT_MARGIN: ")
            if (rightMargin != null) {
                codeStyleSettings.setRightMargin(KotlinLanguage.INSTANCE, rightMargin)
            }

            val trailingComma = InTextDirectivesUtils.getPrefixedBoolean(originalFileText, "// TRAILING_COMMA: ")
            if (trailingComma != null) {
                customSettings.ALLOW_TRAILING_COMMA = trailingComma
            }

            val configurator = FormatSettingsUtil.createConfigurator(originalFileText, codeStyleSettings)
            if (!inverted) {
                configurator.configureSettings()
            } else {
                configurator.configureInvertedSettings()
            }

            customSettings.ALLOW_TRAILING_COMMA_ON_CALL_SITE = callSite
            project.executeWriteCommand("reformat") {
                CodeStyleManager.getInstance(project).reformat(psiFile)
            }

            KotlinTestUtils.assertEqualsToFile(file, psiFile.text)
        }
    }
}