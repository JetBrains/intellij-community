// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinFqnDeclarativeInlayActionHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
@ApiStatus.Internal
internal fun PresentationTreeBuilder.printKtType(type: KaType) {
    // See org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer.renderType
    type.abbreviation?.let { abbreviatedType ->
        printKtType(abbreviatedType)
        return
    }

    var markedNullable = type.isMarkedNullable
    when (type) {
        is KaCapturedType -> text("*")
        is KaDefinitelyNotNullType -> {
            printKtType(type.original)
            text(" & ")
            text(DefaultTypeClassIds.ANY.relativeClassName.asString())
        }
        is KaUsualClassType -> printNonErrorClassType(type)
        is KaFlexibleType -> {
            val lower = type.lowerBound
            val upper = type.upperBound
            markedNullable = lower.isMarkedNullable
            if (isMutabilityFlexibleType(lower, upper)) {
                text("(")
                printClassId((lower as KaClassType).classId, "Mutable")
                text(")")
                printKtType(upper.withNullability(false))
            } else {
                if (isNullabilityFlexibleType(lower, upper)) {
                    printKtType(lower)
                    text("!")
                } else if (isNonNullableFlexibleType(upper, lower)) {
                    printNonErrorClassType(upper as KaClassType, lower as KaClassType)
                } else {
                    // fallback case
                    text("(")
                    printKtType(lower)
                    text("..")
                    printKtType(upper)
                    text(")")
                }
            }
        }
        is KaTypeParameterType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaTypeParameterTypeRenderer.AS_SOURCE
            val symbol = type.symbol
            printSymbolPsi(symbol, type.name.asString())
        }
        is KaIntersectionType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtIntersectionTypeRenderer.AS_INTERSECTION
            val iterator = type.conjuncts.iterator()
            while (iterator.hasNext()) {
                printKtType(iterator.next())
                if (iterator.hasNext()) {
                    text(" & ")
                }
            }
        }
        is KaFunctionType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
            if (type.isSuspend) {
                text(KtTokens.SUSPEND_KEYWORD.value)
                text(" ")
            }
            type.receiverType?.let {
                printKtType(it)
                text(".")
            }
            val iterator = type.parameters.iterator()
            text("(")
            while (iterator.hasNext()) {
                val valueParameter = iterator.next()
                if (valueParameter.name != null)
                    text("${valueParameter.name}: ")
                printKtType(valueParameter.type)
                if (iterator.hasNext()) {
                    text(", ")
                }
            }
            text(") -> ")
            printKtType(type.returnType)
        }
        is KaDynamicType -> {
            // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtDynamicTypeRenderer.AS_DYNAMIC_WORD
            text(KtTokens.DYNAMIC_KEYWORD.value)
        }
        is KaErrorType -> {
            // error types should not be printed
        }
    }

    if (markedNullable) text("?")
}

context(_: KaSession)
private fun PresentationTreeBuilder.printNonErrorClassType(type: KaClassType, anotherType: KaClassType? = null) {
    val truncatedName = truncatedName(type)
    if (truncatedName.isNotEmpty()) {
        if (type.classId.isLocal) {
            printSymbolPsi(type.symbol, truncatedName)
        } else {
            printClassId(type.classId, truncatedName)
        }
    }

    val ownTypeArguments = type.typeArguments
    if (ownTypeArguments.isNotEmpty()) {
        text("<")

        val anotherOwnTypeArguments = anotherType?.typeArguments
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


context(_: KaSession)
private fun PresentationTreeBuilder.printProjection(projection: KaTypeProjection, optionalProjection: Boolean) {
    fun String.asOptional(optional: Boolean): String =
        if (optional) "($this)" else this

    when (projection) {
        is KaStarTypeProjection -> {
            text("*".asOptional(optionalProjection))
        }
        is KaTypeArgumentWithVariance -> {
            projection.variance.label.takeIf { it.isNotEmpty() }?.let {
                text("${it.asOptional(optionalProjection)} ")
            }
            printKtType(projection.type)
        }
    }
}


private fun PresentationTreeBuilder.printClassId(classId: ClassId, name: String) {
    if (classId.shortClassName.isSpecial) {
        text(name)
    } else {
        text(
            name,
            InlayActionData(
                StringInlayActionPayload(classId.asFqNameString()),
                KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME
            )
        )
    }
}

private fun PresentationTreeBuilder.printSymbolPsi(symbol: KaSymbol, name: String) {
    text(
        name,
        symbol.psi?.createSmartPointer()?.let {
            InlayActionData(
                PsiPointerInlayActionPayload(it),
                PsiPointerInlayActionNavigationHandler.HANDLER_ID
            )
        }
    )
}

private fun isMutabilityFlexibleType(lower: KaType, upper: KaType): Boolean {
    // see org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer.AS_SHORT.isMutabilityFlexibleType
    if (lower !is KaClassType || upper !is KaClassType) return false

    return (StandardClassIds.Collections.mutableCollectionToBaseCollection[lower.classId] == upper.classId)
}


private fun isNullabilityFlexibleType(lower: KaType, upper: KaType): Boolean {
    if (lower.nullability == KaTypeNullability.NON_NULLABLE && upper.nullability == KaTypeNullability.NULLABLE) {
        if (lower is KaTypeParameterType && upper is KaTypeParameterType && lower.symbol == upper.symbol) {
            return true
        }
        if (lower is KaClassType && upper is KaClassType && lower.classId == upper.classId) {
            return isSimilarTypes(lower, upper)
        }
    }
    return false
}

private fun isNonNullableFlexibleType(lower: KaType, upper: KaType): Boolean {
    if (lower is KaClassType && upper is KaClassType &&
        lower.classId == upper.classId &&
        lower.nullability == KaTypeNullability.NON_NULLABLE &&
        upper.nullability == lower.nullability &&
        isSimilarTypes(lower, upper)
    ) {
        val lowerTypeArguments = lower.typeArguments
        val upperTypeArguments = upper.typeArguments
        return lowerTypeArguments.isNotEmpty() && lowerTypeArguments.zip(upperTypeArguments)
            .any { (lowerTypeArg, upperTypeArg) ->
                lowerTypeArg != upperTypeArg
            }
    }
    return false
}

private fun isSimilarTypes(
    lower: KaClassType,
    upper: KaClassType
): Boolean = lower.typeArguments.zip(upper.typeArguments)
    .none { (lowerTypeArg, upperTypeArg) -> lowerTypeArg.type != upperTypeArg.type }

private fun truncatedName(classType: KaClassType): String {
    val names = classType.qualifiers
        .mapNotNull {
            val symbol = it.symbol
            symbol.takeUnless {
                (it as? KaNamedClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT &&
                        it.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
            }?.name ?: symbol.takeIf { (symbol as? KaClassSymbol)?.classKind == KaClassKind.ANONYMOUS_OBJECT }?.let {
                SpecialNames.ANONYMOUS
            }
        }

    names.joinToString(".", transform = Name::asString)
        .takeIf { names.size <= 1 || it.length < PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH }
        ?.let { return it }

    var lastJoinString = ""
    for (name in names.reversed()) {
        val nameAsString = name.asString()
        if (lastJoinString.length + nameAsString.length + 1 > PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH) {
            break
        }
        lastJoinString = if (lastJoinString.isEmpty()) nameAsString else "$nameAsString.$lastJoinString"
    }

    return Typography.ellipsis.toString() + lastJoinString
}