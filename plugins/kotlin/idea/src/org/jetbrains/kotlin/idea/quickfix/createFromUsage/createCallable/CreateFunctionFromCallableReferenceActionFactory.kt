// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.util.SmartList
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.getValueParameterTypesFromFunctionType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.*
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.ifEmpty

object CreateFunctionFromCallableReferenceActionFactory : CreateCallableMemberFromUsageFactory<KtCallableReferenceExpression>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtCallableReferenceExpression? {
        return diagnostic.psiElement.getStrictParentOfType<KtCallableReferenceExpression>()
    }

    override fun extractFixData(element: KtCallableReferenceExpression, diagnostic: Diagnostic): List<CallableInfo> {
        val containers = element.getExtractionContainers(includeAll = true)
        if (containers.isEmpty()) return emptyList()
        val name = element.callableReference.getReferencedName()
        val resolutionFacade = element.getResolutionFacade()
        val context = resolutionFacade.analyze(element, BodyResolveMode.PARTIAL_WITH_CFA)
        val receiverExpression = element.receiverExpression
        val qualifierType = context.get(BindingContext.DOUBLE_COLON_LHS, receiverExpression)?.type
        val receiverTypeInfo = qualifierType?.let { TypeInfo(it, Variance.IN_VARIANCE) } ?: TypeInfo.Empty
        return element.guessTypes(context, resolutionFacade.moduleDescriptor)
            .ifEmpty { element.guessTypes(context, resolutionFacade.moduleDescriptor, allowErrorTypes = true) } // approximate with Any
            .filter(KotlinType::isFunctionType)
            .map { type ->
                val expectedReceiverType = type.getReceiverTypeFromFunctionType()
                val returnTypeInfo = TypeInfo(type.getReturnTypeFromFunctionType(), Variance.OUT_VARIANCE)
                val parameterInfos = SmartList<ParameterInfo>().apply {
                    if (receiverExpression == null && expectedReceiverType != null) {
                        add(ParameterInfo(TypeInfo(expectedReceiverType, Variance.IN_VARIANCE)))
                    }

                    type.getValueParameterTypesFromFunctionType()
                        .let {
                            if (receiverExpression != null &&
                                qualifierType == it.firstOrNull()?.type &&
                                expectedReceiverType == null &&
                                it.isNotEmpty()
                            ) it.subList(1, it.size) else it
                        }
                        .mapTo(this) {
                            ParameterInfo(TypeInfo(it.type, it.projectionKind))
                        }
                }

                FunctionInfo(name, receiverTypeInfo, returnTypeInfo, containers, parameterInfos)
            }
    }
}
