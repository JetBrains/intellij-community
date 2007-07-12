/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes extends GroovyTokenTypes {

  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");

  // Auxiliary elements
  GroovyElementType SEP = new GroovyElementType("Statement separator");
  GroovyElementType LITERAL = new GroovyElementType("Literal");

  // Top-level elements
  IFileElementType FILE = new IFileElementType(GroovyFileType.GROOVY_FILE_TYPE.getLanguage());

  //Packaging
  GroovyElementType PACKAGE_DEFINITION = new GroovyElementType("Package definition");

  // Blocks
  GroovyElementType STATEMENT = new GroovyElementType("Any statement");
  GroovyElementType CLOSABLE_BLOCK = new GroovyElementType("Closable block");
  GroovyElementType OPEN_BLOCK = new GroovyElementType("Open block");

  // Enum
  GroovyElementType ENUM_CONSTANTS = new GroovyElementType("Enumeration constants");
  GroovyElementType ENUM_CONSTANT = new GroovyElementType("Enumeration constant");
  GroovyElementType ENUM_CONSTANT_ERROR = new GroovyElementType("Enumeration constant error");

  GroovyElementType IMPORT_STATEMENT = new GroovyElementType("Import statement");

  //Branch statements
  GroovyElementType BREAK_STATEMENT = new GroovyElementType("Break statement");
  GroovyElementType CONTINUE_STATEMENT = new GroovyElementType("Continue statement");
  GroovyElementType RETURN_STATEMENT = new GroovyElementType("Return statement");
  GroovyElementType ASSERT_STATEMENT = new GroovyElementType("Assert statement");
  GroovyElementType THROW_STATEMENT = new GroovyElementType("Throw statement");

  // Expression statements
  GroovyElementType LABELED_STATEMENT = new GroovyElementType("Labeled statement");
  GroovyElementType CALL_EXPRESSION = new GroovyElementType("Expression statement");
  GroovyElementType COMMAND_ARGUMENT = new GroovyElementType("Command arguments");
  GroovyElementType COMMAND_ARGUMENTS = new GroovyElementType("Command argument");
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
  GroovyElementType RANGE_EXPRESSION = new GroovyElementType("Range expression");
  GroovyElementType COMPOSITE_SHIFT_SIGN = new GroovyElementType("Composite shift sign");
  GroovyElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  GroovyElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  GroovyElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  GroovyElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  GroovyElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  GroovyElementType CAST_EXPRESSION = new GroovyElementType("Simple unary expression");
  GroovyElementType POSTFIX_EXPRESSION = new GroovyElementType("Postfix expression");
  GroovyElementType PATH_EXPRESSION = new GroovyElementType("Path expression");
  GroovyElementType PATH_PROPERTY_REFERENCE = new GroovyElementType("Property reference");
  GroovyElementType PATH_METHOD_CALL = new GroovyElementType("Method call");
  GroovyElementType PATH_INDEX_PROPERTY = new GroovyElementType("Index property");
  GroovyElementType PARENTHESIZED_EXPRESSION = new GroovyElementType("Parenthesized expression");

  // Plain label
  GroovyElementType LABEL = new GroovyElementType("Label");

  // Arguments
  GroovyElementType ARGUMENTS = new GroovyElementType("Arguments");
  GroovyElementType ARGUMENT = new GroovyElementType("Compound argument");
  GroovyElementType ARGUMENT_LABEL = new GroovyElementType("Argument label");

  // Simple expression
  GroovyElementType PATH_PROPERTY = new GroovyElementType("Path name selector");
  GroovyElementType REFERENCE_EXPRESSION = new GroovyElementType("Reference expressions");
  GroovyElementType THIS_REFERENCE_EXPRESSION = new GroovyElementType("This reference expressions");
  GroovyElementType SUPER_REFERENCE_EXPRESSION = new GroovyElementType("Super reference expressions");
  GroovyElementType NEW_EXPRESSION = new GroovyElementType("New expressions");
  GroovyElementType PRIMARY_EXPRESSION = new GroovyElementType("Primary expressions");

  // Lists & maps
  GroovyElementType LIST_OR_MAP = new GroovyElementType("Generalized list");

  // Type Elements
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");
  GroovyElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");
  GroovyElementType REGEX = new GroovyElementType("Regular expression");

  GroovyElementType DECLARATION = new GroovyElementType("declaration");

  GroovyElementType CLASS_DEFINITION_ERROR = new GroovyElementType("class definition error");
  GroovyElementType INTERFACE_DEFINITION_ERROR = new GroovyElementType("interface definition error");
  GroovyElementType ENUM_DEFINITION_ERROR = new GroovyElementType("enumeration definition error");
  GroovyElementType ANNOTATION_DEFINITION_ERROR = new GroovyElementType("annotation definition error");

  GroovyElementType CLASS_DEFINITION = new GroovyElementType("class definition");
  GroovyElementType INTERFACE_DEFINITION = new GroovyElementType("interface definition");
  GroovyElementType ENUM_DEFINITION = new GroovyElementType("enumeration definition");
  GroovyElementType ANNOTATION_DEFINITION = new GroovyElementType("annotation definition");

  //types
  GroovyElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  GroovyElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");
  GroovyElementType IMPLEMENTS_CLAUSE = new GroovyElementType("implements clause");
  GroovyElementType EXTENDS_CLAUSE = new GroovyElementType("super class clause");

  GroovyElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments");
  GroovyElementType TYPE_ARGUMENT = new GroovyElementType("type argument");

  //fields
  GroovyElementType DEFAULT_ANNOTATION_MEMBER = new GroovyElementType("default annotation member");
  GroovyElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  //methods
  GroovyElementType METHOD_DEFINITION = new GroovyElementType("method definition");
  GroovyElementType CONSTRUCTOR_DEFINITION = new GroovyElementType("constructor definition");

  //bodies
  //  GroovyElementType METHOD_BODY = new GroovyElementType("method body");
  GroovyElementType CONSTRUCTOR_BODY_ERROR = new GroovyElementType("constructor body with error");
  //  GroovyElementType CONSTRUCTOR_BODY = new GroovyElementType("constructor body");
  GroovyElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  //throws
  GroovyElementType THROW_CLAUSE = new GroovyElementType("throw clause");

  //annotation
  GroovyElementType ANNOTATION_ARGUMENTS = new GroovyElementType("annotation arguments");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIR = new GroovyElementType("annotation member value pair");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIRS = new GroovyElementType("annotation member value pairs");
  GroovyElementType ANNOTATION = new GroovyElementType("annotation");

  //parameters
  GroovyElementType PARAMETERS_LIST = new GroovyElementType("parameters list");
  GroovyElementType PARAMETER = new GroovyElementType("parameter");
  GroovyElementType PARAMETER_MODIFIERS = new GroovyElementType("parameter modifiers");

  GroovyElementType CLASS_BODY = new GroovyElementType("class block");

  //statements
  GroovyElementType IF_STATEMENT = new GroovyElementType("if statement");
  GroovyElementType FOR_STATEMENT = new GroovyElementType("for statement");
  GroovyElementType WHILE_STATEMENT = new GroovyElementType("while statement");
  GroovyElementType WITH_STATEMENT = new GroovyElementType("with statement");
  GroovyElementType USE_STATEMENT = new GroovyElementType("with statement");

  // switch dtatement
  GroovyElementType SWITCH_STATEMENT = new GroovyElementType("switch statement");
  GroovyElementType CASE_BLOCK = new GroovyElementType("case block");
  GroovyElementType CASE_LABEL = new GroovyElementType("case label");

  //for clauses
  GroovyElementType FOR_IN_CLAUSE = new GroovyElementType("IN clause");
  GroovyElementType FOR_TRADITIONAL_CLAUSE = new GroovyElementType("Traditional clause");

  GroovyElementType STAR_STATEMENT = new GroovyElementType("star statement");
  GroovyElementType TRY_BLOCK_STATEMENT = new GroovyElementType("try block statement");
  GroovyElementType CATCH_CLAUSE = new GroovyElementType("catch clause");
  GroovyElementType FINALLY_CLAUSE = new GroovyElementType("finally clause");
  GroovyElementType SYNCHRONIZED_STATEMENT = new GroovyElementType("synchronized block statement");
  GroovyElementType STATIC_COMPOUND_STATEMENT = new GroovyElementType("static compound statement");
  GroovyElementType COMPOUND_STATEMENT = new GroovyElementType("compound statement");

  GroovyElementType VARIABLE_DEFINITION_ERROR = new GroovyElementType("variable definitions with errors");
  GroovyElementType VARIABLE_DEFINITION = new GroovyElementType("variable definitions");
  GroovyElementType VARIABLE_INITIALIZER = new GroovyElementType("variable initializer");
  GroovyElementType VARIABLE = new GroovyElementType("assigned variable");

  GroovyElementType FIELD = new GroovyElementType("field");
  //variable difinition of method call
  GroovyElementType VARIABLE_DEFINITION_OR_METHOD_CALL = new GroovyElementType("variable definition or method call");

  //modifiers
  //  GroovyElementType MODIFIER = new GroovyElementType("modifier"); //node
  GroovyElementType MODIFIERS = new GroovyElementType("modifiers"); //node

  GroovyElementType BALANCED_BRACKETS = new GroovyElementType("balanced brackets"); //node

  //types
  GroovyElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  //balanced tokens
  GroovyElementType BALANCED_TOKENS = new GroovyElementType("balanced tokens in the brackts"); //not a node

  GroovyElementType UPPER_CASE_IDENT = new GroovyElementType("Upper case identifier");
  TokenSet BLOCK_SET = TokenSet.create(CLOSABLE_BLOCK,
      OPEN_BLOCK,
      CLASS_BODY,
      CASE_BLOCK);
}