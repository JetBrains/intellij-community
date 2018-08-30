// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.editorconfig.language.codeinsight.linemarker.EditorConfigSectionLineMarkerProviderUtil
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.isSubcaseOf

class EditorConfigHeaderReference(header: EditorConfigHeader) : PsiPolyVariantReferenceBase<EditorConfigHeader>(header) {
  private val header get() = element

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val file = EditorConfigPsiTreeUtil.getOriginalFile(header.containingFile) as? EditorConfigPsiFile ?: return emptyArray()
    return EditorConfigPsiTreeUtil
      .findAllParentsFiles(file)
      .asSequence()
      .flatMap { it.sections.asSequence() }
      .map(EditorConfigSection::getHeader)
      .filter(EditorConfigSectionLineMarkerProviderUtil.createActualParentFilter(header))
      .filter { header.isSubcaseOf(it) }
      .map(::PsiElementResolveResult)
      .toList()
      .toTypedArray()
  }
}
