// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlighter
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

internal object CompilerPluginDeclarationHighlighter {

    data class CodeLine(val tokens: List<ColoredToken>)

    data class ColoredToken(val text: String, val attributes: TextAttributes)

    fun highlightKotlinSyntax(kotlinCode: String, project: Project, colorsScheme: EditorColorsScheme): List<CodeLine> {
        val tokens = getTokens(kotlinCode, project, colorsScheme)
        return splitTokensToLines(tokens)
    }

    /**
     * Some tokens may contain line breaks.
     * The function splits a list of colored tokens into separate lines based on line break characters.
     */
    private fun splitTokensToLines(tokens: List<ColoredToken>): List<CodeLine> {
        val result = mutableListOf<CodeLine>()
        val currentLine = mutableListOf<ColoredToken>()
        for (token in tokens) {
            val attributes = token.attributes
            val split = token.text.split("\n")
            when (split.size) {
                0 -> continue
                1 -> {
                    currentLine += token
                }

                else -> {
                    currentLine += ColoredToken(split.first(), attributes)
                    result += CodeLine(currentLine.toList())
                    currentLine.clear()

                    // all lines except the first and last lines
                    split.subList(1, split.size - 1).mapTo(result) { CodeLine(listOf(ColoredToken(it, attributes))) }

                    currentLine += ColoredToken(split.last(), attributes)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            result += CodeLine(currentLine.toList())
        }

        return result
    }

    private fun getTokens(kotlinCode: String, project: Project, colorsScheme: EditorColorsScheme): List<ColoredToken> {
        val ktFile = KtPsiFactory(project).createFile("declaration.kt", kotlinCode)
        CodeStyleManager.getInstance(ktFile.project).reformat(ktFile)
        return ktFile.getTokens(colorsScheme)
    }

    private fun KtFile.getTokens(colorsScheme: EditorColorsScheme): List<ColoredToken> {
        val infos = collectHighlightingInfosByBeforeResolveHighlighters()

        fun MutableList<TextAttributes>.addAttributesFromInfos(element: LeafPsiElement) {
            for (info in infos) {
                if (info.range().contains(element.textRange)) {
                    addIfNotNull(info.getTextAttributes(element, colorsScheme))
                }
            }
        }

        val result = mutableListOf<ColoredToken>()

        // O(N^2) where N is a number of child elements in the tree.
        // Considering that generated declarations are not usually large, that should be OK
        forEachDescendantOfType<LeafPsiElement> { element ->
            val attributes = buildList {
                add(colorsScheme.getAttributes(HighlighterColors.TEXT))
                addIfNotNull(getLexerAttributes(element, colorsScheme))
                addAttributesFromInfos(element)
            }.reduce(TextAttributes::merge)

            setForegroundColorIfNotSet(attributes, colorsScheme)
            result += ColoredToken(element.text, attributes)
        }

        return result
    }

    private fun HighlightInfo.range(): TextRange = TextRange(startOffset, endOffset)

    private fun setForegroundColorIfNotSet(
        attributes: TextAttributes,
        colorsScheme: EditorColorsScheme
    ) {
        if (attributes.foregroundColor == null) {
            attributes.foregroundColor = colorsScheme.defaultForeground
        }
    }

    private fun getLexerAttributes(
        element: LeafPsiElement,
        colorsScheme: EditorColorsScheme
    ): TextAttributes? {
        val tokenType = element.node?.elementType ?: return null
        val keys = KotlinHighlighter().getTokenHighlights(tokenType)
        if (keys.isEmpty()) return null
        return LayeredTextAttributes.create(colorsScheme, keys)
    }

    private fun KtFile.collectHighlightingInfosByBeforeResolveHighlighters(): List<HighlightInfo> {
        val holder = HighlightInfoHolder(this)
        val beforeResolveVisitors = BeforeResolveHighlightingExtension.EP_NAME.extensionList.map { it.createVisitor(holder) }
        forEachDescendantOfType<PsiElement> { element ->
            for (visitor in beforeResolveVisitors) {
                element.accept(visitor)
            }
        }
        return (0 until holder.size()).map { holder[it] }
    }
}