// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.actions.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigFlatOptionKey
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.reference.findParents
import org.editorconfig.language.util.headers.EditorConfigOverridingHeaderSearcher

internal class EditorConfigGotoSuperHandler : GotoTargetHandler() {
  override fun getFeatureUsedKey() = GotoSuperAction.FEATURE_ID

  override fun getSourceAndTargetElements(editor: Editor, file: PsiFile): GotoData? {
    val source = findSource(editor, file) ?: return null
    val targets = findTargets(source)
    return GotoData(source, targets.toTypedArray(), emptyList())
  }

  override fun getChooserTitle(sourceElement: PsiElement, name: String?, length: Int, finished: Boolean) = when (sourceElement) {
    is EditorConfigHeader -> EditorConfigBundle.get("goto.super.select.header")
    is EditorConfigFlatOptionKey -> EditorConfigBundle.get("goto.super.select.option")
    else -> EditorConfigBundle.get("goto.super.select.parent")
  }

  override fun getNotFoundMessage(project: Project, editor: Editor, file: PsiFile) = when (findSource(editor, file)) {
    is EditorConfigHeader -> EditorConfigBundle.get("goto.super.header.not.found")
    is EditorConfigFlatOptionKey -> EditorConfigBundle.get("goto.super.option.not.found")
    else -> EditorConfigBundle.get("goto.super.parent.not.found")
  }

  private fun findSource(editor: Editor, file: PsiFile): PsiElement? {
    val element = file.findElementAt(editor.caretModel.offset) ?: return null
    return PsiTreeUtil.getParentOfType(
      element,
      EditorConfigHeader::class.java,
      EditorConfigFlatOptionKey::class.java
    )
  }

  private fun findTargets(element: PsiElement) = when (element) {
    // todo icons
    is EditorConfigHeader -> EditorConfigOverridingHeaderSearcher().findMatchingHeaders(element).map { it.header }
    is EditorConfigFlatOptionKey -> element.findParents()
    else -> emptyList()
  }
}
