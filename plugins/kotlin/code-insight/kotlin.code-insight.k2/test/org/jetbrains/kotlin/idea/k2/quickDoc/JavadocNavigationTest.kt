// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.resolveLinkToTarget
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.test.util.invalidateCaches


class JavadocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }

    fun testLinkFromPackage() {
        val file = myFixture.configureByText(
            KotlinFileType.INSTANCE, """package foo.bar 
            | class A
            | """.trimMargin()
        ) as KtFile
        val psiPackage = JavaPsiFacade.getInstance(myFixture.project).findPackage(file.packageFqName.asString())!!
        val target = psiTarget(psiPackage)
        val destination = navigateViaInlineLink(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "foo.bar.A", target)
        assertNotNull("The link isn't resolved", destination)
        assertEquals("A", (destination!!.parent as? KtClass)?.name)
    }

    fun testLinkForClass() {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """ 
            | interface A { fun foo() }
            | """.trimMargin()
        ) as KtFile
        val inheritor = myFixture.addClass(
            """class B implements A {
            |  @Override void foo() {}
            |}""".trimMargin()
        )
        val target = psiTarget(inheritor)
        val destination = navigateViaInlineLink(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "A", target)
        assertNotNull("The link isn't resolved", destination)
        assertEquals("A", (destination!!.parent as? KtClass)?.name)
    }

    fun testLinkForFunction() {
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """ 
            | interface A { fun foo() }
            | """.trimMargin()
        ) as KtFile
        val inheritor = myFixture.addClass(
            """class B implements A {
            |  @Override void foo() {}
            |}""".trimMargin()
        )
        val target = psiTarget(inheritor.methods[0])
        val destination = navigateViaInlineLink(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "A#foo()", target)
        assertNotNull("The link isn't resolved", destination)
        assertEquals("foo", (destination!!.parent as? KtNamedFunction)?.name)
    }

    fun psiTarget(element: PsiElement): DocumentationTarget {
        val targets = timeoutRunBlocking {
            readAction {
                psiDocumentationTargets(element, null)
            }
        }
        assertNotEmpty(targets)
        return targets.first()
    }

    private fun navigateViaInlineLink(url: String, target: DocumentationTarget): PsiElement? {
        val resolvedPointer = timeoutRunBlocking {
            resolveLinkToTarget(target.createPointer(), url)
        } ?: return null

        runInEdtAndWait {
            DocumentationManager.getInstance(project).navigateInlineLink(url) { target }
        }

        val navigatable = resolvedPointer.dereference()?.navigatable ?: return null
        runInEdtAndWait {
            if (navigatable.canNavigate()) navigatable.navigate(true)
        }

        return myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    }
}
