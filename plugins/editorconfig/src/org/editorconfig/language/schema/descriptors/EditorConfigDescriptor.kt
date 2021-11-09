// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors

import com.intellij.psi.PsiElement

/**
 * For consistency purposes, all subclasses are advised to be data classes
 */
interface EditorConfigDescriptor {
  val children
    get() = emptyList<EditorConfigDescriptor>()

  val documentation: String?
  val deprecation: String?
  val parent: EditorConfigDescriptor?
  fun accept(visitor: EditorConfigDescriptorVisitor)
  fun matches(element: PsiElement): Boolean

  fun getPresentableText(): String = ""
}
