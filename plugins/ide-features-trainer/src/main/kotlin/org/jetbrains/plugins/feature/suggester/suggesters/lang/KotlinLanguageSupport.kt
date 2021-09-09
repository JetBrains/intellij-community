package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.plugins.feature.suggester.getParentByPredicate
import org.jetbrains.plugins.feature.suggester.getParentOfType

class KotlinLanguageSupport : LanguageSupport {

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
