// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.folding

import com.intellij.compose.ide.plugin.shared.COMPOSE_MODIFIER_FQN
import com.intellij.compose.ide.plugin.shared.callReturnTypeFqName
import com.intellij.compose.ide.plugin.shared.isAndroidFile
import com.intellij.compose.ide.plugin.shared.isComposeEnabledForElementModule
import com.intellij.compose.ide.plugin.shared.isInsideComposableControlFlow
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Adds a folding region for a Modifier chain longer or equal than two.
 *
 * Based on: [com.android.tools.compose.ComposeFoldingBuilder]
 */
@ApiStatus.Internal
abstract class ComposeFoldingBuilder : CustomFoldingBuilder() {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor>,
    root: PsiElement,
    document: Document,
    quick: Boolean,
  ) {
    if (root !is KtFile || DumbService.isDumb(root.project)) return

    // Do not run on Android modules - this is covered with the Android plugin
    if (isAndroidFile(root)) return

    // Do not run on modules that do not have Compose enabled
    if (!isComposeEnabledForElementModule(root)) return

    root.accept(ComposeFoldingVisitor(descriptors))
  }

  private fun KtDotQualifiedExpression.isModifierChainLongerThanOne(): Boolean {
    if (receiverExpression !is KtDotQualifiedExpression) return false
    return callReturnTypeFqName() == COMPOSE_MODIFIER_FQN
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
      if (expression.isFoldableModifierChain()) {
        descriptors.add(FoldingDescriptor(expression.node, expression.node.textRange))
      }
      super.visitDotQualifiedExpression(expression)
    }

    fun KtDotQualifiedExpression.isFoldableModifierChain(): Boolean =
      parent !is KtDotQualifiedExpression &&
      isModifierChainLongerThanOne() &&
      isInsideComposableControlFlow()
  }
}