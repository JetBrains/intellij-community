// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core.util

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor

/**
 * Analyzes all inline function calls in [file] and returns the list of files (including [file]) that contain all reachable inline
 * functions.
 */
fun analyzeInlinedFunctions(
    resolutionFacadeForFile: ResolutionFacade,
    file: KtFile,
    analyzeOnlyReifiedInlineFunctions: Boolean,
): List<KtFile> {
    val analyzedElements = HashSet<KtElement>()
    analyzeElementWithInline(
        resolutionFacadeForFile,
        file,
        analyzedElements,
        !analyzeOnlyReifiedInlineFunctions,
    )
    return analyzedElements.mapTo(mutableSetOf(file)) { it.containingKtFile }.toList()
}

private fun analyzeElementWithInline(
    resolutionFacade: ResolutionFacade,
    element: KtElement,
    analyzedElements: MutableSet<KtElement>,
    analyzeInlineFunctions: Boolean,
) {
    val project = element.project
    val declarationsWithBody = HashSet<KtDeclarationWithBody>()

    element.accept(object : KtTreeVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            super.visitExpression(expression)

            val bindingContext = resolutionFacade.analyze(expression)
            val call = bindingContext.get(BindingContext.CALL, expression) ?: return
            val resolvedCall = bindingContext.get(BindingContext.RESOLVED_CALL, call)
            checkResolveCall(resolvedCall)
        }

        override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
            super.visitDestructuringDeclaration(destructuringDeclaration)

            val bindingContext = resolutionFacade.analyze(destructuringDeclaration)

            for (entry in destructuringDeclaration.entries) {
                val resolvedCall = bindingContext.get(BindingContext.COMPONENT_RESOLVED_CALL, entry)
                checkResolveCall(resolvedCall)
            }
        }

        override fun visitForExpression(expression: KtForExpression) {
            super.visitForExpression(expression)

            val bindingContext = resolutionFacade.analyze(expression)

            checkResolveCall(bindingContext.get(BindingContext.LOOP_RANGE_ITERATOR_RESOLVED_CALL, expression.loopRange))
            checkResolveCall(bindingContext.get(BindingContext.LOOP_RANGE_HAS_NEXT_RESOLVED_CALL, expression.loopRange))
            checkResolveCall(bindingContext.get(BindingContext.LOOP_RANGE_NEXT_RESOLVED_CALL, expression.loopRange))
        }

        private fun checkResolveCall(resolvedCall: ResolvedCall<*>?) {
            if (resolvedCall == null) return

            val descriptor = resolvedCall.resultingDescriptor
            if (descriptor is DeserializedSimpleFunctionDescriptor) return

            isAdditionalResolveNeededForDescriptor(descriptor)

            if (descriptor is PropertyDescriptor) {
                for (accessor in descriptor.accessors) {
                    isAdditionalResolveNeededForDescriptor(accessor)
                }
            }
        }

        private fun isAdditionalResolveNeededForDescriptor(descriptor: CallableDescriptor) {
            if (!(InlineUtil.isInline(descriptor) && (analyzeInlineFunctions || hasReifiedTypeParameters(descriptor)))) {
                return
            }

            val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
            if (declaration != null && declaration is KtDeclarationWithBody && !analyzedElements.contains(declaration)) {
                declarationsWithBody.add(declaration)
                return
            }
        }
    })

    analyzedElements.add(element)

    if (declarationsWithBody.isNotEmpty()) {
        for (inlineFunction in declarationsWithBody) {
            val body = inlineFunction.bodyExpression
            if (body != null) {
                analyzeElementWithInline(
                    resolutionFacade,
                    inlineFunction,
                    analyzedElements,
                    analyzeInlineFunctions
                )
            }
        }

        analyzedElements.addAll(declarationsWithBody)
    }
}

private fun hasReifiedTypeParameters(descriptor: CallableDescriptor): Boolean {
    return descriptor.typeParameters.any { it.isReified }
}