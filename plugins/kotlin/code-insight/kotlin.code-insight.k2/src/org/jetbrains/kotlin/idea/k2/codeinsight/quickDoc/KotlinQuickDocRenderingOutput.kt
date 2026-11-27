// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.highlighting.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.idea.highlighting.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
import org.jetbrains.kotlin.name.FqName

internal enum class KotlinQuickDocTextAttribute : KaTextAttribute {
    CODE_SNIPPET, INFORMATION, PARAMETER_LABEL, ERROR
}

/**
 * Converts raw Kotlin text to HTML during one rendering call in [session].
 * References to named classifiers link to their declarations. Declaration names and local classes have no links.
 * Only the completed [String] can leave the analysis session.
 */
internal class KotlinQuickDocRenderingOutput(
    private val session: KaSession,
    private val highlighting: KotlinIdeDescriptorRendererHighlightingManager<Attributes>
) : KaRenderingOutput {

    private val html = StringBuilder()
    private val pieces = ArrayDeque<KaPiece<*>>()
    private var lastCharacter: Char? = null
    private var pendingSpace = false
    private var indent = 0

    @KaSpiExtensionPoint
    override fun enter(piece: KaPiece<*>) {
        pieces.addLast(piece)
    }

    @KaSpiExtensionPoint
    override fun leave(piece: KaPiece<*>) {
        check(pieces.removeLast() == piece)
    }

    override fun append(text: String, attributes: Set<KaTextAttribute>): KaRenderingOutput {
        if (text.isEmpty()) return this
        if (lastCharacter == null || lastCharacter == '\n') {
            repeat(indent) { html.append("    ") }
        } else if (pendingSpace) {
            html.append(' ')
        }
        pendingSpace = false
        lastCharacter = text.last()

        if (KotlinQuickDocTextAttribute.CODE_SNIPPET in attributes ||
            KaTextAttribute.StringLiteral in attributes || KaTextAttribute.NumberLiteral in attributes
        ) {
            if (highlighting === KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING) {
                html.append(HtmlEscapers.htmlEscaper().escape(text))
            } else {
                with(highlighting) { html.appendCodeSnippetHighlightedByLexer(text) }
            }
            return this
        }

        val symbol = attributes.firstNotNullOfOrNull { (it as? KaTextAttribute.Symbol)?.symbol }
        val style = with(highlighting) {
            when {
                KotlinQuickDocTextAttribute.ERROR in attributes -> asError
                KotlinQuickDocTextAttribute.INFORMATION in attributes || KaTextAttribute.Comment in attributes -> asInfo
                KotlinQuickDocTextAttribute.PARAMETER_LABEL in attributes -> asParameter
                KaTextAttribute.Keyword in attributes -> asKeyword
                KaTextAttribute.Punctuation in attributes -> punctuationStyle(text)
                KaTextAttribute.Identifier in attributes -> identifierStyle(symbol)
                else -> null
            }
        }
        val escaped = HtmlEscapers.htmlEscaper().escape(text)
        val target = if (KaPiece.SymbolName in pieces) null else symbol?.linkTarget()
        val label = if (target == null) escaped else buildString {
            DocumentationManagerUtil.createHyperlink(this, HtmlEscapers.htmlEscaper().escape(target.asString()), escaped, true)
        }
        if (style == null) html.append(label) else with(highlighting) { html.appendHighlighted(label, style) }
        return this
    }

    private fun punctuationStyle(text: String): Attributes? = with(highlighting) {
        when (text) {
            "." -> asDot
            "," -> asComma
            ":" -> asColon
            "::" -> asDoubleColon
            "->" -> asArrow
            "(", ")", "()" -> asParentheses
            "[", "]", "[]" -> asBrackets
            "{", "}", "{}" -> asBraces
            "?" -> asNullityMarker
            "!" -> asNonNullAssertion
            "@" -> asAnnotationName
            "<", ">", "=", "&", "*", ".." -> asOperationSign
            else -> null
        }
    }

    private fun identifierStyle(symbol: KaSymbol?): Attributes? = with(highlighting) {
        when (pieces.lastOrNull()) {
            KaPiece.ErrorType -> return asError
            KaPiece.FunctionTypeParameter, KaPiece.AnnotationValues -> return asParameter
            KaPiece.AnnotationValue -> return asClassName
            KaPiece.ClassName -> if (pieces.getOrNull(pieces.lastIndex - 1) == KaPiece.Annotation) return asAnnotationName
            else -> Unit
        }
        when (symbol) {
            is KaClassSymbol -> when (symbol.classKind) {
                KaClassKind.CLASS, KaClassKind.ENUM_CLASS, KaClassKind.INTERFACE -> asClassName
                KaClassKind.ANNOTATION_CLASS -> asAnnotationName
                KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.ANONYMOUS_OBJECT -> asObjectName
            }
            is KaValueParameterSymbol -> if (context(session) { symbol.primaryConstructorProperty } == null) asParameter else asInstanceProperty
            is KaParameterSymbol -> asParameter
            is KaPackageSymbol -> asPackageName
            is KaTypeParameterSymbol -> asTypeParameterName
            is KaTypeAliasSymbol -> asTypeAlias
            is KaPropertySymbol -> asInstanceProperty
            is KaLocalVariableSymbol -> asLocalVarOrVal
            is KaEnumEntrySymbol, is KaFunctionSymbol -> asFunDeclaration
            else -> null
        }
    }

    private fun KaSymbol.linkTarget(): FqName? = context(session) {
        when (this) {
            is KaClassLikeSymbol -> if (name?.isSpecial == false) classId?.asSingleFqName() else null
            is KaTypeParameterSymbol -> if (name.isSpecial) null else containingDeclaration?.getFqNameIfPackageOrNonLocal()?.child(name)
                ?: FqName.topLevel(name)
            else -> null
        }
    }

    override fun space(): KaRenderingOutput {
        pendingSpace = lastCharacter?.isWhitespace() == false
        return this
    }

    override fun pushIndent(): KaRenderingOutput {
        indent++
        return this
    }

    override fun popIndent(): KaRenderingOutput {
        check(indent > 0)
        indent--
        return this
    }

    override fun newLine(): KaRenderingOutput {
        html.append('\n')
        lastCharacter = '\n'
        pendingSpace = false
        return this
    }

    override fun toString(): String = html.toString()
}
