// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.structureview

import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.editorconfig.common.syntax.psi.EditorConfigRootDeclaration
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.util.childrenOfType

class EditorConfigStructureViewElement(private val element: NavigatablePsiElement) : StructureViewTreeElement, SortableTreeElement {
  override fun getValue(): NavigatablePsiElement = element
  override fun navigate(requestFocus: Boolean): Unit = element.navigate(requestFocus)
  override fun canNavigate(): Boolean = element.canNavigate()
  override fun canNavigateToSource(): Boolean = element.canNavigateToSource()
  override fun getAlphaSortKey(): @NlsSafe String = element.name ?: ""
  override fun getPresentation(): ItemPresentation = element.presentation ?: PresentationData()

  override fun getChildren(): Array<out TreeElement> = when (element) {
    is EditorConfigPsiFile -> {
      val roots = element
        .childrenOfType<EditorConfigRootDeclaration>()
        .map(::EditorConfigStructureViewElement)

      val sections = element.sections
        .map(::EditorConfigStructureViewElement)
        .toTypedArray()

      (roots + sections).toTypedArray()
    }

    is EditorConfigSection -> element.optionList
      .map(::EditorConfigStructureViewElement)
      .toTypedArray()

    else -> emptyArray()
  }
}
