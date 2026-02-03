// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.suppression

import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.jetbrains.annotations.Nls

class EditorConfigSuppressInspectionFix(
  id: String,
  @Nls text: String,
  private val target: Class<out PsiElement>
) : AbstractBatchSuppressByNoInspectionCommentFix(id, false) {
  init {
    setText(text)
  }

  override fun getContainer(context: PsiElement?): PsiElement? = runReadAction {
    val element = EditorConfigPsiTreeUtil.findIdentifierUnderCaret(context)
    PsiTreeUtil.getParentOfType(element, target)
  }
}
