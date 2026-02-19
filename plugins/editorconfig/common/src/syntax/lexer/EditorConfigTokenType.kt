package com.intellij.editorconfig.common.syntax.lexer

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

class EditorConfigTokenType(@NonNls debugName: String) : IElementType(debugName, EditorConfigLanguage) {
  override fun toString(): String = "EditorConfigTokenType." + super.toString()
}
