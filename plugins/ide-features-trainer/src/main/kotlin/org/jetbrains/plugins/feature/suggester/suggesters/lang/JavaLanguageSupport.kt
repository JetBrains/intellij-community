package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.psi.*
import com.intellij.psi.util.findDescendantOfType
import org.jetbrains.plugins.feature.suggester.getParentOfType

class JavaLanguageSupport : LanguageSupport {

    override fun isIfStatement(element: PsiElement): Boolean {
        return element is PsiIfStatement
    }

    override fun isForStatement(element: PsiElement): Boolean {
        return element is PsiForStatement
    }

    override fun isWhileStatement(element: PsiElement): Boolean {
        return element is PsiWhileStatement
    }

    override fun isCodeBlock(element: PsiElement): Boolean {
        return element is PsiCodeBlock
    }

    override fun getCodeBlock(element: PsiElement): PsiElement? {
        return element.findDescendantOfType<PsiCodeBlock>()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<PsiCodeBlock>()
    }

    override fun getStatements(element: PsiElement): List<PsiElement> {
        return if (element is PsiCodeBlock) {
            element.statements.toList()
        } else {
            emptyList()
        }
    }

    override fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean {
        return element is PsiStatement
    }

    override fun isPartOfExpression(element: PsiElement): Boolean {
        return element.getParentOfType<PsiExpression>() != null
    }

    override fun isExpressionStatement(element: PsiElement): Boolean {
        return element is PsiExpressionStatement
    }

    override fun isVariableDeclaration(element: PsiElement): Boolean {
        return element is PsiDeclarationStatement
    }

    override fun getVariableName(element: PsiElement): String? {
        return if (element is PsiDeclarationStatement) {
            val localVariable = element.declaredElements.firstOrNull() as? PsiLocalVariable
            localVariable?.name
        } else {
            null
        }
    }

    override fun isFileStructureElement(element: PsiElement): Boolean {
        return element is PsiField || element is PsiMethod || element is PsiClass
    }

    override fun isIdentifier(element: PsiElement): Boolean {
        return element is PsiIdentifier
    }

    override fun isLiteralExpression(element: PsiElement): Boolean {
        return element is PsiLiteralExpression
    }
}