// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.lexer;

import com.intellij.psi.tree.IElementType;
import org.editorconfig.language.psi.EditorConfigTokenType;

public final class IntellijEditorConfigTokenTypes {
  public final static IElementType VALUE_CHAR = new EditorConfigTokenType("VALUE_CHAR");
}
