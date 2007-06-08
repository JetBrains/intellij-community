/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.CompositeLexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiNameHelperImpl;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ven
 */
public class GroovyNamesUtil {
  private static final Object LOCK = new Object();

  public static boolean isIdentifier(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      Lexer lexer = new GroovyLexer();
      lexer.start(text, 0, text.length(), 0);
      if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) return false;
      lexer.advance();
      return lexer.getTokenType() == null;
    }
  }

  public static boolean isKeyword(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    synchronized (LOCK) {
      Lexer lexer = new GroovyLexer();
      lexer.start(text,0,text.length(),0);
      if (lexer.getTokenType() == null || !GroovyTokenTypes.KEYWORDS.contains(lexer.getTokenType())) return false;
      lexer.advance();
      return lexer.getTokenType() == null;
    }
  }





}
