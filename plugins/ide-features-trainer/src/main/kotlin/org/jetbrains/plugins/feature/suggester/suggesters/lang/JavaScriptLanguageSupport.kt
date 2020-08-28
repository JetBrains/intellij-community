package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.lang.ecmascript6.psi.ES6Class
import com.intellij.lang.javascript.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.findDescendantOfType
import org.jetbrains.plugins.feature.suggester.getParentOfType

class JavaScriptLanguageSupport : LanguageSupport {

    override fun isIfStatement(element: PsiElement): Boolean {
        return element is JSIfStatement
    }

    override fun isForStatement(element: PsiElement): Boolean {
        return element is JSForStatement
    }

    override fun isWhileStatement(element: PsiElement): Boolean {
        return element is JSWhileStatement
    }

    override fun isCodeBlock(element: PsiElement): Boolean {
        return element is JSBlockStatement
    }

    override fun getCodeBlock(element: PsiElement): PsiElement? {
        return element.findDescendantOfType<JSBlockStatement>()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<JSBlockStatement>()
    }

    override fun getParentStatementOfBlock(element: PsiElement): PsiElement? {
        return element.parent
    }

    override fun getStatements(element: PsiElement): List<PsiElement> {
        return if (element is JSBlockStatement) {
            element.statements.toList()
        } else {
            emptyList()
        }
    }

    override fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean {
        return element is JSStatement
    }

    override fun isPartOfExpression(element: PsiElement): Boolean {
        return element.getParentOfType<JSExpression>() != null
    }

    override fun isExpressionStatement(element: PsiElement): Boolean {
        return element is JSExpressionStatement
    }

    override fun isVariableDeclaration(element: PsiElement): Boolean {
        return element is JSVarStatement
    }

    override fun getVariableName(element: PsiElement): String? {
        return if (element is JSVarStatement) {
            element.declarations.firstOrNull()?.name
        } else {
            null
        }
    }

    override fun isFileStructureElement(element: PsiElement): Boolean {
        return (element is JSVariable && element.getParentOfType<JSFunction>() == null)
                || element is JSFunction || element is ES6Class
    }

    override fun isIdentifier(element: PsiElement): Boolean {
        return element is LeafPsiElement && element.elementType.toString() == "JS:IDENTIFIER"
    }

    override fun isLiteralExpression(element: PsiElement): Boolean {
        return element is JSLiteralExpression
    }
}