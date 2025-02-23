package com.intellij.compose.ide.plugin.k1.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.highlighting.ComposableFunctionCallHighlightingTestCase
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

internal class K1ComposableFunctionCallHighlightingTestCase : ComposableFunctionCallHighlightingTestCase() {
  private val ext = ComposableHighlightingVisitorExtension()

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K1

  override fun PsiFile.highlightCallUnderCaret(): HighlightInfoType? {
    val element = checkNotNull(this.findElementAt(editor.caretModel.offset)) {
      "Element under caret not found!"
    }

    val resolvedCall = (element.parentOfType<KtCallExpression>() ?: element.parentOfType<KtNameReferenceExpression>())
      ?.resolveToCall() as ResolvedCall<*>
    return ext.highlightCall(element, resolvedCall)
  }
}