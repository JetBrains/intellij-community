// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.interfaces

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor

interface EditorConfigDescribableElement : PsiElement, NavigatablePsiElement {
  val option: EditorConfigOption
  val section: EditorConfigSection
  val describableParent: EditorConfigDescribableElement?
  val declarationSite: String
  fun getDescriptor(smart: Boolean): EditorConfigDescriptor?
}
