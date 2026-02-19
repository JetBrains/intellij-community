// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.structureview

import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigRootDeclaration
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.psi.PsiFile

class EditorConfigStructureViewModel(psiFile: PsiFile) :
  StructureViewModelBase(psiFile, EditorConfigStructureViewElement(psiFile)), StructureViewModel.ElementInfoProvider {

  override fun getSorters(): Array<Sorter?> = arrayOf(Sorter.ALPHA_SORTER)
  override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false
  override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = when (element.value) {
    is EditorConfigFlatOptionKey -> true
    is EditorConfigQualifiedOptionKey -> true
    is EditorConfigRootDeclaration -> true
    else -> false
  }
}
