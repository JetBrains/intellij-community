// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.common.plugin;

import com.intellij.editorconfig.common.syntax.lexer._EditorConfigLexer;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes;
import com.intellij.lexer.FlexAdapter;
import com.intellij.psi.tree.IElementType;

public class IntellijEditorConfigLexerAdapter extends FlexAdapter {

  private enum AdapterState {
    Initial,
    Header,
    Key,
    Value
  }

  private AdapterState myState = AdapterState.Initial;

  public IntellijEditorConfigLexerAdapter() {
    super(new _EditorConfigLexer());
  }

  @Override
  public IElementType getTokenType() {
    IElementType tokenType = super.getTokenType();
    if ( tokenType == EditorConfigElementTypes.DOT && myState == AdapterState.Value) {
      return IntellijEditorConfigTokenTypes.VALUE_CHAR;
    }
    return tokenType;
  }

  @Override
  public void advance() {
    super.advance();
    if (getTokenText().contains("\n")) {
      myState = AdapterState.Initial;
    }
    else {
      if (getCurrentPosition().getState() > 0) {
        myState = AdapterState.Header;
      }
      else {
        final IElementType tokenType = getTokenType();
        switch (myState) {
          case Initial -> {
            if (tokenType == EditorConfigElementTypes.IDENTIFIER) {
              myState = AdapterState.Key;
            }
          }
          case Header -> myState = AdapterState.Initial;
          case Key -> {
            if (tokenType == EditorConfigElementTypes.SEPARATOR) {
              myState = AdapterState.Value;
            }
          }
          case Value -> {
          }
        }
      }
    }
  }
}
