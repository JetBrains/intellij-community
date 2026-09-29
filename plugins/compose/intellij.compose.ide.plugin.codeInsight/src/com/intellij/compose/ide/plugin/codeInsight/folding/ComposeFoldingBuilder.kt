// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight.folding

import com.intellij.compose.ide.plugin.codeInsight.isInsideComposableControlFlow
import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.callReturnTypeFqName
import com.intellij.compose.ide.plugin.shared.isAndroidFile
import com.intellij.compose.ide.plugin.shared.isComposeEnabledForElementModule
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/** Adds a folding region for a Modifier chain longer or equal than two. */
internal class ComposeFoldingBuilder : CustomFoldingBuilder() {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor>,
    root: PsiElement,
    document: Document,
    quick: Boolean,
  ) {
    if (root !is KtFile || DumbService.isDumb(root.project)) return

    // Do not run on modules that do not have Compose enabled
    if (!isComposeEnabledForElementModule(root)) return

    root.accept(ComposeFoldingVisitor(descriptors))
  }

  private fun KtDotQualifiedExpression.isModifierChainLongerThanOne(): Boolean {
    return receiverExpression is KtDotQualifiedExpression && callReturnTypeFqName() == COMPOSE_MODIFIER_FQN
  }

  /** For Modifier.adjust().adjust() -> Modifier.(...) */
  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    return node.text.substringBefore(".").trim() + ".(...)"
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false

  private inner class ComposeFoldingVisitor(
    private val descriptors: MutableList<FoldingDescriptor>
  ) : KtTreeVisitorVoid() {

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
      val isFoldableModifierChain = expression.run {
        parent !is KtDotQualifiedExpression &&
        isModifierChainLongerThanOne() &&
        isInsideComposableControlFlow()
      }
      if (isFoldableModifierChain) descriptors.add(FoldingDescriptor(expression.node, expression.node.textRange))
      super.visitDotQualifiedExpression(expression)
    }
  }
}
