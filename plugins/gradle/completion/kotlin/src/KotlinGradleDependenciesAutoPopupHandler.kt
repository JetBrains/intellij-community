// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal class KotlinGradleDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!useDependencyCompletionService()) return Result.CONTINUE
    if (!FileUtilRt.extensionEquals(file.name, "gradle.kts") || charTyped != '"') return Result.CONTINUE

    val offset = editor.caretModel.offset
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      if (!insideScriptBlockPattern(DEPENDENCIES).accepts(element)) return@scheduleAutoPopup false
      if (!element.isCallArgument()) return@scheduleAutoPopup false
      element.isSingleDependencyArgument()
      || element.isDependencyArgument(exclude)
      || element.isPositionalOrNamedDependencyArgument()
    }

    return Result.CONTINUE
  }

  // the following util methods are based on utils.kt but with one parent less (the psi element here is a parent of psi element used there)

  private fun PsiElement.isSingleDependencyArgument(): Boolean =
    this.argumentsSize == 1 && (this.argumentName.isEmpty() || this.argumentName == "dependencyNotation")

  private fun PsiElement.isDependencyArgument(dependencyConfigurations: Collection<String>): Boolean {
    val pattern = psiElement<LeafPsiElement>().withSuperParent(
      4, psiElement<KtCallExpression>().withChild(
        psiElement<KtNameReferenceExpression>().withText(string().oneOf(dependencyConfigurations))
      )
    )

    return pattern.accepts(this)
  }

  private fun PsiElement.isPositionalOrNamedDependencyArgument(): Boolean {
    val argumentName = this.argumentName
    return argumentName in setOf("group", "name", "version") || (argumentName.isEmpty() && this.argumentIndex in 0..2)
  }

  /**
   * Checks if the current element is a string quote in the place of a call argument.
   *
   * TODO should filter by allowed dependencyConfigurations and methods such as `exclude` once that is implemented
   */
  private fun PsiElement.isCallArgument(): Boolean {
    val pattern = psiElement<LeafPsiElement>().withSuperParent(1, KtStringTemplateExpression::class.java)
      .withSuperParent(2, KtValueArgument::class.java)
      .withSuperParent(3, psiElement<KtValueArgumentList>())
      .withSuperParent(4, psiElement<KtCallExpression>())

    return pattern.accepts(this)
  }

  private val PsiElement.argumentsSize get(): Int = (this.parent?.parent?.parent as? KtValueArgumentList)?.arguments?.size ?: 0

  private val PsiElement.argumentName: String
    get() {
      val valueArgument = this.parent?.parent as? KtValueArgument ?: return ""
      val valueArgumentName = valueArgument.children[0] as? KtValueArgumentName ?: return ""
      return valueArgumentName.text
    }

  private val PsiElement.argumentIndex: Int
    get() {
      val valueArgument = this.parent?.parent as? KtValueArgument ?: return -1
      val argumentList = valueArgument.parent as? KtValueArgumentList ?: return -1
      return argumentList.arguments.indexOf(valueArgument)
    }
}
