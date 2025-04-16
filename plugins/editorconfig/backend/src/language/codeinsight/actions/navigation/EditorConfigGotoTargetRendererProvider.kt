// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiElement
import org.editorconfig.language.util.EditorConfigPresentationUtil

class EditorConfigGotoTargetRendererProvider : GotoTargetRendererProvider {
  override fun getRenderer(element: PsiElement, gotoData: GotoTargetHandler.GotoData): PsiElementListCellRenderer<*>? {
    if (element !is EditorConfigHeader && element !is EditorConfigFlatOptionKey) return null
    return object : PsiElementListCellRenderer<PsiElement?>() {
      override fun getContainerText(element: PsiElement?, name: String): String? {
        val containingFile = element?.containingFile as? EditorConfigPsiFile ?: return null
        return EditorConfigPresentationUtil.getFileName(containingFile, true)
      }

      override fun getElementText(element: PsiElement?) = when (element) {
        is EditorConfigHeader,
        is EditorConfigFlatOptionKey -> element.text
        else -> "unknown element"
      }
    }
  }
}
