// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.KtTypeProjection
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KtCapturedType
import org.jetbrains.kotlin.analysis.api.types.KtDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KtDynamicType
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtIntegerLiteralType
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinFqnDeclarativeInlayActionHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds

context(KtAnalysisSession)
@ApiStatus.Internal
internal fun PresentationTreeBuilder.printKtType(type: KtType) {
    var markedNullable = type.isMarkedNullable
    when (type) {
        is KtCapturedType -> text("*")
        is KtDefinitelyNotNullType -> {
            printKtType(type.original)
            text(" & ")
            text(DefaultTypeClassIds.ANY.relativeClassName.asString())
        }
        is KtUsualClassType -> printNonErrorClassType(type)
        is KtFlexibleType -> {
            val lower = type.lowerBound
            val upper = type.upperBound
            markedNullable = lower.isMarkedNullable
            if (isMutabilityFlexibleType(lower, upper)) {
                text("(")
                printClassId((lower as KtNonErrorClassType).classId, "Mutable")
                text(")")
                printKtType(upper.withNullability(KtTypeNullability.NON_NULLABLE))
            } else {
                if (isNullabilityFlexibleType(lower, upper)) {
                    printKtType(lower)
                    text("!")
                } else if (isNonNullableFlexibleType(upper, lower)) {
                    printNonErrorClassType(upper as KtNonErrorClassType, lower as KtNonErrorClassType)
                } else {
                    // fallback case
                    printKtType(lower)
                    text("..")
                    printKtType(upper)
                }
            }
        }
        is KtTypeParameterType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeParameterTypeRenderer.AS_SOURCE
            val symbol = type.symbol
            text(
                type.name.asString(),
                symbol.psi?.createSmartPointer()?.let {
                    InlayActionData(
                        PsiPointerInlayActionPayload(it),
                        PsiPointerInlayActionNavigationHandler.HANDLER_ID
                    )
                }
            )
        }
        is KtIntegerLiteralType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtIntegerLiteralTypeRenderer.AS_ILT_WITH_VALUE
            text("ILT(${type.value})")
        }
        is KtIntersectionType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtIntersectionTypeRenderer.AS_INTERSECTION
            val iterator = type.conjuncts.iterator()
            while (iterator.hasNext()) {
                printKtType(iterator.next())
                if (iterator.hasNext()) {
                    text(" & ")
                }
            }
        }
        is KtFunctionalType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
            type.receiverType?.let {
                printKtType(it)
                text(".")
            }
            val iterator = type.parameterTypes.iterator()
            if (iterator.hasNext()) {
                text("(")
                while (iterator.hasNext()) {
                    printKtType(iterator.next())
                    if (iterator.hasNext()) {
                        text(", ")
                    }
                }
                text(")")
            }
            text(" -> ")
            printKtType(type.returnType)
        }
        is KtDynamicType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtDynamicTypeRenderer.AS_DYNAMIC_WORD
            text(KtTokens.DYNAMIC_KEYWORD.value)
        }
        is KtErrorType -> {
            // error types should not be printed
        }
    }

    if (markedNullable) text("?")
}

context(KtAnalysisSession)
private fun PresentationTreeBuilder.printNonErrorClassType(type: KtNonErrorClassType, anotherType: KtNonErrorClassType? = null) {
    type.classId.let { printClassId(it, shortNameWithCompanionNameSkip(it)) }

    val ownTypeArguments = type.ownTypeArguments
    if (ownTypeArguments.isNotEmpty()) {
        text("<")

        val anotherOwnTypeArguments = anotherType?.ownTypeArguments
        val iterator = ownTypeArguments.iterator()
        val anotherIterator = anotherOwnTypeArguments?.iterator()
        while (iterator.hasNext()) {
            val projection = iterator.next()
            val anotherProjection = anotherIterator?.takeIf { it.hasNext() }?.next()

            printProjection(projection, anotherProjection != null && projection != anotherProjection)

            if (iterator.hasNext()) text(", ")
        }

        text(">")
    }
}


context(KtAnalysisSession)
private fun PresentationTreeBuilder.printProjection(projection: KtTypeProjection, optionalProjection: Boolean) {
    fun String.asOptional(optional: Boolean): String =
        if (optional) "($this)" else this

    when (projection) {
        is KtStarTypeProjection -> {
            text("*".asOptional(optionalProjection))
        }
        is KtTypeArgumentWithVariance -> {
            projection.variance.label.takeIf { it.isNotEmpty() }?.let {
                text("${it.asOptional(optionalProjection)} ")
            }
            printKtType(projection.type)
        }
    }
}


private fun PresentationTreeBuilder.printClassId(classId: ClassId, name: String) {
    text(
        name,
        InlayActionData(
            StringInlayActionPayload(classId.asFqNameString()),
            KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME
        )
    )
}

private fun isMutabilityFlexibleType(lower: KtType, upper: KtType): Boolean {
    // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtFlexibleTypeRenderer.AS_SHORT.isMutabilityFlexibleType
    if (lower !is KtNonErrorClassType || upper !is KtNonErrorClassType) return false

    return (StandardClassIds.Collections.mutableCollectionToBaseCollection[lower.classId] == upper.classId)
}


private fun isNullabilityFlexibleType(lower: KtType, upper: KtType): Boolean {
    if (lower.nullability == KtTypeNullability.NON_NULLABLE && upper.nullability == KtTypeNullability.NULLABLE) {
        if (lower is KtTypeParameterType && upper is KtTypeParameterType && lower.symbol == upper.symbol) {
            return true
        }
        if (lower is KtNonErrorClassType && upper is KtNonErrorClassType && lower.classId == upper.classId) {
            return isSimilarTypes(lower, upper)
        }
    }
    return false
}

private fun isNonNullableFlexibleType(lower: KtType, upper: KtType): Boolean {
    if (lower is KtNonErrorClassType && upper is KtNonErrorClassType &&
        lower.classId == upper.classId &&
        lower.nullability == KtTypeNullability.NON_NULLABLE &&
        upper.nullability == lower.nullability &&
        isSimilarTypes(lower, upper)
    ) {
        val lowerTypeArguments = lower.ownTypeArguments
        val upperTypeArguments = upper.ownTypeArguments
        return lowerTypeArguments.isNotEmpty() && lowerTypeArguments.zip(upperTypeArguments)
            .any { (lowerTypeArg, upperTypeArg) ->
                lowerTypeArg != upperTypeArg
            }
    }
    return false
}

private fun isSimilarTypes(
    lower: KtNonErrorClassType,
    upper: KtNonErrorClassType
): Boolean = lower.ownTypeArguments.zip(upper.ownTypeArguments)
    .none { (lowerTypeArg, upperTypeArg) -> lowerTypeArg.type != upperTypeArg.type }

private fun shortNameWithCompanionNameSkip(classId: ClassId): String {
    return classId.relativeClassName.pathSegments()
        .filter { it != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT }
        .joinToString(".")
}