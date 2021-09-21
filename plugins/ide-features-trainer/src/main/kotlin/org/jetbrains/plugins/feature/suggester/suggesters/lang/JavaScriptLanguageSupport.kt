package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.lang.ecmascript6.psi.ES6Class
import com.intellij.lang.javascript.psi.JSBlockStatement
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSExpressionStatement
import com.intellij.lang.javascript.psi.JSForStatement
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSIfStatement
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSStatement
import com.intellij.lang.javascript.psi.JSVarStatement
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.JSWhileStatement
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.plugins.feature.suggester.getParentByPredicate
import org.jetbrains.plugins.feature.suggester.getParentOfType

class JavaScriptLanguageSupport : LanguageSupport {
    override fun isSourceFile(file: PsiFile): Boolean {
        return file is JSFileImpl
    }

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
        return element.descendantsOfType<JSBlockStatement>().firstOrNull()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<JSBlockStatement>()
    }

    override fun getParentStatementOfBlock(element: PsiElement): PsiElement? {
        return element.parent
    }

    override fun getStatements(element: PsiElement): List<PsiElement> {
        return if (element is JSBlockStatement) {
            element.statementListItems.toList()
        } else {
            emptyList()
        }
    }

    override fun getTopmostStatementWithText(psiElement: PsiElement, text: String): PsiElement? {
        return psiElement.getParentByPredicate {
            isSupportedStatementToIntroduceVariable(it) && it.text.contains(text) && it.text != text
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
        return (element is JSVariable && element.getParentOfType<JSFunction>() == null) ||
            element is JSFunction || element is ES6Class
    }

    override fun isIdentifier(element: PsiElement): Boolean {
        return element is LeafPsiElement && element.elementType.toString() == "JS:IDENTIFIER"
    }

    override fun isLiteralExpression(element: PsiElement): Boolean {
        return element is JSLiteralExpression
    }
}
