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

  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

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

  // Statements
  GroovyElementType STATEMENT = new GroovyElementType("Any statement");

  // Import elements
  GroovyElementType IMPORT_STATEMENT = new GroovyElementType("Import statement");
  GroovyElementType IDENITFIER_STAR = new GroovyElementType("Import identifier");
  GroovyElementType IMPORT_END = new GroovyElementType("Import end");
  GroovyElementType IMPORT_SELECTOR = new GroovyElementType("Import selector");

  // Expression statements
  GroovyElementType EXPRESSION_STATEMENT = new GroovyElementType("Expression statement");
  GroovyElementType ASSIGNMENT_EXXPRESSION = new GroovyElementType("Assignment expressions");
  GroovyElementType CONDITIONAL_EXXPRESSION = new GroovyElementType("Conditional expressions");


  GroovyElementType ADDITIVE_EXXPRESSION = new GroovyElementType("Additive expressions");

  GroovyElementType PRIMARY_EXXPRESSION = new GroovyElementType("Primary expressions");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");

  GroovyElementType DECLARATION = new GroovyElementType("declaration"); //node
  GroovyElementType TYPE_DEFINITION = new GroovyElementType("type definition"); //node

  GroovyElementType CLASS_DEFINITION = new GroovyElementType("class definition"); //node
  GroovyElementType INTERFACE_DEFINITION = new GroovyElementType("interface definition"); //node
  GroovyElementType ENUM_DEFINITION = new GroovyElementType("enumeration definition"); //node
  GroovyElementType ANNOTATION_DEFINITION = new GroovyElementType("annotation definition"); //node



  //declaration
  GroovyElementType DECLARATION_START = new GroovyElementType("Declaration start"); //not a node
  GroovyElementType DECLARATION_END = new GroovyElementType("Declaration end"); //not a node

  //modifiers
  GroovyElementType MODIFIER = new GroovyElementType("modifier"); //node
  GroovyElementType MODIFIERS = new GroovyElementType("modifiers"); //node

  //annotation
  GroovyElementType ANNOTATION = new GroovyElementType("annotation"); //node

  GroovyElementType BALANCED_BRACKETS = new GroovyElementType("balanced brackets"); //node

  //types
  GroovyElementType TYPE_SPECIFICATION = new GroovyElementType("specification of the type"); //node

  //balanced tokens
  GroovyElementType BALANCED_TOKENS = new GroovyElementType("balanced tokens in the brackts"); //not a node

  GroovyElementType UPPER_CASE_IDENT = new GroovyElementType("Upper case identifier");

  TokenSet tWRONG_SET = TokenSet.create(WRONGWAY, mWRONG, mWRONG_GSTRING_LITERAL, mWRONG_STRING_LITERAL);
}