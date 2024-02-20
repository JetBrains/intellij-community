// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints


import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.SpecialNames

context(KtAnalysisSession)
@ApiStatus.Internal
internal fun PresentationTreeBuilder.printKtType(type: KtType) {
    when (type) {
        is KtCapturedType -> text("*")
        is KtDefinitelyNotNullType -> {
            printKtType(type.original)
            text(" & ")
            text(DefaultTypeClassIds.ANY.relativeClassName.asString())
        }
        is KtUsualClassType -> {
            val classId = type.classId
            text(
                shortNameWithCompanionNameSkip(classId),
                InlayActionData(
                    StringInlayActionPayload(classId.asFqNameString()),
                    KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME
                )
            )
            val ownTypeArguments = type.ownTypeArguments
            if (ownTypeArguments.isNotEmpty()) {
                text("<")
                val iterator = ownTypeArguments.iterator()
                while (iterator.hasNext()) {
                    when(val projection = iterator.next()) {
                        is KtStarTypeProjection -> text("*")
                        is KtTypeArgumentWithVariance -> printKtType(projection.type)
                    }
                    if (iterator.hasNext()) text(", ")
                }
                text(">")
            }
        }
        is KtFlexibleType -> {
            val lower = type.lowerBound
            val upper = type.upperBound
            printKtType(lower)
            if (isNullabilityFlexibleType(lower, upper)) {
                text("!")
            }
        }
        is KtTypeParameterType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtTypeParameterTypeRenderer.AS_SOURCE
            val symbol = type.symbol
            val classId = (symbol.upperBounds.firstOrNull { it is KtNonErrorClassType } as? KtNonErrorClassType)?.classId
            text(
                classId?.let { shortNameWithCompanionNameSkip(it) } ?: type.name.asString(),
                classId?.let {
                    InlayActionData(
                        StringInlayActionPayload(classId.asFqNameString()),
                        KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME
                    )
                } ?: symbol.psi?.createSmartPointer()?.let {
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

    if (type.isMarkedNullable) text("?")
}

private fun isNullabilityFlexibleType(lower: KtType, upper: KtType): Boolean {
    val isTheSameType = lower is KtNonErrorClassType && upper is KtNonErrorClassType && lower.classId == upper.classId ||
            lower is KtTypeParameterType && upper is KtTypeParameterType && lower.symbol == upper.symbol
    if (isTheSameType &&
        lower.nullability == KtTypeNullability.NON_NULLABLE
        && upper.nullability == KtTypeNullability.NULLABLE
    ) {
        if (lower !is KtNonErrorClassType && upper !is KtNonErrorClassType) {
            return true
        }
        if (lower is KtNonErrorClassType && upper is KtNonErrorClassType) {
            val lowerOwnTypeArguments = lower.ownTypeArguments
            val upperOwnTypeArguments = upper.ownTypeArguments
            if (lowerOwnTypeArguments.size == upperOwnTypeArguments.size) {
                for ((index, ktTypeProjection) in lowerOwnTypeArguments.withIndex()) {
                    if (upperOwnTypeArguments[index].type != ktTypeProjection.type) {
                        return false
                    }
                }
                return true
            }
        }
    }
    return false
}

private fun shortNameWithCompanionNameSkip(classId: ClassId): String {
    return classId.relativeClassName.pathSegments()
        .filter { it != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT }
        .joinToString(".")
}