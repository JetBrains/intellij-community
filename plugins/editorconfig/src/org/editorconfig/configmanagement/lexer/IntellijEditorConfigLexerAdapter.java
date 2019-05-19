// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.psi.tree.IElementType;
import org.editorconfig.language.lexer._EditorConfigLexer;
import org.editorconfig.language.psi.EditorConfigElementTypes;

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
          case Initial:
            if (tokenType == EditorConfigElementTypes.IDENTIFIER) {
              myState = AdapterState.Key;
            }
            break;
          case Header:
            myState = AdapterState.Initial;
            break;
          case Key:
            if (tokenType == EditorConfigElementTypes.SEPARATOR) {
              myState = AdapterState.Value;
            }
            break;
          case Value:
            break;
        }
      }
    }
  }
}
