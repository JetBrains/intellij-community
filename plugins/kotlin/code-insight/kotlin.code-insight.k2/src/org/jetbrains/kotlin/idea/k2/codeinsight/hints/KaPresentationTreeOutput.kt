// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.codeInsight.hints.KotlinFqnDeclarativeInlayActionHandler

/**
 * Writes rendered fragments into a [PresentationTreeBuilder], turning each [KaTextAttribute.Symbol] into the inlay
 * action data that navigates to the referenced declaration.
 *
 * Fragments are accumulated and emitted as few [PresentationTreeBuilder.text] nodes as possible, because the tree
 * holds at most [PresentationTreeBuilderImpl.MAX_NODE_COUNT] nodes and is truncated with an ellipsis beyond that. A
 * run is broken only where it has to be: a node carries a single action, and its text is truncated past
 * [PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH].
 *
 * [flush] must be called once rendering is finished, to emit the last accumulated run.
 */
internal class KaPresentationTreeOutput(private val builder: PresentationTreeBuilder) : KaRenderingOutput {
    private val pendingText = StringBuilder()
    private var pendingActionData: InlayActionData? = null
    private var lastAppendedChar: Char? = null

    override fun append(text: String, attributes: Set<KaTextAttribute>): KaRenderingOutput {
        // `PresentationTreeBuilder.text` rejects empty text.
        if (text.isEmpty()) return this

        val actionData = attributes.filterIsInstance<KaTextAttribute.Symbol>().firstOrNull()?.symbol?.inlayActionData()
        if (actionData != pendingActionData ||
            pendingText.length + text.length > PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH
        ) {
            flush()
            pendingActionData = actionData
        }

        pendingText.append(text)
        lastAppendedChar = text.last()
        return this
    }

    override fun space(): KaRenderingOutput {
        if (lastAppendedChar?.isWhitespace() == true) {
            return this
        }

        return append(" ", KaTextAttribute.Whitespace)
    }

    /**
     * A no-op: the presentation tree is a single line, so there is no line start for an indentation to apply to.
     * Also, type rendering never indents; only declaration pieces such as a class body do.
     */
    override fun pushIndent(): KaRenderingOutput = this

    override fun popIndent(): KaRenderingOutput = this

    /** Degrades to a single separating space, since the presentation tree cannot hold a line break. */
    override fun newLine(): KaRenderingOutput {
        if (lastAppendedChar == null || lastAppendedChar == ' ') return this
        return space()
    }

    /** Emits the accumulated run, if any. */
    fun flush() {
        if (pendingText.isEmpty()) return

        builder.text(pendingText.toString(), pendingActionData)
        pendingText.setLength(0)
        pendingActionData = null
    }

    private fun KaSymbol.inlayActionData(): InlayActionData? {
        val classId = (this as? KaClassLikeSymbol)?.classId
        if (classId != null) {
            if (classId.shortClassName.isSpecial) return null
            return InlayActionData(
                StringInlayActionPayload(classId.asFqNameString()),
                KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME,
            )
        }

        return psi?.createSmartPointer()?.let {
            InlayActionData(PsiPointerInlayActionPayload(it), PsiPointerInlayActionNavigationHandler.HANDLER_ID)
        }
    }
}