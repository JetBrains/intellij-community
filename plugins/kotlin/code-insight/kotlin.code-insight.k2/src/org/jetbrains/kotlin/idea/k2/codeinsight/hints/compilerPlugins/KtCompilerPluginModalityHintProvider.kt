// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.base.psi.predictImplicitModality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier

internal class KtCompilerPluginModalityHintProvider : AbstractKtCompilerPluginDeclarativeHintProvider() {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        if (element is KtDeclaration) {
            collectModality(element, sink)
        }
    }

    private fun collectModality(declaration: KtDeclaration, sink: InlayTreeSink) {
        if (!declaration.declarationCanBeModifiedByCompilerPlugins()) return
        if (!declaration.declarationCanHaveModality()) return

        if (declaration.modalityModifier() != null) {
            // compiler plugins cannot change modality when it's implicit
            return
        }
        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            // overridden modality is not modified
            return
        }

        val expectedModality = declaration.predictImplicitModality()
        val realModality = analyze(declaration) { declaration.symbol.modality }.asKeywordToken()
        if (expectedModality != realModality) {
            // that means that modality was altered by some compiler plugin
            sink.addPresentation(
                InlineInlayPosition(declaration.placeNewModalityModifierBeforeOffset(), relatedToPrevious = true),
                tooltip = KotlinBundle.message("hints.tooltip.compiler.plugins.modality", expectedModality.value, realModality.value),
                hintFormat = HintFormat.Companion.default
            ) {
                text(realModality.value)
            }
        }
    }

    private fun KtDeclaration.declarationCanHaveModality(): Boolean {
        when (this) {
            is KtConstructor<*> -> {
                return false
            }

            is KtPropertyAccessor -> {
                return false
            }

            is KtProperty -> {
                if (isLocal) return false
            }

            is KtNamedFunction -> {
                if (isLocal) return false
            }
        }
        return true
    }

    private fun KtDeclaration.placeNewModalityModifierBeforeOffset(): Int {
        val modifierList = modifierList

        if (modifierList != null) {
            modifierList.findFirstKeyword()?.let { return it.startOffset }
            declarationKeyword()?.let { return it.startOffset }
            return modifierList.startOffset
        }

        declarationKeyword()?.let { return it.startOffset }
        return startOffset
    }

    private fun KtModifierList.findFirstKeyword(): LeafPsiElement? {
        for (child in childrenOfType<LeafPsiElement>()) {
            val elementType = child.elementType
            if (elementType in KtTokens.KEYWORDS || elementType in KtTokens.SOFT_KEYWORDS) {
                return child
            }
        }

        return null
    }

    private fun KtDeclaration.declarationKeyword() = when (this) {
        is KtClassOrObject -> getDeclarationKeyword()
        is KtProperty -> getValOrVarKeyword()
        is KtNamedFunction -> funKeyword
        is KtTypeAlias -> getTypeAliasKeyword()
        else -> null
    }

    private fun KaSymbolModality.asKeywordToken() = when (this) {
        KaSymbolModality.FINAL -> KtTokens.FINAL_KEYWORD
        KaSymbolModality.SEALED -> KtTokens.SEALED_KEYWORD
        KaSymbolModality.OPEN -> KtTokens.OPEN_KEYWORD
        KaSymbolModality.ABSTRACT -> KtTokens.ABSTRACT_KEYWORD
    }
}