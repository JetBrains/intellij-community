// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.psi.injection

import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.injection.Injectable
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class StringInterpolationInjectionTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testInterpolationSimpleName() = doTest(
        """
                        fun foo(){
                            val body = "Hello!"
                            "<html>${"$"}body</html><caret>"
                        }
                    """,
        "<html>Hello!</html>"
    )

    fun testInterpolationBlock() = doTest(
        """
            const val a = "Hello "

            fun foo(){
                val body = "World!"
                "<html>${"$"}{a + body}</html><caret>"
            }
                    """,
        "<html>Hello World!</html>"
    )

    private fun doTest(text: String, expectedText: String) {
        myFixture.configureByText("Injected.kt", text)

        InjectLanguageAction.invokeImpl(
            project,
            myFixture.editor,
            myFixture.file,
            Injectable.fromLanguage(HTMLLanguage.INSTANCE)
        )

        val injectedElement = injectedElement ?: kotlin.test.fail("no injection")
        TestCase.assertEquals(HTMLLanguage.INSTANCE, injectedElement.language)
        val containingFile = injectedElement.containingFile
        TestCase.assertEquals(expectedText, containingFile.text)
        TestCase.assertFalse(
            "Shouldn't be FRANKENSTEIN",
            InjectedLanguageManager.getInstance(containingFile.project).isFrankensteinInjection(containingFile)
        )

        undo(editor)
    }

    private fun undo(editor: Editor) {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK)
            try {
                val undoManager = UndoManager.getInstance(project)
                val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
                undoManager.undo(textEditor)
            } finally {
                TestDialogManager.setTestDialog(oldTestDialog)
            }
        })
    }

    private val injectedLanguageManager: InjectedLanguageManager
        get() = InjectedLanguageManager.getInstance(project)

    private val injectedElement: PsiElement?
        get() = injectedLanguageManager.findInjectedElementAt(topLevelFile, topLevelCaretPosition)

    private val topLevelFile: PsiFile get() = file.let { injectedLanguageManager.getTopLevelFile(it) }

    private val topLevelCaretPosition get() = editor.caretModel.offset

}