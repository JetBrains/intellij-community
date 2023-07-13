// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForClass
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForKtParameterDeclaration
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForKtPropertyDeclaration
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class DeclarationHighlightingVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder) {
    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        highlightNamedDeclaration(typeAlias, KotlinHighlightInfoTypeSemanticNames.TYPE_ALIAS)
        super.visitTypeAlias(typeAlias)
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        highlightNamedDeclaration(declaration, KotlinHighlightInfoTypeSemanticNames.OBJECT)
        super.visitObjectDeclaration(declaration)
    }

    override fun visitClass(klass: KtClass) {
        highlightNamedDeclaration(klass, textAttributesForClass(klass))
        super.visitClass(klass)
    }

    override fun visitProperty(property: KtProperty) {
        textAttributesForKtPropertyDeclaration(property)?.let { attributes ->
            highlightNamedDeclaration(property, attributes)
        }
        highlightMutability(property)
        super.visitProperty(property)
    }

    override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
        val isVar = destructuringDeclaration.isVar
        for (entry in destructuringDeclaration.entries) {
            highlightName(entry, KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE)
            if (isVar) {
                highlightName(entry, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)
            }
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        highlightNamedDeclaration(parameter, textAttributesForKtParameterDeclaration(parameter))
        highlightMutability(parameter)
        super.visitParameter(parameter)
    }


    private fun <D> highlightMutability(declaration: D) where D : KtValVarKeywordOwner, D : KtNamedDeclaration {
        if (PsiUtilCore.getElementType(declaration.valOrVarKeyword) == KtTokens.VAR_KEYWORD) {
            highlightNamedDeclaration(declaration, KotlinHighlightInfoTypeSemanticNames.MUTABLE_VARIABLE)
        }
    }
}

class DeclarationHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor =
        DeclarationHighlightingVisitor(holder)
}