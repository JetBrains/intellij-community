// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.file.structure

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.platform.modification.KaElementModificationType
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.LLFirDeclarationModificationService
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.handleElementModification
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * In the IDE, [LLFirDeclarationModificationService] might be called with PSI elements received via PSI tree change events. Because such
 * elements may currently be in the process of modification, they may be inconsistent. This test ensures that the declaration modification
 * service can handle such inconsistent PSI, without throwing exceptions.
 *
 * The element to modify is marked in the test data with a `<selection>...</selection>` marker.
 *
 * Previously, the test lived in LL Analysis API on the Kotlin compiler side.
 * However, PSI modifications are not supported there anymore as modification services were moved out of the compiler in KT-85052,
 * see [org.jetbrains.kotlin.psi.KtPsiMutationService].
 */
abstract class AbstractDeclarationModificationServicePsiResilienceTest : KotlinLightCodeInsightFixtureTestCase() {
    protected abstract fun modifySelectedElement(element: PsiElement)

    @OptIn(KaImplementationDetail::class, LLFirInternals::class)
    fun doTest(path: String) {
        val testDataFile = File(path)
        val fileText = testDataFile.readText()
        myFixture.configureByText(testDataFile.name, fileText)

        val ktFile = myFixture.file as KtFile
        val selectedElement = getSelectedElement(ktFile)

        /**
        * The test passes when [LLFirDeclarationModificationService] throws no exceptions. Both the PSI modification and the handling must
        * run in a write action ([handleElementModification] explicitly requires it, and PSI deletion is only allowed under one).
        */
        WriteCommandAction.runWriteCommandAction(project) {
            modifySelectedElement(selectedElement)
            val declarationModificationService = LLFirDeclarationModificationService.getInstance(project)
            declarationModificationService.handleElementModification(selectedElement, KaElementModificationType.Unknown)
        }
    }

    private fun getSelectedElement(ktFile: KtFile): KtElement {
        val selectionModel = myFixture.editor.selectionModel
        require(selectionModel.hasSelection()) {
            "Expected a `<selection>...</selection>` marker in the test file."
        }

        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd

        return PsiTreeUtil.findElementOfClassAtRange(ktFile, start, end, KtElement::class.java)
            ?: error("Expected a single `KtElement` in the selection range [$start, $end).")
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified E : PsiElement> assertSelectedElementType(selectedElement: PsiElement) {
    contract { returns() implies (selectedElement is E) }
    if (selectedElement !is E) {
        error("Expected the selected element to be a ${E::class.simpleName}. Selected element: $selectedElement")
    }
}

abstract class AbstractDeclarationModificationServiceCallExpressionCalleeResilienceTest :
    AbstractDeclarationModificationServicePsiResilienceTest() {
    override fun modifySelectedElement(element: PsiElement) {
        assertSelectedElementType<KtCallExpression>(element)

        val calleeExpression = element.calleeExpression
        require(calleeExpression != null) {
            "A consistent call expression should have a callee expression. Expression: $element"
        }
        calleeExpression.delete()
    }
}

abstract class AbstractDeclarationModificationServiceDotQualifiedExpressionReceiverResilienceTest :
    AbstractDeclarationModificationServicePsiResilienceTest() {
    override fun modifySelectedElement(element: PsiElement) {
        assertSelectedElementType<KtDotQualifiedExpression>(element)
        element.checkConsistency()

        element.firstChild.delete()
    }
}

abstract class AbstractDeclarationModificationServiceDotQualifiedExpressionSelectorResilienceTest :
    AbstractDeclarationModificationServicePsiResilienceTest() {
    override fun modifySelectedElement(element: PsiElement) {
        assertSelectedElementType<KtDotQualifiedExpression>(element)
        element.checkConsistency()

        element.firstChild.delete()
        element.operationTokenNode.psi.delete()
    }
}

private fun KtDotQualifiedExpression.checkConsistency() {
    require(children.size == 2) {
        "A consistent dot-qualified expression should have two children. Expression: $this"
    }
}

abstract class AbstractDeclarationModificationServicePropertyDeclarationInitializerResilienceTest :
    AbstractDeclarationModificationServicePsiResilienceTest() {
    override fun modifySelectedElement(element: PsiElement) {
        assertSelectedElementType<KtProperty>(element)

        val initializer = element.initializer
        require(initializer != null) {
            "The property declaration is expected to have an initializer. Property declaration: $element"
        }
        initializer.delete()
    }
}
