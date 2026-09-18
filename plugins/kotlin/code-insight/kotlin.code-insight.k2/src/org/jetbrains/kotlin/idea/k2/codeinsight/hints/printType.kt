// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute.Symbol
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaResolvedClassTypeQualifier
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.analysis.api.types.isNullable
import org.jetbrains.kotlin.idea.base.analysis.api.utils.approximateAnonymousObjectToSupertypeOrSelf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isAnnotatedWithTypeUseOnly
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds

context(_: KaSession)
internal fun PresentationTreeBuilder.printKtType(type: KaType) {
    val output = KaPresentationTreeOutput(this)
    HINT_TYPE_RENDERER.render(type, output)
    output.flush()
}

private val HINT_TYPE_RENDERER: KaRenderer = KaRenderer.default.copy {
    set(KaRenderingOption.LinkSymbols, true)

    // Render the type exactly as it was inferred.
    set(KaRenderingOption.TypeTransformation) { it }

    set(KaRenderingOption.Annotations) { annotated ->
        annotated.annotations.filter { annotation ->
            // Annotations declared in the `kotlin` package describe syntax the type is already rendered with — `@ExtensionFunctionType`
            // the receiver, `@ParameterName` the parameter name — so rendering them as well would duplicate it.
            annotation.isAnnotatedWithTypeUseOnly() && annotation.classId?.packageFqName != StandardClassIds.BASE_KOTLIN_PACKAGE
        }
    }

    // A hint has no room for annotation arguments, so an annotation is rendered by its name alone.
    pushEmpty(KaPiece.AnnotationValues)

    push(HintReflectionFunctionTypeRenderer)
    push(HintFlexibleTypeRenderer)
    push(KaPiece.ClassTypeName) { renderClassTypeName(it) }
    push(KaPiece.CapturedType) {
        output.append("*", KaTextAttribute.Punctuation)
        render(it, KaPiece.TypeNullability)
    }
    // Error types are not rendered at all.
    pushEmpty(KaPiece.ErrorType)
}

/**
 * Renders a reflection function type, such as `KFunction1<Int, String>`, with function type syntax: `(Int) -> String`.
 *
 * The built-in renderer sends such a type to [KaPiece.ClassType], because function type syntax cannot express that the type is a reflection
 * one. A hint deliberately drops that distinction, as the shorter form is what the callable reference reads like at the use site.
 */
private object HintReflectionFunctionTypeRenderer : KaPieceRenderer<KaClassType>(KaPiece.ClassType) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaClassType, next: () -> Unit): Boolean {
        if (value !is KaFunctionType) return false

        render(value, KaPiece.FunctionType)
        return true
    }
}

/**
 * Merges the bounds of a flexible type which differ only in their type arguments into a single rendering which parentheses the arguments
 * that differ, so `Array<String!>..Array<out String!>` is rendered as `Array<(out) String!>`.
 *
 * This is the one compact form of a flexible type the built-in renderer has no notion of; it renders such a type as a range. Every other
 * form is left to it.
 */
private object HintFlexibleTypeRenderer : KaPieceRenderer<KaFlexibleType>(KaPiece.FlexibleType) {
    context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
    override fun render(value: KaFlexibleType, next: () -> Unit): Boolean {
        val lower = value.lowerBound
        val upper = value.upperBound
        if (!isNonNullableFlexibleType(lower, upper)) return false

        // The upper bound is the primary one, so that the type arguments which carry the variance the other bound lacks are the ones
        // rendered, with that variance parenthesized.
        renderClassTypeName(upper as KaClassType, lower as KaClassType)
        return true
    }
}

/**
 * Renders the name of [type], collapsing qualifier segments that carry no type arguments into a single truncated name.
 *
 * When [anotherType] is given, the two types are rendered as one: a type argument that differs between them is
 * parenthesized. This is why the rendering lives in a function rather than in a [KaPieceRenderer], which only ever
 * receives one value.
 *
 * The nullability marker is not rendered here: it belongs to [KaPiece.ClassType], which appends it around this piece.
 */
context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
private fun renderClassTypeName(type: KaClassType, anotherType: KaClassType? = null) {
    val classType = type.approximateAnonymousObjectToSupertypeOrSelf() as KaClassType
    val qualifiers = classType.qualifiers
    val anotherQualifiers = anotherType?.qualifiers

    var lastUsedIndex = -1
    var nested = false
    for ((index, qualifier) in qualifiers.withIndex()) {
        if (qualifier.typeArguments.isEmpty() && index != qualifiers.lastIndex) {
            // skip ahead to type arguments
            continue
        }

        val truncatedName = truncatedName(qualifiers.subList(lastUsedIndex + 1, index + 1))
        if (truncatedName.isEmpty()) continue

        if (nested) output.append(".", KaTextAttribute.Punctuation)
        output.append(truncatedName, setOf(KaTextAttribute.Identifier, Symbol(qualifier.symbol)))

        val ownTypeArguments = qualifier.typeArguments.ifEmpty { break } // no type arguments, so we've reached the end of qualifiers

        output.group(KaPiece.TypeProjection) {
            output.append("<", KaTextAttribute.Punctuation)

            val anotherOwnTypeArguments = anotherQualifiers?.getOrNull(index)?.typeArguments
            val iterator = ownTypeArguments.iterator()
            val anotherIterator = anotherOwnTypeArguments?.iterator()
            while (iterator.hasNext()) {
                val projection = iterator.next()
                val anotherProjection = anotherIterator?.takeIf { it.hasNext() }?.next()

                renderProjection(projection, anotherProjection != null && projection != anotherProjection)

                if (iterator.hasNext()) output.append(", ", KaTextAttribute.Punctuation)
            }

            output.append(">", KaTextAttribute.Punctuation)
        }
        lastUsedIndex = index
        nested = true
    }
}

/**
 * Renders [projection], parenthesizing its variance — or the whole projection, if it is a star — when [optionalProjection] is set, which
 * marks a type argument that differs between the two bounds of a merged flexible type.
 */
context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
private fun renderProjection(projection: KaTypeProjection, optionalProjection: Boolean) {
    if (!optionalProjection) {
        render(projection, KaPiece.TypeProjection)
        return
    }

    when (projection) {
        is KaStarTypeProjection -> {
            output.append("(*)", KaTextAttribute.Punctuation)
        }
        is KaTypeArgumentWithVariance -> {
            projection.variance.label.takeIf { it.isNotEmpty() }?.let {
                output.append("($it) ", KaTextAttribute.Keyword)
            }
            render(projection.type, KaPiece.Type)
        }
    }
}

context(_: KaSession)
private fun isNonNullableFlexibleType(lower: KaType, upper: KaType): Boolean {
    if (lower is KaClassType && upper is KaClassType &&
        lower.classId == upper.classId &&
        !lower.isNullable &&
        upper.isNullable == lower.isNullable &&
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

private fun truncatedName(qualifiers: List<KaResolvedClassTypeQualifier>): String {
    val lastQualifier = qualifiers.lastOrNull()
    val names = qualifiers
        .mapNotNull { qualifier ->
            val symbol = qualifier.symbol.takeUnless { classifierSymbol ->
                (classifierSymbol as? KaNamedClassSymbol)?.classKind == KaClassKind.COMPANION_OBJECT &&
                        classifierSymbol.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT && qualifier != lastQualifier
            }
            symbol?.name
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