// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.breadcrumbs

import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPresentationUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil

class EditorConfigBreadcrumbsProvider : BreadcrumbsProvider {
  private val SUPPORTED_LANGUAGES: Array<EditorConfigLanguage>

  init {
    val enabled = EditorConfigRegistry.shouldSupportBreadCrumbs()
    SUPPORTED_LANGUAGES = if (enabled) arrayOf(EditorConfigLanguage) else emptyArray()
  }

  override fun getLanguages(): Array<EditorConfigLanguage> = SUPPORTED_LANGUAGES
  override fun acceptElement(element: PsiElement): Boolean =
    element is EditorConfigPsiFile
    || element is EditorConfigSection

  override fun getElementInfo(element: PsiElement): String = when (element) {
    is EditorConfigSection -> element.header.text
    is EditorConfigPsiFile -> EditorConfigPresentationUtil.getFileName(element, true)
    else -> "<unknown element>"
  }

  override fun getParent(element: PsiElement): PsiElement? = when (element) {
    is EditorConfigPsiFile -> EditorConfigPsiTreeUtil.findOneParentFile(element)
    else -> element.parent
  }

  override fun getChildren(element: PsiElement): List<PsiElement> = when (element) {
    is EditorConfigPsiFile -> EditorConfigPsiTreeUtil.findAllChildrenFiles(element, false)
    else -> element.children.toList()
  }
}
