// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.structureview

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile

class EditorConfigStructureViewFactory : PsiStructureViewFactory {
  override fun getStructureViewBuilder(psiFile: PsiFile) = EditorConfigStructureViewBuilder(psiFile)
}
