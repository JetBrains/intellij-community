// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.structureview

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class EditorConfigStructureViewBuilder(private val psiFile: PsiFile) : TreeBasedStructureViewBuilder() {
  override fun createStructureViewModel(editor: Editor?): StructureViewModel = EditorConfigStructureViewModel(psiFile)
}
