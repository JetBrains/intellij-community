// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

private class Visitor(var range: TextRange) : KtTreeVisitorVoid() {
    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
        fun PsiElement.containsToken(type: IElementType) = allChildren.any { it.node.elementType == type }

        if (!range.contains(declaration.textRange)) return

        val classBody = declaration.parent as? KtClassBody ?: return
        val klass = classBody.parent as? KtClass ?: return
        if (!klass.isEnum()) return

        var delta = 0

        val psiFactory = KtPsiFactory(klass.project)
        if (declaration is KtEnumEntry) {
            val comma = psiFactory.createComma()

            val nextEntry = declaration.nextSiblingOfSameType()
            if (nextEntry != null && !declaration.containsToken(KtTokens.COMMA)) {
                declaration.add(comma)
                delta += comma.textLength
            }
        } else {
            val lastEntry = klass.declarations.lastIsInstanceOrNull<KtEnumEntry>()
            if (lastEntry != null &&
                (lastEntry.containsToken(KtTokens.SEMICOLON) || lastEntry.nextSibling?.node?.elementType == KtTokens.SEMICOLON)
            ) return
            if (lastEntry == null && classBody.containsToken(KtTokens.SEMICOLON)) return

            val semicolon = psiFactory.createSemicolon()
            delta += if (lastEntry != null) {
                classBody.addAfter(semicolon, lastEntry)
                semicolon.textLength
            } else {
                val newLine = psiFactory.createNewLine()
                classBody.addAfter(semicolon, classBody.lBrace)
                classBody.addAfter(psiFactory.createNewLine(), classBody.lBrace)
                semicolon.textLength + newLine.textLength
            }
        }

        range = TextRange(range.startOffset, range.endOffset + delta)
    }
}

class KotlinPreFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        val psi = element.psi ?: return range
        if (!psi.isValid) return range
        if (psi.containingFile !is KtFile) return range
        return Visitor(range).apply { psi.accept(this) }.range
    }
}
