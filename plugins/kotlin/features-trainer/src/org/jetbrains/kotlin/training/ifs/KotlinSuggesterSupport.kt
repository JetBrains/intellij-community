// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ifs

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.psi.*
import training.featuresSuggester.SuggesterSupport
import training.featuresSuggester.getParentByPredicate
import training.featuresSuggester.getParentOfType

class KotlinSuggesterSupport : SuggesterSupport {
    override fun isLoadedSourceFile(file: PsiFile): Boolean {
        return file is KtFile && !file.isCompiled && file.isContentsLoaded
    }

    override fun isIfStatement(element: PsiElement): Boolean {
        return element is KtIfExpression
    }

    override fun isForStatement(element: PsiElement): Boolean {
        return element is KtForExpression
    }

    override fun isWhileStatement(element: PsiElement): Boolean {
        return element is KtWhileExpression
    }

    override fun isCodeBlock(element: PsiElement): Boolean {
        return element is KtBlockExpression
    }

    override fun getCodeBlock(element: PsiElement): PsiElement? {
        return element.descendantsOfType<KtBlockExpression>().firstOrNull()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<KtBlockExpression>()
    }

    override fun getParentStatementOfBlock(element: PsiElement): PsiElement? {
        return element.parent?.parent
    }

    override fun getStatements(element: PsiElement): List<PsiElement> {
        return if (element is KtBlockExpression) {
            element.statements
        } else {
            emptyList()
        }
    }

    override fun getTopmostStatementWithText(psiElement: PsiElement, text: String): PsiElement? {
        val statement = psiElement.getParentByPredicate {
            isSupportedStatementToIntroduceVariable(it) && it.text.contains(text) && it.text != text
        }
        return if (statement is KtCallExpression) {
            return statement.parentsOfType<KtDotQualifiedExpression>().lastOrNull() ?: statement
        } else {
            statement
        }
    }

    override fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean {
        return element is KtProperty || element is KtIfExpression ||
                element is KtCallExpression || element is KtQualifiedExpression ||
                element is KtReturnExpression
    }

    override fun isPartOfExpression(element: PsiElement): Boolean {
        return element.getParentOfType<KtExpression>() != null
    }

    override fun isExpressionStatement(element: PsiElement): Boolean {
        return element is KtExpression
    }

    override fun isVariableDeclaration(element: PsiElement): Boolean {
        return element is KtProperty
    }

    override fun getVariableName(element: PsiElement): String? {
        return if (element is KtProperty) {
            element.name
        } else {
            null
        }
    }

    override fun isFileStructureElement(element: PsiElement): Boolean {
        return (element is KtProperty && !element.isLocal) || element is KtNamedFunction || element is KtClass
    }

    override fun isIdentifier(element: PsiElement): Boolean {
        return element is LeafPsiElement && element.elementType.toString() == "IDENTIFIER"
    }

    override fun isLiteralExpression(element: PsiElement): Boolean {
        return element is KtStringTemplateExpression
    }
}
