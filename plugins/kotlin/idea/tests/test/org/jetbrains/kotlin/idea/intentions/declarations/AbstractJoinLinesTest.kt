// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions.declarations

import com.intellij.codeInsight.editorActions.JoinLinesHandler
import com.intellij.ide.DataManager
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.AFTER_ERROR_DIRECTIVE
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractJoinLinesTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    @Throws(Exception::class)
    fun doTest(unused: String) {
        myFixture.configureByFile(fileName())

        val dataContext = DataManager.getInstance().getDataContext(editor.contentComponent)

        myFixture.project.executeWriteCommand("Join lines") {
            JoinLinesHandler(null).execute(editor, editor.caretModel.currentCaret, dataContext)
        }

        val testFile = dataFile()
        val expectedFile = File(testFile.parentFile, testFile.name + ".after")
        myFixture.checkResultByFile(expectedFile)
        checkForUnexpectedErrors(testFile, myFixture.file as KtFile, myFixture.file.text)
    }

    private fun checkForUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
        val skipErrorsAfterCheck = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
            fileText,
            *skipErrorsAfterCheckDirectives.toTypedArray()
        ).isNotEmpty()
        if (!skipErrorsAfterCheck) {
            checkForErrorsAfter(mainFile, ktFile, fileText)
        }
    }

    protected open val skipErrorsAfterCheckDirectives: List<String> =
        listOf(IgnoreTests.DIRECTIVES.of(pluginMode), DISABLE_ERRORS_DIRECTIVE, "// SKIP_ERRORS_AFTER")

    protected open fun checkForErrorsAfter(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, directive = AFTER_ERROR_DIRECTIVE)
    }
}