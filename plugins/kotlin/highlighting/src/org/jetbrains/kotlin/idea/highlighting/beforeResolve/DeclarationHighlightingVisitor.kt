// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForClass
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForKtParameterDeclaration
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesForKtPropertyDeclaration
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.highlighter.*
import org.jetbrains.kotlin.idea.highlighting.beforeResolve.AbstractBeforeResolveHiglightingVisitory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class DeclarationHighlightingVisitor(holder: AnnotationHolder) : AbstractBeforeResolveHiglightingVisitory(holder) {
    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        highlightNamedDeclaration(typeAlias, Colors.TYPE_ALIAS)
        super.visitTypeAlias(typeAlias)
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        highlightNamedDeclaration(declaration, Colors.OBJECT)
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
            highlightName(entry, Colors.LOCAL_VARIABLE)
            if (isVar) {
                highlightName(entry, Colors.MUTABLE_VARIABLE)
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
            highlightNamedDeclaration(declaration, Colors.MUTABLE_VARIABLE)
        }
    }
}

class DeclarationHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: AnnotationHolder): AbstractHighlightingVisitor =
        DeclarationHighlightingVisitor(holder)
}