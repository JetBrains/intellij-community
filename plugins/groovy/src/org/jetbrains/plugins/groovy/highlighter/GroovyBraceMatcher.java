package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * Brace matcher for Groovy language
 *
 * @author Ilya.Sergey
 */
public class GroovyBraceMatcher  implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
          new BracePair('(', GroovyTokenTypes.mLPAREN, ')', GroovyTokenTypes.mRPAREN, false),
          new BracePair('[', GroovyTokenTypes.mLBRACK, ']', GroovyTokenTypes.mRBRACK, false),
          new BracePair('{', GroovyTokenTypes.mLCURLY, '}', GroovyTokenTypes.mRCURLY, true)
  };

  public BracePair[] getPairs() {

    return PAIRS;
  }
}