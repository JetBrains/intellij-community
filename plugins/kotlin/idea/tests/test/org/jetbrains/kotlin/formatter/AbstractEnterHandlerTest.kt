// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.formatter

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.formatter.KotlinLineIndentProvider
import org.jetbrains.kotlin.idea.test.KotlinLightPlatformCodeInsightTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.junit.Assert
import java.io.File

abstract class AbstractEnterHandlerTest : KotlinLightPlatformCodeInsightTestCase() {
    private val customLineIndentProvider: LineIndentProvider = KotlinLineIndentProvider()

    fun doNewlineTestWithInvert(afterInvFilePath: String) {
        doNewlineTest(afterInvFilePath, true)
    }

    @JvmOverloads
    fun doNewlineTest(afterFilePath: String, inverted: Boolean = false) {
        val afterFile = File(afterFilePath)
        val testFileName = afterFile.name.substring(0, afterFile.name.indexOf("."))
        val testFileExtension = afterFile.name.substring(afterFile.name.lastIndexOf("."))
        val originFileName = testFileName + testFileExtension
        val originalFile = File(afterFile.parent, originFileName)
        val originalFileText = FileUtil.loadFile(originalFile, true)

        val withoutCustomLineIndentProvider = InTextDirectivesUtils.findStringWithPrefixes(
            originalFileText,
            "// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER"
        ) != null

        val ignoreFormatter = InTextDirectivesUtils.findStringWithPrefixes(
            originalFileText,
            "// IGNORE_FORMATTER"
        ) != null

        val ignoreInvFormatter = InTextDirectivesUtils.findStringWithPrefixes(
            originalFileText,
            "// IGNORE_INV_FORMATTER"
        ) != null

        val normalIndentSizePrefix = "// NORMAL_INDENT_SIZE:"
        val normalIndentSize =
            InTextDirectivesUtils
                .findStringWithPrefixes(
                    originalFileText,
                    normalIndentSizePrefix
                )
                ?.replace(normalIndentSizePrefix, "")
                ?.trim()
                ?.toInt()

        Assert.assertFalse(
            "Only one option of 'WITHOUT_CUSTOM_LINE_INDENT_PROVIDER' and 'IGNORE_FORMATTER' is available at the same time",
            withoutCustomLineIndentProvider && ignoreFormatter
        )

        configureCodeStyleAndRun(
            project = project,
            configurator = {
                val configurator = FormatSettingsUtil.createConfigurator(originalFileText, it)
                if (!inverted) {
                    configurator.configureSettings()
                } else {
                    configurator.configureInvertedSettings()
                }
                if (normalIndentSize != null) {
                    val kotlinSettings = it.getCommonSettings(KotlinLanguage.INSTANCE)
                    if (kotlinSettings.indentOptions == null) {
                        kotlinSettings.initIndentOptions()
                    }
                    kotlinSettings.indentOptions?.INDENT_SIZE = normalIndentSize
                }
            },
            body = {
                doNewlineTest(
                    beforeFilePath = originalFile.path,
                    afterFilePath = afterFilePath,
                    withoutCustomLineIndentProvider = withoutCustomLineIndentProvider,
                    ignoreFormatter = ignoreFormatter,
                    ignoreInvFormatter = ignoreInvFormatter,
                    isInvertedTest = inverted,
                )
            }
        )
    }

    private fun doNewlineTest(
        beforeFilePath: String,
        afterFilePath: String,
        withoutCustomLineIndentProvider: Boolean,
        ignoreFormatter: Boolean,
        ignoreInvFormatter: Boolean,
        isInvertedTest: Boolean,
    ) {
        KotlinLineIndentProvider.useFormatter = true
        typeAndCheck(
            beforeFilePath = beforeFilePath,
            afterFilePath = afterFilePath,
            errorMessage = "with FormatterBasedLineIndentProvider",
            ignoreInvertedFormatter = ignoreInvFormatter,
            ignoreFormatter = ignoreFormatter,
            isInvertedTest = isInvertedTest,
        )

        KotlinLineIndentProvider.useFormatter = false

        if (!withoutCustomLineIndentProvider) {
            typeAndCheck(beforeFilePath, afterFilePath, "with ${customLineIndentProvider.javaClass.simpleName}")
        }

        configureByFile(beforeFilePath)
        assertCustomIndentExist(withoutCustomLineIndentProvider)
    }

    private fun assertCustomIndentExist(withoutCustomLineIndentProvider: Boolean) {
        val offset = editor.caretModel.offset
        runWriteAction {
            editor.document.insertString(offset, "\n")
        }

        val customIndent = customLineIndentProvider.getLineIndent(project, editor, KotlinLanguage.INSTANCE, offset + 1)
        val condition = customIndent == null
        Assert.assertTrue(
            "${if (withoutCustomLineIndentProvider) "Remove" else "Add"} \"// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER\" or fix ${customLineIndentProvider.javaClass.simpleName}",
            if (withoutCustomLineIndentProvider) condition else !condition
        )
    }


    private fun typeAndCheck(
        beforeFilePath: String,
        afterFilePath: String,
        errorMessage: String,
        ignoreInvertedFormatter: Boolean = false,
        ignoreFormatter: Boolean = false,
        isInvertedTest: Boolean = false,
    ) {
        configureByFile(beforeFilePath)
        executeAction(IdeActions.ACTION_EDITOR_ENTER)
        val editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
        var actualTextWithCaret = StringBuilder(editor.document.text)
        for (caret in editor.caretModel.allCarets.asReversed()) {
            actualTextWithCaret = actualTextWithCaret.insert(
                caret.offset,
                EditorTestUtil.CARET_TAG
            )
        }


        val result = kotlin.runCatching {
            KotlinTestUtils.assertEqualsToFile(errorMessage, File(afterFilePath), actualTextWithCaret.toString())
        }

        when {
            ignoreFormatter -> Assert.assertTrue("Remove // IGNORE_FORMATTER", isInvertedTest || result.isFailure)
            isInvertedTest && ignoreInvertedFormatter -> Assert.assertTrue("Remove // IGNORE_INV_FORMATTER", result.isFailure)
            else -> result.getOrThrow()
        }
    }

    override fun getTestDataPath(): String = ""
}
