// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.postfix.test

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinPostfixTemplateInCodeFragmentTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinJvmLightProjectDescriptor.DEFAULT

    private fun getContextElement(): KtNamedFunction {
        myFixture.configureByText("Context.kt", "fun context() { val x = 0 }")
        val ktFile = file as KtFile
        return ktFile.declarations.single() as KtNamedFunction
    }

    private fun expandInCodeFragment(fragmentText: String): String {
        val context = getContextElement()
        val fragment = KtBlockCodeFragment(project, "fragment.kt", fragmentText, "", context)
        myFixture.configureFromExistingVirtualFile(fragment.virtualFile)
        try {
            myFixture.type("\t")
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
            return editor.document.text
        } finally {
            val templateState = TemplateManagerImpl.getTemplateState(editor)
            if (templateState?.isFinished == false) {
                project.executeCommand("") { templateState.gotoEnd(false) }
            }
        }
    }

    fun testIfPostfixExpandsInCodeFragment() {
        val text = expandInCodeFragment("true.if<caret>")
        assertTrue("Expected 'if (true)' in result, got: $text", text.contains("if (true)"))
    }

    fun testSoutPostfixExpandsInCodeFragment() {
        val text = expandInCodeFragment("\"hello\".sout<caret>")
        assertTrue("Expected 'println' in result, got: $text", text.contains("println"))
    }
}
