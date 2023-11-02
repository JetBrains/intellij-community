// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes.createFromUsage

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class LowPriorityCreateCallableFromUsageFix(
    callableKind: CallableKind,
    callExpression: KtCallExpression,
    private var text: String,
    callableDefinitionToBuildAsString: String,
    nameOfNewCallable: String,
    pointerToContainerOfNewCallable: SmartPsiElementPointer<*>,
) : CreateCallableFromUsageFix<KtCallExpression>(
    callableKind,
    callExpression,
    text,
    callableDefinitionToBuildAsString,
    nameOfNewCallable,
    pointerToContainerOfNewCallable,
), LowPriorityAction

open class CreateCallableFromUsageFix<E : KtExpression>(
    private val callableKind: CallableKind,
    private val expression: E,
    private var text: String,
    private val callableDefinitionToBuildAsString: String,
    private val nameOfNewCallable: String,
    private val pointerToContainerOfNewCallable: SmartPsiElementPointer<*>,
) : KotlinQuickFixAction<E>(expression) {
    override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

    override fun getText(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val callableDefinitionAsPSI = when (callableKind) {
            CallableKind.FUNCTION -> KtPsiFactory(project).createFunction(callableDefinitionToBuildAsString)
            else -> TODO("Not yet implemented")
        }
        val addedPsi = callableDefinitionAsPSI.addToContainer(file)
        editor?.updateCaret(addedPsi)
    }

    private fun KtElement.addToContainer(file: KtFile): PsiElement =
        when (val container = pointerToContainerOfNewCallable.element ?: file) {
            is KtClassOrObject -> {
                val classBody = container.getOrCreateBody()
                classBody.addBefore(this, classBody.rBrace)
            }

            else -> container.add(this)
        }

    private fun Editor.updateCaret(addedPsi: PsiElement) {
        val psiForCaretLocation = addedPsi.findDescendantOfType<PsiElement> { it.text == nameOfNewCallable } ?: addedPsi
        val range = psiForCaretLocation.textRange
        caretModel.moveToOffset(range.startOffset)
        selectionModel.setSelection(range.startOffset, range.endOffset)
    }
}