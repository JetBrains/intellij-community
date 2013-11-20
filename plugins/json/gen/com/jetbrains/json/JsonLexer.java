package com.jetbrains.json;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author Mikhail Golubev
 */
public class JsonLexer extends FlexAdapter {
  public JsonLexer() {
    super(new _JsonLexer((Reader)null));
  }
}
