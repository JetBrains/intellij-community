package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import com.intellij.psi.tree.TokenSet;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, Ilya Sergey
 */
public interface GroovyElementTypes extends GroovyTokenTypes {

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");

  // Auxiliary elements
  GroovyElementType SEP = new GroovyElementType("Statement separator");

  // Top-level elements
  GroovyElementType FILE = new GroovyElementType("Groovy file");
  GroovyElementType COMPILATION_UNIT = new GroovyElementType("Compilation unit");
  GroovyElementType IMPORT_STATEMENT = new GroovyElementType("Import statement");
  GroovyElementType IDENITFIER_STAR = new GroovyElementType("Import identifier");

  GroovyElementType STATEMENT = new GroovyElementType("Any statement");

  //declaration
  GroovyElementType DECLARATION_START = new GroovyElementType("declaration start");

  GroovyElementType UPPER_CASE_IDENT = new GroovyElementType("upper case identifier");

  TokenSet WRONG_SET = TokenSet.create(WRONGWAY, mWRONG, mWRONG_GSTRING_LITERAL, mWRONG_STRING_LITERAL);
}
