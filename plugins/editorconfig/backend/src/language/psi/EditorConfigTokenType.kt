// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi

import com.intellij.psi.tree.IElementType
import org.editorconfig.language.EditorConfigLanguage
import org.jetbrains.annotations.NonNls

class EditorConfigTokenType(@NonNls debugName: String) : IElementType(debugName, EditorConfigLanguage) {
  override fun toString(): String = "EditorConfigTokenType." + super.toString()
}
