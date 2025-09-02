package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

class EditorConfigElementType(@NonNls debugName: String) : IElementType(debugName, EditorConfigLanguage)