// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.interfaces

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigSection

interface EditorConfigHeaderElement : PsiElement, NavigatablePsiElement {
  val header: EditorConfigHeader
  val section: EditorConfigSection
}
