package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author Ilya Sergey
 */
public class GroovyFlexLexer extends FlexAdapter {
  public GroovyFlexLexer() {
    super(new _GroovyLexer((Reader) null));
  }
}
