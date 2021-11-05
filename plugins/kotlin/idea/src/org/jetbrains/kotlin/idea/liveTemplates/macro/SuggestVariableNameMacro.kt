// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.IterableTypesDetection
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.resolve.ideService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class SuggestVariableNameMacro : KotlinMacro() {
    override fun getName() = "kotlinSuggestVariableName"
    override fun getPresentableName() = "kotlinSuggestVariableName()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? {
        return suggestNames(context).firstOrNull()?.let(::TextResult)
    }

    override fun calculateLookupItems(params: Array<out Expression>, context: ExpressionContext): Array<out LookupElement>? {
        val suggestions = suggestNames(context)
        if (suggestions.size < 2) return null
        return suggestions.map { LookupElementBuilder.create(it) }.toTypedArray()
    }

    private fun suggestNames(context: ExpressionContext): Collection<String> {
        val project = context.project
        val psiDocumentManager = PsiDocumentManager.getInstance(project)

        val document = context.editor!!.document
        psiDocumentManager.commitDocument(document)
        val psiFile = psiDocumentManager.getPsiFile(document) as? KtFile ?: return emptyList()
        val token = psiFile.findElementAt(context.startOffset) ?: return emptyList()
        val declaration = token.parent as? KtCallableDeclaration ?: return emptyList()
        if (token != declaration.nameIdentifier) return emptyList()

        val nameValidator: (String) -> Boolean = { true }

        val initializer = (declaration as? KtDeclarationWithInitializer)?.initializer
        if (initializer != null) {
            val bindingContext = initializer.analyze(BodyResolveMode.PARTIAL)
            return KotlinNameSuggester.suggestNamesByExpressionAndType(initializer, null, bindingContext, nameValidator, null)
        }

        val parent = declaration.parent
        if (parent is KtForExpression && declaration == parent.loopParameter) {
            suggestIterationVariableName(parent, nameValidator)?.let { return it }
        }

        val descriptor = declaration.resolveToDescriptorIfAny() as? VariableDescriptor ?: return emptyList()
        return KotlinNameSuggester.suggestNamesByType(descriptor.type, nameValidator, null)
    }

    private fun suggestIterationVariableName(forExpression: KtForExpression, nameValidator: (String) -> Boolean): Collection<String>? {
        val loopRange = forExpression.loopRange ?: return null
        val resolutionFacade = forExpression.getResolutionFacade()
        val bindingContext = resolutionFacade.analyze(loopRange, BodyResolveMode.PARTIAL)
        val type = bindingContext.getType(loopRange) ?: return null
        val scope = loopRange.getResolutionScope(bindingContext, resolutionFacade)
        val detector = resolutionFacade.ideService<IterableTypesDetection>().createDetector(scope)
        val elementType = detector.elementType(type)?.type ?: return null
        return KotlinNameSuggester.suggestIterationVariableNames(loopRange, elementType, bindingContext, nameValidator, null)
    }
}
