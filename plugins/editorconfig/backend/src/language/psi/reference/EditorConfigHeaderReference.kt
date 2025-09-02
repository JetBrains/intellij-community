// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.psi.PsiReferenceBase

class EditorConfigHeaderReference(header: EditorConfigHeader) : PsiReferenceBase<EditorConfigHeader>(header) {
  override fun resolve(): EditorConfigHeader = element
}
