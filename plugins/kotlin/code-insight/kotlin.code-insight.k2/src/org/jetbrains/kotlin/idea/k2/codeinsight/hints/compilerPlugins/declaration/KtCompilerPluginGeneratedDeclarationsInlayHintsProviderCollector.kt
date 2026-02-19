// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.collectInlaysWithErrorsLogging
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declarationCanBeModifiedByCompilerPlugins
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class KtCompilerPluginGeneratedDeclarationsInlayHintsProviderCollector(
    override val editor: Editor,
    private val settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings,
    override val project: Project,
) : FactoryInlayHintsCollector(editor), GeneratedCodeInlayFactory {

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element is KtClassOrObject) {
            collectInlaysWithErrorsLogging(element) {
                collect(element, sink)
            }
        }
        return true
    }

    private fun collect(ktClass: KtClassOrObject, sink: InlayHintsSink) {
        if (!ktClass.declarationCanBeModifiedByCompilerPlugins()) return

        buildCode {
            val inlay = analyze(ktClass) {
                CompilerPluginDeclarationInlayFactory.renderGeneratedMembersToInlay(ktClass, settings)
            }
            if (inlay == null) return

            val body = ktClass.body
            if (body != null) {
                collectWithForClassWithBody(body, inlay, ktClass, sink)
            } else {
                collectForClassWithoutBody(inlay, ktClass, sink)
            }
        }
    }

    /**
     * Show inlay with additional fake `{}` braces to make it look natural
     */
    context(_: CodeInlaySession)
    private fun collectForClassWithoutBody(
        rawInlay: InlayPresentation,
        ktClass: KtClassOrObject,
        sink: InlayHintsSink
    ) {
        val inlay = listOf(
            rawInlay.asGeneratedCodeBlock().indented(getKotlinIndentSize(), shiftLeftToInset = true),
            // to have a good formatting, the closing brace is shown inside the block inlay hint
            code("}").asSmallInlayAlignedToTextLine(),
        ).vertical().indentedAsElementInEditorAtOffset(
            ktClass.startOffset,
            extraIndent = 0,
            shiftLeftToInset = false
        )
        sink.addBlockElement(offset = ktClass.endOffset, relatesToPrecedingText = true, showAbove = false, priority = 0, inlay)
        // The opening brace is shows as a separate inline inlay hint
        sink.addInlineElement(ktClass.endOffset, relatesToPrecedingText = true, code("{").asSmallInlayAlignedToTextLine(), placeAtTheEndOfLine = true)
    }


    context(_: CodeInlaySession)
    private fun collectWithForClassWithBody(
        body: KtClassBody,
        rawInlay: InlayPresentation,
        ktClass: KtClassOrObject,
        sink: InlayHintsSink
    ) {
        // do not show members on an invalid body
        val lBrace = body.lBrace ?: return
        val rBrace = body.rBrace ?: return

        val inlay = rawInlay.asGeneratedCodeBlock().indentedAsElementInEditorAtOffset(
            ktClass.startOffset,
            extraIndent = getKotlinIndentSize(),
            shiftLeftToInset = true,
        )

        if (lBrace.isOnTheSameLineWith(rBrace)) {
            addSmallInlineInlayWhenBracesAreOnTheSameLine(sink, rBrace)
        } else {
            sink.addBlockElement(offset = rBrace.endOffset, relatesToPrecedingText = true, showAbove = true, priority = 0, inlay)
        }
    }

    context(_: CodeInlaySession)
    private fun addSmallInlineInlayWhenBracesAreOnTheSameLine(sink: InlayHintsSink, rBrace: PsiElement) {
        // inside a body of type `class X() {}` where braces are in the same line
        // no place to put full bodies, so we are placing only hints
        val inlay = code("...")
            .withDefaultInlayBackground()
            .withTooltip(KotlinBundle.message("hints.tooltip.compiler.plugins.declarations.expand"))
        sink.addInlineElement(offset = rBrace.startOffset, relatesToPrecedingText = false, inlay, placeAtTheEndOfLine = false)
    }

    private fun PsiElement.isOnTheSameLineWith(other: PsiElement): Boolean {
        val document = editor.document
        return document.getLineNumber(startOffset) == document.getLineNumber(other.endOffset)
    }

    private fun getKotlinIndentSize(): Int {
        return CodeStyle.getSettings(editor).getIndentSize(KotlinFileType.INSTANCE)
    }
}