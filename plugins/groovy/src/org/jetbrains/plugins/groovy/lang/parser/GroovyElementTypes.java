/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.*;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes extends GroovyTokenTypes, GroovyDocElementTypes {

  /*
  Stub elements
   */
  GrStubElementType<GrTypeDefinitionStub, GrClassDefinition> CLASS_DEFINITION = new GrClassDefinitionElementType();
  GrStubElementType<GrTypeDefinitionStub, GrInterfaceDefinition> INTERFACE_DEFINITION = new GrInterfaceDefinitionElementType();
  GrStubElementType<GrTypeDefinitionStub, GrEnumTypeDefinition> ENUM_DEFINITION = new GrEnumDefinitionElementType();
  GrStubElementType<GrTypeDefinitionStub, GrAnnotationTypeDefinition> ANNOTATION_DEFINITION = new GrAnnotationDefinitionElementType();
  GrStubElementType<GrTypeDefinitionStub, GrAnonymousClassDefinition> ANONYMOUS_CLASS_DEFINITION = new GrAnonymousClassDefinitionElementType();

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = new GrEnumConstantElementType();
  GrStubElementType<GrFieldStub, GrField> FIELD = new GrFieldElementType();
  GrStubElementType<GrMethodStub, GrMethod> METHOD_DEFINITION = new GrMethodElementType();
  GrStubElementType<GrAnnotationMethodStub, GrAnnotationMethod> ANNOTATION_METHOD = new GrAnnotationMethodElementType();

  GrStubElementType<GrReferenceListStub, GrImplementsClause> IMPLEMENTS_CLAUSE = new GrImplementsClauseElementType();
  GrStubElementType<GrReferenceListStub, GrExtendsClause> EXTENDS_CLAUSE = new GrExtendsClauseElementType();


  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

  GroovyElementType IDENTIFIER = new GroovyElementType("Groovy identifier");

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = new GroovyElementType("Literal");
  //Packaging
  GroovyElementType PACKAGE_DEFINITION = new GroovyElementType("Package definition");

  GroovyElementType CLOSABLE_BLOCK = new GroovyElementType("Closable block");
  GroovyElementType OPEN_BLOCK = new GroovyElementType("Open block");

  GroovyElementType BLOCK_STATEMENT = new GroovyElementType("Block statement");

  // Enum
  GroovyElementType ENUM_CONSTANTS = new GroovyElementType("Enumeration constants");
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
  GroovyElementType COMMAND_ARGUMENTS = new GroovyElementType("Command argument");
  GroovyElementType CONDITIONAL_EXPRESSION = new GroovyElementType("Conditional expression");
  GroovyElementType ELVIS_EXPRESSION = new GroovyElementType("Elvis expression");
  GroovyElementType ASSIGNMENT_EXPRESSION = new GroovyElementType("Assignment expression");
  GroovyElementType LOGICAL_OR_EXPRESSION = new GroovyElementType("Logical OR expression");
  GroovyElementType LOGICAL_AND_EXPRESSION = new GroovyElementType("Logical AND expression");
  GroovyElementType INCLUSIVE_OR_EXPRESSION = new GroovyElementType("Inclusive OR expression");
  GroovyElementType EXCLUSIVE_OR_EXPRESSION = new GroovyElementType("Exclusive OR expression");
  GroovyElementType AND_EXPRESSION = new GroovyElementType("AND expression");
  GroovyElementType REGEX_FIND_EXPRESSION = new GroovyElementType("Regex Find expression");
  GroovyElementType REGEX_MATCH_EXPRESSION = new GroovyElementType("Regex Match expression");
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
  GroovyElementType CAST_EXPRESSION = new GroovyElementType("cast expression");
  GroovyElementType SAFE_CAST_EXPRESSION = new GroovyElementType("safe cast expression");
  GroovyElementType INSTANCEOF_EXPRESSION = new GroovyElementType("instanceof expression");
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

  GroovyElementType BUILT_IN_TYPE_EXPRESSION = new GroovyElementType("Built in type expression");

  // Lists & maps
  GroovyElementType LIST_OR_MAP = new GroovyElementType("Generalized list");
  // Type Elements
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");

  GroovyElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");
  IElementType GSTRING_INJECTION =new GroovyElementType("Gstring injection");

  GroovyElementType REGEX = new GroovyElementType("Regular expression");
  //types
  GroovyElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  GroovyElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");

  GroovyElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments");
  GroovyElementType TYPE_ARGUMENT = new GroovyElementType("type argument");
  GroovyElementType TYPE_PARAMETER_LIST = new GroovyElementType("type parameter list");

  GroovyElementType TYPE_PARAMETER = new GroovyElementType("type parameter");
  GroovyElementType TYPE_PARAMETER_EXTENDS_BOUND_LIST = new GroovyElementType("type extends list");

  GroovyElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GroovyElementType CONSTRUCTOR_DEFINITION = new GroovyElementType("constructor definition");

  //  GroovyElementType CONSTRUCTOR_BODY = new GroovyElementType("constructor body");
  GroovyElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  //throws
  GroovyElementType THROW_CLAUSE = new GroovyElementType("throw clause");
  //annotation
  GroovyElementType ANNOTATION_ARRAY_INITIALIZER = new GroovyElementType("annotation array initializer");
  GroovyElementType ANNOTATION_ARGUMENTS = new GroovyElementType("annotation arguments");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIR = new GroovyElementType("annotation member value pair");
  GroovyElementType ANNOTATION_MEMBER_VALUE_PAIRS = new GroovyElementType("annotation member value pairs");

  GroovyElementType ANNOTATION = new GroovyElementType("annotation");
  //parameters
  GroovyElementType PARAMETERS_LIST = new GroovyElementType("parameters list");

  GroovyElementType PARAMETER = new GroovyElementType("parameter");
  GroovyElementType CLASS_BODY = new GroovyElementType("class block");

  GroovyElementType ENUM_BODY = new GroovyElementType("enum block");
  //statements
  GroovyElementType IF_STATEMENT = new GroovyElementType("if statement");
  GroovyElementType FOR_STATEMENT = new GroovyElementType("for statement");

  GroovyElementType WHILE_STATEMENT = new GroovyElementType("while statement");
  // switch dtatement
  GroovyElementType SWITCH_STATEMENT = new GroovyElementType("switch statement");
  GroovyElementType CASE_SECTION = new GroovyElementType("case block");

  GroovyElementType CASE_LABEL = new GroovyElementType("case label");
  //for clauses
  GroovyElementType FOR_IN_CLAUSE = new GroovyElementType("IN clause");

  GroovyElementType FOR_TRADITIONAL_CLAUSE = new GroovyElementType("Traditional clause");
  GroovyElementType TRY_BLOCK_STATEMENT = new GroovyElementType("try block statement");
  GroovyElementType CATCH_CLAUSE = new GroovyElementType("catch clause");
  GroovyElementType FINALLY_CLAUSE = new GroovyElementType("finally clause");
  GroovyElementType SYNCHRONIZED_STATEMENT = new GroovyElementType("synchronized block statement");
  GroovyElementType CLASS_INITIALIZER = new GroovyElementType("static compound statement");

  GroovyElementType VARIABLE_DEFINITION_ERROR = new GroovyElementType("variable definitions with errors");
  GroovyElementType VARIABLE_DEFINITION = new GroovyElementType("variable definitions");
  GroovyElementType MULTIPLE_VARIABLE_DEFINITION = new GroovyElementType("multivariable definition");
  GroovyElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  GroovyElementType TUPLE_EXPRESSION = new GroovyElementType("tuple expression");


  GroovyElementType TUPLE_ERROR = new GroovyElementType("tuple with error");

  GroovyElementType VARIABLE = new GroovyElementType("assigned variable");

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = new GrModifierListElementType("modifier list");

  GroovyElementType BALANCED_BRACKETS = new GroovyElementType("balanced brackets"); //node

  //types
  GroovyElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  TokenSet BLOCK_SET = TokenSet.create(CLOSABLE_BLOCK,
          BLOCK_STATEMENT,
          OPEN_BLOCK,
          ENUM_BODY,
          CLASS_BODY);

}