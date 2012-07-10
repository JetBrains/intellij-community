package com.jetbrains.gettext;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextLexer extends FlexAdapter {
  public GetTextLexer() {
    super(new _GetTextLexer((Reader)null));
  }
}