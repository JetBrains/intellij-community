// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

@Suppress("TooManyFunctions")
interface SuggesterSupport {
  companion object {
    private val EP_NAME: ExtensionPointName<LanguageExtensionPoint<SuggesterSupport>> =
      ExtensionPointName.create("training.ifs.suggesterSupport")

    private val extensions: List<LanguageExtensionPoint<SuggesterSupport>>
      get() = EP_NAME.extensionList

    fun getForLanguage(language: Language): SuggesterSupport? {
      return if (language == Language.ANY) {
        extensions.firstOrNull()?.instance
      }
      else {
        val langSupport = extensions.find { it.language == language.id }?.instance
        if (langSupport == null && language.baseLanguage != null) {
          val baseLanguage = language.baseLanguage!!
          extensions.find { it.language == baseLanguage.id }?.instance
        }
        else langSupport
      }
    }
  }

  fun isLoadedSourceFile(file: PsiFile): Boolean

  fun isIfStatement(element: PsiElement): Boolean

  fun isForStatement(element: PsiElement): Boolean

  fun isWhileStatement(element: PsiElement): Boolean

  fun isCodeBlock(element: PsiElement): Boolean

  fun getCodeBlock(element: PsiElement): PsiElement?

  fun getContainingCodeBlock(element: PsiElement): PsiElement?

  /**
   * element must be a code block @see [getCodeBlock], [getContainingCodeBlock], [isCodeBlock]
   */
  fun getParentStatementOfBlock(element: PsiElement): PsiElement?

  /**
   * element must be a code block @see [getCodeBlock], [getContainingCodeBlock], [isCodeBlock]
   */
  fun getStatements(element: PsiElement): List<PsiElement>

  fun getTopmostStatementWithText(psiElement: PsiElement, text: String): PsiElement?

  fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean

  fun isPartOfExpression(element: PsiElement): Boolean

  fun isExpressionStatement(element: PsiElement): Boolean

  fun isVariableDeclaration(element: PsiElement): Boolean

  /**
   * element must be a variable declaration @see [isVariableDeclaration]
   */
  fun getVariableName(element: PsiElement): String?

  /**
   * Return true if element is class, method or non local property definition
   */
  fun isFileStructureElement(element: PsiElement): Boolean

  fun isIdentifier(element: PsiElement): Boolean

  fun isLiteralExpression(element: PsiElement): Boolean
}
