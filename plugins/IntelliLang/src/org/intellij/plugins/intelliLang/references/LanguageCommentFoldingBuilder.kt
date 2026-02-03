// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import org.intellij.plugins.intelliLang.inject.InjectorUtils

internal class LanguageCommentFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    return SmartList<FoldingDescriptor>().also { result ->
      root.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitComment(comment: PsiComment) {
          super.visitComment(comment)
          if (LanguageInjectionUtil.languageRange(comment) != null) {
            result.add(FoldingDescriptor(comment, comment.textRange))
          }
        }
      })
    }.toArray(FoldingDescriptor.EMPTY_ARRAY)
  }

  override fun getPlaceholderText(node: ASTNode): String? {
    val psiComment = node.psi.asSafely<PsiComment>() ?: return null
    val languageRange = LanguageInjectionUtil.languageRange(psiComment) ?: return null
    val writtenText = languageRange.substring(psiComment.text)
    return InjectorUtils.getLanguageByString(writtenText)?.id ?: writtenText
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}