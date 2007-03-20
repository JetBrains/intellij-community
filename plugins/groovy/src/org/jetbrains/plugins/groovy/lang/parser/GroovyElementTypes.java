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
  GroovyElementType COMMAND_ARGUMENTS = new GroovyElementType("Command arguments");
  GroovyElementType CONDITIONAL_EXPRESSION = new GroovyElementType("Conditional expression");
  GroovyElementType ASSIGNMENT_EXPRESSION = new GroovyElementType("Assignment expression");
  GroovyElementType LOGICAL_OR_EXPRESSION = new GroovyElementType("Logical OR expression");
  GroovyElementType LOGICAL_AND_EXPRESSION = new GroovyElementType("Logical AND expression");
  GroovyElementType INCLUSIVE_OR_EXPRESSION = new GroovyElementType("Inclusive OR expression");
  GroovyElementType EXCLUSIVE_OR_EXPRESSION = new GroovyElementType("Exclusive OR expression");
  GroovyElementType AND_EXPRESSION = new GroovyElementType("AND expression");
  GroovyElementType REGEX_EXPRESSION = new GroovyElementType("Regex expression");
  GroovyElementType EQUALITY_EXPRESSION = new GroovyElementType("Equality expression");
  GroovyElementType RELATIONAL_EXPRESSION = new GroovyElementType("Relational expression");
  GroovyElementType SHIFT_EXPRESSION = new GroovyElementType("Shift expression");
  GroovyElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  GroovyElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  GroovyElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  GroovyElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  GroovyElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  GroovyElementType UNARY_EXPRESSION_NOT_PM = new GroovyElementType("Simple unary expression");
  GroovyElementType TYPE_CAST = new GroovyElementType("Explicit typecast");
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");

  GroovyElementType POSTFIX_EXPRESSION = new GroovyElementType("Postfix expression");




  GroovyElementType PRIMARY_EXXPRESSION = new GroovyElementType("Primary expressions");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");

  GroovyElementType DECLARATION = new GroovyElementType("declaration"); //node

  GroovyElementType CLASS_DEFINITION = new GroovyElementType("class definition"); //node
  GroovyElementType INTERFACE_DEFINITION = new GroovyElementType("interface definition"); //node
  GroovyElementType ENUM_DEFINITION = new GroovyElementType("enumeration definition"); //node
  GroovyElementType ANNOTATION_DEFINITION = new GroovyElementType("annotation definition"); //node

  GroovyElementType CLASS_INTERFACE_TYPE = new GroovyElementType("class or interface type"); //node
  GroovyElementType IMPLEMENTS_CLAUSE = new GroovyElementType("implements clause"); //node
  GroovyElementType INTERFACE_EXTENDS_CLAUSE = new GroovyElementType("interface extends clause"); //node
  GroovyElementType SUPER_CLASS_CLAUSE = new GroovyElementType("super class clause"); //node

  GroovyElementType CLASS_FIELD = new GroovyElementType("class field"); //node
  GroovyElementType INTERFACE_FIELD = new GroovyElementType("interface field"); //node
  GroovyElementType ANNOTATION_FIELD = new GroovyElementType("annotation field"); //node
  GroovyElementType ENUM_FIELD = new GroovyElementType("enumeration field"); //node

  //blocks
  GroovyElementType CLASS_BLOCK = new GroovyElementType("class block"); //node
  GroovyElementType INTERFACE_BLOCK = new GroovyElementType("interface block"); //node
  GroovyElementType ENUM_BLOCK = new GroovyElementType("enumeration block"); //node
  GroovyElementType ANNOTATION_BLOCK = new GroovyElementType("annotation block"); //node

  //statements
  GroovyElementType IF_STATEMENT = new GroovyElementType("if statement"); //node
  GroovyElementType FOR_STATEMENT = new GroovyElementType("for statement"); //node
  GroovyElementType WHILE_STATEMENT = new GroovyElementType("while statement"); //node
  GroovyElementType WITH_STATEMENT = new GroovyElementType("with statement"); //node
  GroovyElementType SWITCH_STATEMENT = new GroovyElementType("switch statement"); //node
  //todo: rename star expression
  GroovyElementType STAR_STATEMENT = new GroovyElementType("star statement"); //node
  GroovyElementType TRY_BLOCK_STATEMENT = new GroovyElementType("try block statement"); //node
  GroovyElementType SYNCHRONIZED_BLOCK_STATEMENT = new GroovyElementType("synchronized block statement"); //node

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