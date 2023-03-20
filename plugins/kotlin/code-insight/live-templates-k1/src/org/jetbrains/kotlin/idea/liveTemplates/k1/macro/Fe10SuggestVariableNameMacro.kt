// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k1.macro

import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.IterableTypesDetection
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractSuggestVariableNameMacro
import org.jetbrains.kotlin.idea.resolve.ideService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class Fe10SuggestVariableNameMacro(private val defaultName: String? = null) : AbstractSuggestVariableNameMacro() {
    override fun suggestNames(declaration: KtCallableDeclaration): Collection<String> {
        val nameValidator = Fe10KotlinNewDeclarationNameValidator(
            declaration,
            declaration.siblings(withItself = false),
            KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
        )

        val parent = declaration.parent
        if (parent is KtForExpression && declaration == parent.loopParameter) {
            suggestIterationVariableName(parent, nameValidator)?.let { return it }
        }

        val descriptor = declaration.resolveToDescriptorIfAny() as? VariableDescriptor ?: return emptyList()
        return defaultName?.let { listOf(Fe10KotlinNameSuggester.suggestNameByName(it, nameValidator)) }
            ?: Fe10KotlinNameSuggester.suggestNamesByType(descriptor.type, nameValidator, null)
    }

    private fun suggestIterationVariableName(forExpression: KtForExpression, nameValidator: (String) -> Boolean): Collection<String>? {
        val loopRange = forExpression.loopRange ?: return null
        val resolutionFacade = forExpression.getResolutionFacade()
        val bindingContext = resolutionFacade.analyze(loopRange, BodyResolveMode.PARTIAL)
        val type = bindingContext.getType(loopRange) ?: return null
        val scope = loopRange.getResolutionScope(bindingContext, resolutionFacade)
        val detector = resolutionFacade.ideService<IterableTypesDetection>().createDetector(scope)
        val elementType = detector.elementType(type)?.type ?: return null
        return Fe10KotlinNameSuggester.suggestIterationVariableNames(loopRange, elementType, bindingContext, nameValidator, null)
    }
}