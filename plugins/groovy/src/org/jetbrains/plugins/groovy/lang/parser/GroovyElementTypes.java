package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

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
  GroovyElementType IDENTIFIER = new GroovyElementType("Statement separator");

  // Top-level elements
  GroovyElementType FILE = new GroovyElementType("Groovy file");
  GroovyElementType COMPILATION_UNIT = new GroovyElementType("Compilation unit");

  //Packaging
  GroovyElementType PACKAGE_DEFINITION = new GroovyElementType("Package definition");

  // Import elements
  GroovyElementType IMPORT_STATEMENT = new GroovyElementType("Import statement");
  GroovyElementType IDENITFIER_STAR = new GroovyElementType("Import identifier");
  GroovyElementType IMPORT_END = new GroovyElementType("Import end");
  GroovyElementType IMPORT_SELECTOR = new GroovyElementType("Import selector");

  GroovyElementType STATEMENT = new GroovyElementType("Any statement");

  //declaration
  GroovyElementType DECLARATION_START = new GroovyElementType("Declaration start"); //not node
  GroovyElementType DECLARATION = new GroovyElementType("declaration"); //node

  GroovyElementType BALANCED_BRACKETS = new GroovyElementType("balanced brackets"); //node

  //balanced tokens
  GroovyElementType BALANCED_TOKENS = new GroovyElementType("balanced tokens in the brackts"); //not node

  GroovyElementType UPPER_CASE_IDENT = new GroovyElementType("Upper case identifier");

  TokenSet tWRONG_SET = TokenSet.create(WRONGWAY, mWRONG, mWRONG_GSTRING_LITERAL, mWRONG_STRING_LITERAL);
}