package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

interface LanguageSupport {
    companion object {
        val EP_NAME: ExtensionPointName<LanguageExtensionPoint<LanguageSupport>> =
            ExtensionPointName.create("org.intellij.featureSuggester.languageSupport")

        val extensions: List<LanguageExtensionPoint<LanguageSupport>>
            get() = EP_NAME.extensionList

        fun getForLanguage(language: Language): LanguageSupport? {
            return extensions.find { it.language == language.id }?.instance
        }
    }

    fun isIfStatement(element: PsiElement): Boolean

    fun isForStatement(element: PsiElement): Boolean

    fun isWhileStatement(element: PsiElement): Boolean

    fun isCodeBlock(element: PsiElement): Boolean

    fun getCodeBlock(element: PsiElement): PsiElement?

    fun getContainingCodeBlock(element: PsiElement): PsiElement?

    /**
     * element must be a code block @see [getCodeBlock], [getContainingCodeBlock]
     */
    fun getStatements(element: PsiElement): List<PsiElement>

    fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean

    fun isPartOfExpression(element: PsiElement): Boolean

    fun isExpressionStatement(element: PsiElement): Boolean

    fun isVariableDeclaration(element: PsiElement): Boolean

    /**
     * element must be a variable declaration @see [isVariableDeclaration]
     */
    fun getVariableName(element: PsiElement): String?
}