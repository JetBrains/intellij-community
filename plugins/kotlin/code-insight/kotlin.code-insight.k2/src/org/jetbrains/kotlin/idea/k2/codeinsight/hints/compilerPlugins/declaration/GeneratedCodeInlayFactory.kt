// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.InlayHintsUtils.getDefaultInlayHintsProviderPopupActions
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints


internal interface GeneratedCodeInlayFactory {
    val factory: PresentationFactory
    val editor: Editor
    val project: Project
}

context(_: GeneratedCodeInlayFactory)
internal fun InlayPresentation.asGeneratedCodeBlock(): InlayPresentation {
    return this
        // show half of the top/bottom indents under the background, and the other halves outside
        .inset(left = INSET_SIZE, right = INSET_SIZE, top = INSET_SIZE / 2, down = INSET_SIZE / 2)
        .withDefaultInlayBackground()
        // outside halves
        .inset(left = 0, right = 0, top = INSET_SIZE / 2, down = INSET_SIZE / 2)
        .withContextMenu()
        .withTooltip(KotlinBundle.message("hints.tooltip.compiler.plugins.declarations"))
}

/**
 * Wraps the inlay into the background, adds padding, and ensures that the inlay stays in the text line like regular code.
 */
context(_: GeneratedCodeInlayFactory)
internal fun InlayPresentation.asSmallInlayAlignedToTextLine(): InlayPresentation {
    return this
        // Show top and bottom insets inside the background. Negative insets will be added later outside the background to ensure it aligns with the text line
        .inset(left = SMALL_INSET_SIZE, right = SMALL_INSET_SIZE, top = SMALL_INSET_SIZE, down = SMALL_INSET_SIZE)
        .withDefaultInlayBackground()
        // the negating insets to ensure alignment
        .inset(left = 0, right = 0, top = -SMALL_INSET_SIZE, down = -SMALL_INSET_SIZE)
}

context(_: GeneratedCodeInlayFactory)
internal fun InlayPresentation.inset(left: Int, right: Int, top: Int, down: Int): InlayPresentation =
    InsetPresentation(this, left = left, right = right, top = top, down = down)

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withDefaultInlayBackground(): InlayPresentation {
    val bgColor =
        factory.editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLAY_DEFAULT).backgroundColor
            ?: factory.editor.colorsScheme.defaultBackground
    return RoundWithBackgroundPresentation(this, arcWidth = BG_ARC_DIAMETER, arcHeight = BG_ARC_DIAMETER, color = bgColor)
}

context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.withTooltip(@NlsContexts.HintText tooltip: String): InlayPresentation {
    return factory.factory.withTooltip(tooltip, this)
}

context(_: GeneratedCodeInlayFactory)
internal fun List<InlayPresentation>.vertical(): InlayPresentation {
    if (size == 1) return single()
    return VerticalListInlayPresentation(this)
}

context(_: GeneratedCodeInlayFactory)
internal fun List<InlayPresentation>.horizontal(): InlayPresentation {
    return when (size) {
        0 -> SpacePresentation(0, 0)
        1 -> single()
        else -> SequencePresentation(this)
    }
}

/**
 * @see indented
 */
context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.indentedAsElementInEditorAtOffset(
    offset: Int,
    extraIndent: Int = 0,
    shiftLeftToInset: Boolean
): InlayPresentation {
    val document = factory.editor.getDocument()
    val startOffset = document.getLineStartOffset(document.getLineNumber(offset))

    val column = offset - startOffset + extraIndent
    return indented(column, shiftLeftToInset)
}

/**
 * Adjusts the given inlay presentation by adding an indent to its left side.
 * Added indent size is equal to [columns] number of spaces.
 *
 * @param columns The number of spaces to indent by.
 * @param shiftLeftToInset if set to true, indent will be shifted a little left to the offset of inlay inset,
 * aligning its text start with real declarations text
 */
context(factory: GeneratedCodeInlayFactory)
internal fun InlayPresentation.indented(columns: Int, shiftLeftToInset: Boolean): InlayPresentation {
    val spaceWidth = EditorUtil.getPlainSpaceWidth(factory.editor)
    var left = columns * spaceWidth
    if (shiftLeftToInset) left -= INSET_SIZE
    if (left < 0) left = 0
    return inset(left = left, right = 0, top = 0, down = 0)
}

context(factory: GeneratedCodeInlayFactory)
private fun InlayPresentation.withContextMenu(): InlayPresentation {
    return MenuOnClickPresentation(this, factory.project) {
        getDefaultInlayHintsProviderPopupActions(
            KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings.KEY,
            KotlinBundle.messagePointer("hints.settings.compiler.plugins.declarations")
        )
    }
}


private const val INSET_SIZE = 8
private const val SMALL_INSET_SIZE = 1
private const val BG_ARC_DIAMETER = 8

internal inline fun <R> GeneratedCodeInlayFactory.buildCode(build: CodeInlaySession.() -> R): R {
    return CodeInlaySession(editor).let(build)
}

internal class CodeInlaySession(val editor: Editor) {
    val textMetrics = InlayTextMetrics.create(
        editor,
        editor.colorsScheme.editorFontSize2D,
        editor.colorsScheme.getAttributes(HighlighterColors.TEXT).fontType,
        getFontRenderContext(editor.component),
        isUseEditorFontInInlays = true,
    )
}

context(session: CodeInlaySession)
internal fun code(text: String, attributes: TextAttributes): InlayPresentation {
    return CodeInlay(text, attributes, session.editor, session.textMetrics)
}

context(session: CodeInlaySession)
internal fun code(text: String): InlayPresentation {
    return code(text, session.editor.colorsScheme.getAttributes(HighlighterColors.TEXT))
}


private class CodeInlay(
    private val text: String,
    private val ownAttributes: TextAttributes,
    private val editor: Editor,
    private val textMetrics: InlayTextMetrics,
) : BasePresentation() {
    override val width: Int
        get() = textMetrics.getStringWidth(text)

    override val height: Int
        get() = editor.lineHeight

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        val attributesToUse = TextAttributes.merge(ownAttributes, attributes)
        val savedHint = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        try {
            val foreground = attributesToUse.foregroundColor ?: error("ForegroundColor cannot be null")
            val metric = textMetrics
            val font = metric.font
            g.font = font
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(/* inEditor = */ true))
            g.color = foreground.withAlphaFactor()
            // a sum of gaps between which the text is situated on the line
            val fontGap = editor.lineHeight - textMetrics.fontBaseline
            val yCoordinate = editor.lineHeight - fontGap / 2
            g.drawString(text, /* x = */ 0, /* y = */ yCoordinate)
        } finally {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
        }
    }

    private fun Color.withAlphaFactor(): Color {
        // we are materializing the color here, so we can use `java.awt.Color`
        @Suppress("UseJBColor")
        return Color(red, green, blue, (alpha * ALPHA_FACTOR).toInt())
    }

    override fun toString(): String = text

    companion object {
        const val ALPHA_FACTOR = .95
    }
}