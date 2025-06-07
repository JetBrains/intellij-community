// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinDslSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinFunctionCallSemanticAnalyzer(holder, session) {
    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        val referenceExpression = expression.operationReference
        val dslExpressionHighlightType = expressionHighlightType(referenceExpression)
        if (dslExpressionHighlightType != null) {
            highlightElement(referenceExpression, dslExpressionHighlightType)
        } else {
            super.visitBinaryExpression(expression)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val calleeExpression = expression.calleeExpression ?: return
        val dslExpressionHighlightType = expressionHighlightType(calleeExpression)
        if (dslExpressionHighlightType != null) {
            highlightElement(calleeExpression, dslExpressionHighlightType)
        } else {
            super.visitCallExpression(expression)
        }
    }

    /**
     * Highlights the expression in case it's a DSL function (it has a single lambda argument),
     * and its receiver is a DSL class. The receiver is considered to be a DSL class if:
     * 1) Its type specifier is marked with an annotation, that is marked by a dsl annotation
     * 2) The class or its superclasses' definition is marked by a dsl annotation
     */
    private fun expressionHighlightType(expression: KtExpression): HighlightInfoType? {
        val dslAnnotation = with(session) {
            val functionCall = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            // function declaration argument has a dsl marker

            // to check function declaration arguments type, rather call site
            // to avoid cases with generics like `apply` those have no dsl markers
            functionCall.symbol.valueParameters.forEach { parameterSymbol ->
                val receiverType = (parameterSymbol.returnType as? KaFunctionType)?.receiverType
                receiverType?.let { type ->
                    getDslAnnotation(type)?.let { return@with it }
                }
            }

            // function has a dsl marker
            val symbol = functionCall.symbol

            // in context of implicit receiver
            val dispatchReceiver = functionCall.partiallyAppliedSymbol.dispatchReceiver
            val dispatchReceiverType = (dispatchReceiver as? KaImplicitReceiverValue)?.type as? KaClassType
            dispatchReceiverType?.symbol?.let(::firstDslAnnotationOrNull)?.let { return@with it }

            // in case of ext function
            firstDslAnnotationOrNull(symbol)
        } ?: return null

        val dslStyleId = DslStyleUtils.styleIdByFQName(dslAnnotation.asSingleFqName())
        val highlightInfoType = DslStyleUtils.typeById(dslStyleId)
        return highlightInfoType
    }
}

/**
 * Returns a dsl style ID for the given annotation [KtClass]. This class must be annotated with [DslMarker].
 */
fun KtClass.getDslStyleId(): Int? {
    if (!isAnnotation() || annotationEntries.isEmpty()) {
        return null
    }
    val dslAnnotation = analyze(this) {
        getDslAnnotation(namedClassSymbol)
    } ?: return null
    return DslStyleUtils.styleIdByFQName(dslAnnotation.asSingleFqName())
}

private fun getDslAnnotation(namedClassSymbol: KaNamedClassSymbol?): ClassId? {
    val classSymbol = namedClassSymbol?.takeIf {
        it.classKind == KaClassKind.ANNOTATION_CLASS && it.isDslHighlightingMarker()
    }
    return classSymbol?.classId
}

/**
 * Returns a dsl annotation for a given symbol (or for one of its supertypes), if there is one.
 * A Dsl annotation is an annotation that is itself marked by [DslMarker] annotation.
 */
private fun KaSession.firstDslAnnotationOrNull(symbol: KaDeclarationSymbol): ClassId? {
    val allAnnotationsWithSuperTypes = sequence {
        yieldAll(symbol.annotations.classIds)
        if (symbol is KaClassSymbol) {
            for (superType in symbol.superTypes) {
                superType.expandedSymbol?.let { yieldAll(it.annotations.classIds) }
            }
        }
    }
    return allAnnotationsWithSuperTypes.find {
        findClass(it)?.isDslHighlightingMarker() == true
    }
}

private fun KaSession.getDslAnnotation(type: KaType): ClassId? {
    val allAnnotationsWithSuperTypes = sequence {
        yieldAll(type.annotations.classIds)
        type.symbol?.let { yieldAll(it.annotations.classIds) }
        for (superType in type.allSupertypes) {
            superType.expandedSymbol?.let { yieldAll(it.annotations.classIds) }
        }
    }
    val dslAnnotation = allAnnotationsWithSuperTypes.find { annotationClassId ->
        findClass(annotationClassId)?.isDslHighlightingMarker() ?: false
    }
    return dslAnnotation
}

private fun KaClassSymbol.isDslHighlightingMarker(): Boolean {
    return DslStyleUtils.DSL_MARKER_CLASS_ID in annotations
}