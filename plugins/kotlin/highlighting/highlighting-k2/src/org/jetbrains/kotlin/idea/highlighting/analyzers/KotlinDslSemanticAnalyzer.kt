// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.idea.highlighter.HighlightingFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass

internal class KotlinDslSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinSemanticAnalyzer(holder, session) {
    override fun visitCallExpression(expression: KtCallExpression) {
        holder.add(highlightCall(expression)?.create())
    }

    /**
     * Highlights the call expression in case it's a DSL function (it has a single lambda argument)
     * and its receiver is a DSL class. The receiver is considered to be a DSL class if:
     * 1) Its type specifier is marked with an annotation, that is marked by a dsl annotation
     * 2) The class or its superclasses' definition is marked by a dsl annotation
     */
    private fun highlightCall(element: KtCallExpression): HighlightInfo.Builder? {
        val calleeExpression = element.calleeExpression ?: return null
        val lambdaExpression = element.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return null
        val dslAnnotation = with(session) {
            val receiverType = (lambdaExpression.expressionType as? KaFunctionType)?.receiverType ?: return null
            getDslAnnotation(receiverType) ?: return null
        }

        val dslStyleId = DslStyleUtils.styleIdByFQName(dslAnnotation.asSingleFqName())
        return HighlightingFactory.highlightName(
            calleeExpression,
            DslStyleUtils.typeById(dslStyleId),
            DslStyleUtils.styleOptionDisplayName(dslStyleId)
        )
    }
}

/**
 * Returns a dsl style ID for the given annotation [KtClass]. This class must be annotated with [DslMarker].
 */
fun KtClass.getDslStyleId(): Int? {
    if (!isAnnotation() || annotationEntries.isEmpty()) {
        return null
    }
    analyze(this) {
        val classSymbol = namedClassSymbol?.takeIf {
            it.classKind == KaClassKind.ANNOTATION_CLASS && it.isDslHighlightingMarker()
        } ?: return null
        val className = classSymbol.classId?.asSingleFqName() ?: return null
        return DslStyleUtils.styleIdByFQName(className)
    }
}

/**
 * Returns a dsl annotation for a given type (or for one of the supertypes), if there is one.
 * A Dsl annotation is an annotation that is itself marked by [DslMarker] annotation.
 */
private fun KaSession.getDslAnnotation(type: KaType): ClassId? {
    val allAnnotationsWithSuperTypes = sequence {
        yieldAll(type.annotations.classIds)
        val symbol = type.expandedSymbol ?: return@sequence
        yieldAll(symbol.annotations.classIds)
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