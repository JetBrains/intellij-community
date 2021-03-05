package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.util.descendantsOfType
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
        return element.descendantsOfType<PsiCodeBlock>().firstOrNull()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<PsiCodeBlock>()
    }

    override fun getParentStatementOfBlock(element: PsiElement): PsiElement? {
        return element.parent?.parent
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
