package org.jetbrains.plugins.feature.suggester.suggesters.lang

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

interface LanguageSupport {
    companion object {
        val EP_NAME: ExtensionPointName<LanguageSupport> =
            ExtensionPointName.create("org.intellij.featureSuggester.languageSupport")

        fun getForLanguage(language: Language): LanguageSupport? {
            return EP_NAME.extensionList.find { it.supportedLang == language }
        }
    }

    val supportedLang: Language

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