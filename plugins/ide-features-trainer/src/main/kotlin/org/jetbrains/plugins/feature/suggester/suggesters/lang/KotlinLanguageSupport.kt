package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findDescendantOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.feature.suggester.suggesters.getParentOfType

class KotlinLanguageSupport : LanguageSupport {

    override val supportedLang: Language = KotlinLanguage.INSTANCE

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
        return element.findDescendantOfType<KtBlockExpression>()
    }

    override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
        return element.getParentOfType<KtBlockExpression>()
    }

    override fun getStatements(element: PsiElement): List<PsiElement> {
        return if (element is KtBlockExpression) {
            element.statements
        } else {
            emptyList()
        }
    }

    override fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean {
        return element is KtProperty || element is KtIfExpression
                || element is KtCallExpression || element is KtQualifiedExpression
                || element is KtReturnExpression
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
}