// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.stubs.EmptyStubElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.GrErrorVariableDeclarationElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.*;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes {

  /*
  Stub elements
   */
  GrStubElementType<GrTypeDefinitionStub, GrClassDefinition> CLASS_DEFINITION = new GrClassDefinitionElementType("class definition");
  GrStubElementType<GrTypeDefinitionStub, GrInterfaceDefinition> INTERFACE_DEFINITION = new GrInterfaceDefinitionElementType("interface definition");
  GrStubElementType<GrTypeDefinitionStub, GrEnumTypeDefinition> ENUM_DEFINITION = new GrEnumDefinitionElementType("enumeration definition");
  GrStubElementType<GrTypeDefinitionStub, GrAnnotationTypeDefinition> ANNOTATION_DEFINITION = new GrAnnotationDefinitionElementType("annotation definition");
  GrStubElementType<GrTypeDefinitionStub, GrAnonymousClassDefinition> ANONYMOUS_CLASS_DEFINITION = new GrAnonymousElementType("Anonymous class");
  GrStubElementType<GrTypeDefinitionStub, GrTraitTypeDefinition> TRAIT_DEFINITION = new GrTraitElementType("Trait definition");
  GrStubElementType<GrTypeDefinitionStub, GrEnumConstantInitializer> ENUM_CONSTANT_INITIALIZER = new GrEnumConstantInitializerElementType("Enum constant initializer");

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = new GrEnumConstantElementType("Enumeration constant");
  GrStubElementType<GrFieldStub, GrField> FIELD = new GrFieldElementType("field");
  GrMethodElementType METHOD_DEFINITION = new GrMethodElementType("method definition");
  GrStubElementType<GrMethodStub, GrMethod> ANNOTATION_METHOD = new GrAnnotationMethodElementType("annotation method");

  GrReferenceListElementType<GrImplementsClause> IMPLEMENTS_CLAUSE = new GrImplementsClauseElementType("implements clause");
  GrReferenceListElementType<GrExtendsClause> EXTENDS_CLAUSE = new GrExtendsClauseElementType("super class clause");

  IElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  IElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = new GroovyElementType("Literal");
  //Packaging
  GrPackageDefinitionElementType PACKAGE_DEFINITION = new GrPackageDefinitionElementType("Package definition");

  GrCodeBlockElementType CLOSABLE_BLOCK = new GrClosureElementType("Closable block");
  GrCodeBlockElementType OPEN_BLOCK = new GrBlockElementType("Open block");
  GrCodeBlockElementType CONSTRUCTOR_BODY = new GrConstructorBlockElementType("Constructor body");

  IElementType BLOCK_STATEMENT = new GroovyElementType("Block statement");

  EmptyStubElementType<GrEnumConstantList> ENUM_CONSTANTS = new GrEnumConstantListElementType("Enumeration constants");
  GrImportStatementElementType IMPORT_STATEMENT = new GrImportStatementElementType("Import statement");
  IElementType IMPORT_ALIAS = new GroovyElementType("IMPORT_ALIAS");

  //Branch statements
  IElementType BREAK_STATEMENT = new GroovyElementType("Break statement");
  IElementType CONTINUE_STATEMENT = new GroovyElementType("Continue statement");

  IElementType RETURN_STATEMENT = new GroovyElementType("Return statement");
  IElementType ASSERT_STATEMENT = new GroovyElementType("Assert statement");
  IElementType THROW_STATEMENT = new GroovyElementType("Throw statement");
  // Expression statements
  IElementType LABELED_STATEMENT = new GroovyElementType("Labeled statement");
  GroovyElementType CALL_EXPRESSION = new GroovyElementType("Expression statement");
  IElementType COMMAND_ARGUMENTS = new GroovyElementType("Command argument");
  IElementType CONDITIONAL_EXPRESSION = new GroovyElementType("Conditional expression");
  IElementType ELVIS_EXPRESSION = new GroovyElementType("Elvis expression");
  IElementType ASSIGNMENT_EXPRESSION = new GroovyElementType("Assignment expression");
  IElementType TUPLE_ASSIGNMENT_EXPRESSION = new GroovyElementType("Tuple assignment expression");
  IElementType LOGICAL_OR_EXPRESSION = new GroovyElementType("Logical OR expression");
  IElementType LOGICAL_AND_EXPRESSION = new GroovyElementType("Logical AND expression");
  IElementType INCLUSIVE_OR_EXPRESSION = new GroovyElementType("Inclusive OR expression");
  IElementType EXCLUSIVE_OR_EXPRESSION = new GroovyElementType("Exclusive OR expression");
  IElementType AND_EXPRESSION = new GroovyElementType("AND expression");
  IElementType REGEX_FIND_EXPRESSION = new GroovyElementType("Regex Find expression");
  IElementType REGEX_MATCH_EXPRESSION = new GroovyElementType("Regex Match expression");
  IElementType EQUALITY_EXPRESSION = new GroovyElementType("Equality expression");
  IElementType RELATIONAL_EXPRESSION = new GroovyElementType("Relational expression");
  IElementType SHIFT_EXPRESSION = new GroovyElementType("Shift expression");
  IElementType RANGE_EXPRESSION = new GroovyElementType("Range expression");
  IElementType COMPOSITE_LSHIFT_SIGN = new GroovyElementType("LEFT_SHIFT_SIGN");
  IElementType COMPOSITE_RSHIFT_SIGN = new GroovyElementType("RIGHT_SHIFT_SIGN");
  IElementType COMPOSITE_TRIPLE_SHIFT_SIGN = new GroovyElementType("RIGHT_SHIFT_UNSIGNED_SIGN");
  IElementType MORE_OR_EQUALS_SIGN = new GroovyElementType(">=");
  IElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  IElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  IElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  IElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  IElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  IElementType CAST_EXPRESSION = new GroovyElementType("cast expression");
  IElementType SAFE_CAST_EXPRESSION = new GroovyElementType("safe cast expression");
  IElementType INSTANCEOF_EXPRESSION = new GroovyElementType("instanceof expression");
  IElementType PATH_PROPERTY_REFERENCE = new GroovyElementType("Property reference");

  GroovyElementType PATH_METHOD_CALL = new GroovyElementType("Method call");

  IElementType PATH_INDEX_PROPERTY = new GroovyElementType("Index property");
  IElementType PARENTHESIZED_EXPRESSION = new GroovyElementType("Parenthesized expression");

  // Arguments
  IElementType ARGUMENTS = new GroovyElementType("Arguments");
  GroovyElementType NAMED_ARGUMENT = new GroovyElementType("Compound argument");
  IElementType SPREAD_ARGUMENT = new GroovyElementType("Spread argument");
  GroovyElementType ARGUMENT_LABEL = new GroovyElementType("Argument label");
  GroovyElementType REFERENCE_EXPRESSION = new GroovyElementType("Reference expressions");

  IElementType NEW_EXPRESSION = new GroovyElementType("New expressions");

  IElementType BUILT_IN_TYPE_EXPRESSION = new GroovyElementType("Built in type expression");

  // Lists & maps
  IElementType LIST_OR_MAP = new GroovyElementType("Generalized list");
  // Type Elements
  IElementType ARRAY_TYPE = new GroovyElementType("Array type");

  IElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  IElementType DISJUNCTION_TYPE_ELEMENT = new GroovyElementType("Disjunction type element");

  // GStrings
  IElementType GSTRING = new GroovyElementType("GString");
  IElementType GSTRING_INJECTION =new GroovyElementType("Gstring injection");
  IElementType GSTRING_CONTENT = new GroovyElementType("STRING_CONTENT");


  IElementType REGEX = new GroovyElementType("Regular expression");
  //types
  IElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  IElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");

  IElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments", true);
  IElementType TYPE_ARGUMENT = new GroovyElementType("type argument");
  EmptyStubElementType<GrTypeParameterList> TYPE_PARAMETER_LIST = new GrTypeParameterListElementType("type parameter list");

  GrStubElementType<GrTypeParameterStub, GrTypeParameter> TYPE_PARAMETER = new GrTypeParameterElementType("type parameter");

  IStubElementType<GrReferenceListStub, GrReferenceList> TYPE_PARAMETER_EXTENDS_BOUND_LIST = new GrTypeParameterBoundsElementType("type extends list");

  IElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GrMethodElementType CONSTRUCTOR_DEFINITION = new GrConstructorElementType("constructor definition");

  IElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  GrReferenceListElementType<GrThrowsClause> THROW_CLAUSE = new GrThrowsClauseElementType("throw clause");
  //annotation
  IElementType ANNOTATION_ARRAY_INITIALIZER = new GroovyElementType("annotation array initializer");
  GrAnnotationArgumentListElementType ANNOTATION_ARGUMENTS = new GrAnnotationArgumentListElementType("annotation arguments");
  GrNameValuePairElementType ANNOTATION_MEMBER_VALUE_PAIR = new GrNameValuePairElementType("Annotation name value pair");

  GrStubElementType<GrAnnotationStub, GrAnnotation> ANNOTATION = new GrAnnotationElementType("annotation");
  //parameters
  EmptyStubElementType<GrParameterList> PARAMETERS_LIST = new GrParameterListElementType("parameters list");

  GrStubElementType<GrParameterStub, GrParameter> PARAMETER = new GrParameterElementType("parameter");

  EmptyStubElementType<GrTypeDefinitionBody> CLASS_BODY = new GrTypeDefinitionBodyElementType("class block");

  EmptyStubElementType<GrEnumDefinitionBody> ENUM_BODY = new GrEnumDefinitionBodyElementType("enum block");
  //statements
  IElementType IF_STATEMENT = new GroovyElementType("if statement");
  IElementType FOR_STATEMENT = new GroovyElementType("for statement");

  IElementType WHILE_STATEMENT = new GroovyElementType("while statement");
  // switch dtatement
  IElementType SWITCH_STATEMENT = new GroovyElementType("switch statement");
  IElementType CASE_SECTION = new GroovyElementType("case block");

  IElementType CASE_LABEL = new GroovyElementType("case label");
  //for clauses
  IElementType FOR_IN_CLAUSE = new GroovyElementType("IN clause");

  IElementType FOR_TRADITIONAL_CLAUSE = new GroovyElementType("Traditional clause");
  IElementType TRY_BLOCK_STATEMENT = new GroovyElementType("try block statement");
  IElementType CATCH_CLAUSE = new GroovyElementType("catch clause");
  IElementType FINALLY_CLAUSE = new GroovyElementType("finally clause");
  IElementType SYNCHRONIZED_STATEMENT = new GroovyElementType("synchronized block statement");
  IElementType CLASS_INITIALIZER = new GroovyElementType("static compound statement");

  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION_ERROR = new GrErrorVariableDeclarationElementType("variable definitions with errors");
  GrVariableDeclarationElementType VARIABLE_DEFINITION = new GrVariableDeclarationElementType("variable definitions");
  IElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  IElementType TUPLE = new GroovyElementType("tuple");

  GrVariableElementType VARIABLE = new GrVariableElementType("assigned variable");

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = new GrModifierListElementType("modifier list");

  //types
  IElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);
}
