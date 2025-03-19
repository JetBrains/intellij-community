// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.lexer;

import com.intellij.psi.tree.IElementType;
import org.editorconfig.language.psi.EditorConfigTokenType;

public final class IntellijEditorConfigTokenTypes {
  public static final IElementType VALUE_CHAR = new EditorConfigTokenType("VALUE_CHAR");
}
