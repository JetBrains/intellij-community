package com.intellij.lang.properties;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author max
 */
public class PropertiesLexer extends FlexAdapter {
  public PropertiesLexer() {
    super(new _PropertiesLexer((Reader)null));
  }
}
