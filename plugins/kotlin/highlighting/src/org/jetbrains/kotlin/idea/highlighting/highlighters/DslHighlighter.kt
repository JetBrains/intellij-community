// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationClassIds
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.base.highlighting.dsl.DslStyleUtils
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass

context(KtAnalysisSession)
internal class DslHighlighter(holder: HighlightInfoHolder) : KotlinSemanticAnalyzer(holder) {
    override fun visitCallExpression(expression: KtCallExpression) {
        holder.add(highlightCall(expression)?.create())
    }

    /**
     * Highlights the call expression in case it's a DSL function (it has a single lambda argument)
     * and its receiver is a DSL class. The receiver is considered to be a DSL class if:
     * 1) Its type specifier is marked with an annotation, that is marked by a dsl annotation
     * 2) The class or its superclasses' definition is marked by a dsl annotation
     */
    context(KtAnalysisSession)
    private fun highlightCall(element: KtCallExpression): HighlightInfo.Builder? {
        val calleeExpression = element.calleeExpression ?: return null
        val lambdaExpression = element.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return null
        val receiverType = (lambdaExpression.getKtType() as? KtFunctionalType)?.receiverType ?: return null
        val dslAnnotation = getDslAnnotation(receiverType) ?: return null

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
        val classSymbol = getNamedClassOrObjectSymbol()?.takeIf {
            it.classKind == KtClassKind.ANNOTATION_CLASS && it.isDslHighlightingMarker()
        } ?: return null
        val className = classSymbol.classIdIfNonLocal?.asSingleFqName() ?: return null
        return DslStyleUtils.styleIdByFQName(className)
    }
}

/**
 * Returns a dsl annotation for a given type (or for one of the supertypes), if there is one.
 * A Dsl annotation is an annotation that is itself marked by [DslMarker] annotation.
 */
context(KtAnalysisSession)
private fun getDslAnnotation(type: KtType): ClassId? {
    val allAnnotationsWithSuperTypes = sequence {
        yieldAll(type.annotationClassIds)
        val symbol = type.expandedClassSymbol ?: return@sequence
        yieldAll(symbol.annotationClassIds)
        for (superType in type.getAllSuperTypes()) {
            superType.expandedClassSymbol?.let { yieldAll(it.annotationClassIds) }
        }
    }
    val dslAnnotation = allAnnotationsWithSuperTypes.find { annotationClassId ->
        val symbol = getClassOrObjectSymbolByClassId(annotationClassId) ?: return@find false
        symbol.isDslHighlightingMarker()
    }
    return dslAnnotation
}

private fun KtClassOrObjectSymbol.isDslHighlightingMarker(): Boolean {
    return hasAnnotation(DslStyleUtils.DSL_MARKER_CLASS_ID)
}