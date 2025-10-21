// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.compose.ide.plugin.shared.highlighting.ComposableFunctionParameterCallHighlightingTestCase
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class K2ComposableFunctionParameterCallHighlightingTestCase : ComposableFunctionParameterCallHighlightingTestCase() {
  private val ext = ComposableFunctionCallHighlighterExtension()

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

  @OptIn(KaAllowAnalysisOnEdt::class)
  override fun PsiFile.highlightCallUnderCaret(): HighlightInfoType? = allowAnalysisOnEdt {
    val element = checkNotNull(this.findElementAt(editor.caretModel.offset)) {
      "Element at caret not found!"
    }

    return analyze(file as KtFile) {
      val call = (element.parentOfType<KtCallExpression>() ?: element.parentOfType<KtNameReferenceExpression>())
        ?.resolveToCall()?.successfulCallOrNull<KaCall>()

      checkNotNull(call) {
        "Call was not found!"
      }

      with(ext) {
        highlightCall(element, call)
      }
    }
  }
}