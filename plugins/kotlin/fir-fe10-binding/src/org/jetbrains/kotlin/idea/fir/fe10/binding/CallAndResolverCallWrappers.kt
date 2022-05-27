// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.fe10.binding

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.idea.fir.fe10.*
import org.jetbrains.kotlin.idea.fir.fe10.FirWeakReference
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getOrBuildFir
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class CallAndResolverCallWrappers(bindingContext: KtSymbolBasedBindingContext) {
    private val context = bindingContext.context

    init {
        bindingContext.registerGetterByKey(BindingContext.CALL, this::getCall)
        bindingContext.registerGetterByKey(BindingContext.RESOLVED_CALL, this::getResolvedCall)
        bindingContext.registerGetterByKey(BindingContext.REFERENCE_TARGET, this::getReferenceTarget)
    }

    private fun getCall(element: KtElement): Call {
        val ktCall = element.parent.safeAs<KtCallExpression>()
        if (ktCall == null) {
            if (element is KtNameReferenceExpression) return getCallForVariable(element)
            context.implementationPostponed()
        }

        val firCall = when (val fir = ktCall.getOrBuildFir(context.ktAnalysisSessionFacade.firResolveSession)) {
            is FirFunctionCall -> fir
            is FirSafeCallExpression -> fir.selector as? FirFunctionCall
            else -> null
        }

        if (firCall != null) return FunctionFe10WrapperCall(ktCall, FirWeakReference(firCall, context.ktAnalysisSessionFacade.analysisSession.token), context)

        // other calls, figure out later
        context.implementationPostponed()
    }

    private fun getCallForVariable(element: KtNameReferenceExpression): Call {
        val fir = element.getOrBuildFir(context.ktAnalysisSessionFacade.firResolveSession)

        val propertyAccess: FirPropertyAccessExpression = when (fir) {
            is FirPropertyAccessExpression -> fir

            is FirExpressionWithSmartcast ->
                fir.originalExpression.safeAs<FirPropertyAccessExpression>()
                ?: context.noImplementation("Unexpected type of fir: $fir")
            else -> context.noImplementation("Unexpected type of fir: $fir")
        }

        return VariableFe10WrapperCall(element, FirWeakReference(propertyAccess, context.ktAnalysisSessionFacade.analysisSession.token), context)
    }

    private fun getResolvedCall(call: Call): ResolvedCall<*> {
        check(call is Fe10WrapperCall<*>) {
            "Incorrect Call type: $call"
        }
        return when(call) {
            is FunctionFe10WrapperCall -> FunctionFe10WrapperResolvedCall(call)
            is VariableFe10WrapperCall -> VariableFe10WrapperResolvedCall(call)
        }
    }

    private fun getReferenceTarget(key: KtReferenceExpression): DeclarationDescriptor? {
        val ktSymbol = context.withAnalysisSession { key.mainReference.resolveToSymbols().singleOrNull() } ?: return null
        return ktSymbol.toDeclarationDescriptor(context)
    }
}
