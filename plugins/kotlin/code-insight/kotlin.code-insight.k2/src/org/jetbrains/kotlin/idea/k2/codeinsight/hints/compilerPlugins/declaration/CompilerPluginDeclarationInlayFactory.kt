// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.psi.KtClassOrObject

@OptIn(KaExperimentalApi::class)
internal object CompilerPluginDeclarationInlayFactory {

    context(_: KaSession, _: CodeInlaySession, _: GeneratedCodeInlayFactory)
    fun renderGeneratedMembersToInlay(
        ktClassOrObject: KtClassOrObject,
        settings: KtCompilerPluginGeneratedDeclarationsInlayHintsProviderSettings
    ): InlayPresentation? {
        val symbol = ktClassOrObject.classSymbol ?: return null
        val members = CompilerGeneratedMembersCollector.collect(symbol, settings)
        if (members.isEmpty()) return null

        val kotlinCode = renderToString(members)
        return renderGeneratedMembersToInlay(kotlinCode, ktClassOrObject)
    }

    context(_: KaSession, factory: GeneratedCodeInlayFactory)
    private fun renderToString(members: List<CompilerGeneratedMembersCollector.CompileGeneratedMember>): String {
        fun PrettyPrinter.renderRecursively(generatedMember: CompilerGeneratedMembersCollector.CompileGeneratedMember) {
            append(generatedMember.member.render(KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES))
            if (generatedMember.subMembers.isNotEmpty()) {
                append(" ")
                withIndentInBraces {
                    printCollection(generatedMember.subMembers, separator = "\n") { renderRecursively(it) }
                }
            }
        }
        return PrettyPrinter(indentSize = EditorUtil.getPlainSpaceWidth(factory.editor)).apply {
            printCollection(members, separator = "\n") { renderRecursively(it) }
        }.toString()
    }


    context(_: KaSession, _: CodeInlaySession, factory: GeneratedCodeInlayFactory)
    private fun renderGeneratedMembersToInlay(kotlinCode: String, context: PsiElement): InlayPresentation {
        val lines = CompilerPluginDeclarationHighlighter.highlightCode(kotlinCode, context, factory.editor.colorsScheme)
        return lines.map { it.render() }.vertical()
    }

    context(_: CodeInlaySession, _: GeneratedCodeInlayFactory)
    private fun CompilerPluginDeclarationHighlighter.CodeLine.render(): InlayPresentation {
        return tokens.map { it.render() }.horizontal()
    }

    context(_: CodeInlaySession, factory: GeneratedCodeInlayFactory)
    private fun CompilerPluginDeclarationHighlighter.ColoredToken.render(): InlayPresentation {
        var presentation = code(text, attributes)
        for (tag in tags) {
            when (tag) {
                is CompilerPluginDeclarationHighlighter.TokenTag.Target -> {
                    val hoverListener = InlayDocumentationHoverHandler.createHoverListener(
                        targetPointer = tag.targetPointer,
                        editor = factory.editor,
                        project = factory.project
                    )

                    presentation = factory.factory.mouseHandling(presentation, clickListener = null, hoverListener = hoverListener)
                }
            }
        }
        return presentation
    }
}