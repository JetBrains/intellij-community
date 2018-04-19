// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.stubs.EmptyStubElementType;
import com.intellij.psi.stubs.IStubElementType;
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

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = new GrEnumConstantElementType();
  GrStubElementType<GrFieldStub, GrField> FIELD = new GrFieldElementType();
  GrMethodElementType METHOD_DEFINITION = new GrMethodElementType("method definition");
  GrStubElementType<GrMethodStub, GrMethod> ANNOTATION_METHOD = new GrAnnotationMethodElementType("annotation method");

  GrReferenceListElementType<GrImplementsClause> IMPLEMENTS_CLAUSE = new GrImplementsClauseElementType("implements clause");
  GrReferenceListElementType<GrExtendsClause> EXTENDS_CLAUSE = new GrExtendsClauseElementType("super class clause");

  GroovyElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  GroovyElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = new GroovyElementType("Literal");
  //Packaging
  GrPackageDefinitionElementType PACKAGE_DEFINITION = new GrPackageDefinitionElementType("Package definition");

  GrCodeBlockElementType CLOSABLE_BLOCK = new GrClosureElementType("Closable block");
  GrCodeBlockElementType OPEN_BLOCK = new GrBlockElementType("Open block");
  GrCodeBlockElementType CONSTRUCTOR_BODY = new GrConstructorBlockElementType("Constructor body");

  GroovyElementType BLOCK_STATEMENT = new GroovyElementType("Block statement");

  EmptyStubElementType<GrEnumConstantList> ENUM_CONSTANTS = new GrEnumConstantListElementType("Enumeration constants");
  GrImportStatementElementType IMPORT_STATEMENT = new GrImportStatementElementType("Import statement");
  GroovyElementType IMPORT_ALIAS = new GroovyElementType("IMPORT_ALIAS");

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
  GroovyElementType TUPLE_ASSIGNMENT_EXPRESSION = new GroovyElementType("Tuple assignment expression");
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
  GroovyElementType COMPOSITE_LSHIFT_SIGN = new GroovyElementType("<<");
  GroovyElementType COMPOSITE_RSHIFT_SIGN = new GroovyElementType(">>");
  GroovyElementType COMPOSITE_TRIPLE_SHIFT_SIGN = new GroovyElementType(">>>");
  GroovyElementType MORE_OR_EQUALS_SIGN = new GroovyElementType(">=");
  GroovyElementType ADDITIVE_EXPRESSION = new GroovyElementType("Additive expression");
  GroovyElementType MULTIPLICATIVE_EXPRESSION = new GroovyElementType("Multiplicative expression");
  GroovyElementType POWER_EXPRESSION = new GroovyElementType("Power expression");
  GroovyElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  GroovyElementType UNARY_EXPRESSION = new GroovyElementType("Unary expression");
  GroovyElementType CAST_EXPRESSION = new GroovyElementType("cast expression");
  GroovyElementType SAFE_CAST_EXPRESSION = new GroovyElementType("safe cast expression");
  GroovyElementType INSTANCEOF_EXPRESSION = new GroovyElementType("instanceof expression");
  GroovyElementType PATH_PROPERTY_REFERENCE = new GroovyElementType("Property reference");

  GroovyElementType PATH_METHOD_CALL = new GroovyElementType("Method call");

  GroovyElementType PATH_INDEX_PROPERTY = new GroovyElementType("Index property");
  GroovyElementType PARENTHESIZED_EXPRESSION = new GroovyElementType("Parenthesized expression");

  // Arguments
  GroovyElementType ARGUMENTS = new GroovyElementType("Arguments");
  GroovyElementType NAMED_ARGUMENT = new GroovyElementType("Compound argument");
  GroovyElementType SPREAD_ARGUMENT = new GroovyElementType("Spread argument");
  GroovyElementType ARGUMENT_LABEL = new GroovyElementType("Argument label");
  GroovyElementType REFERENCE_EXPRESSION = new GroovyElementType("Reference expressions");

  GroovyElementType NEW_EXPRESSION = new GroovyElementType("New expressions");

  GroovyElementType BUILT_IN_TYPE_EXPRESSION = new GroovyElementType("Built in type expression");

  // Lists & maps
  GroovyElementType LIST_OR_MAP = new GroovyElementType("Generalized list");
  // Type Elements
  GroovyElementType ARRAY_TYPE = new GroovyElementType("Array type");

  GroovyElementType BUILT_IN_TYPE = new GroovyElementType("Built in type");

  GroovyElementType DISJUNCTION_TYPE_ELEMENT = new GroovyElementType("Disjunction type element");

  // GStrings
  GroovyElementType GSTRING = new GroovyElementType("GString");
  GroovyElementType GSTRING_INJECTION =new GroovyElementType("Gstring injection");
  GroovyElementType GSTRING_CONTENT = new GroovyElementType("GString content element");


  GroovyElementType REGEX = new GroovyElementType("Regular expression");
  //types
  GroovyElementType REFERENCE_ELEMENT = new GroovyElementType("reference element");
  GroovyElementType ARRAY_DECLARATOR = new GroovyElementType("array declarator");

  GroovyElementType TYPE_ARGUMENTS = new GroovyElementType("type arguments", true);
  GroovyElementType TYPE_ARGUMENT = new GroovyElementType("type argument");
  EmptyStubElementType<GrTypeParameterList> TYPE_PARAMETER_LIST = new GrTypeParameterListElementType("type parameter list");

  GrStubElementType<GrTypeParameterStub, GrTypeParameter> TYPE_PARAMETER = new GrTypeParameterElementType("type parameter");

  IStubElementType<GrReferenceListStub, GrReferenceList> TYPE_PARAMETER_EXTENDS_BOUND_LIST = new GrTypeParameterBoundsElementType();

  GroovyElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GrMethodElementType CONSTRUCTOR_DEFINITION = new GrConstructorElementType("constructor definition");

  GroovyElementType EXPLICIT_CONSTRUCTOR = new GroovyElementType("explicit constructor invokation");

  GrReferenceListElementType<GrThrowsClause> THROW_CLAUSE = new GrThrowsClauseElementType("throw clause");
  //annotation
  GroovyElementType ANNOTATION_ARRAY_INITIALIZER = new GroovyElementType("annotation array initializer");
  GrAnnotationArgumentListElementType ANNOTATION_ARGUMENTS = new GrAnnotationArgumentListElementType();
  GrNameValuePairElementType ANNOTATION_MEMBER_VALUE_PAIR = new GrNameValuePairElementType();

  GrStubElementType<GrAnnotationStub, GrAnnotation> ANNOTATION = new GrAnnotationElementType("annotation");
  //parameters
  EmptyStubElementType<GrParameterList> PARAMETERS_LIST = new GrParameterListElementType("parameters list");

  GrStubElementType<GrParameterStub, GrParameter> PARAMETER = new GrParameterElementType("parameter");

  EmptyStubElementType<GrTypeDefinitionBody> CLASS_BODY = new GrTypeDefinitionBodyElementType("class block");

  EmptyStubElementType<GrEnumDefinitionBody> ENUM_BODY = new GrEnumDefinitionBodyElementType("enum block");
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

  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION_ERROR = new GrErrorVariableDeclarationElementType("variable definitions with errors");
  GrVariableDeclarationElementType VARIABLE_DEFINITION = new GrVariableDeclarationElementType();
  GroovyElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  GroovyElementType TUPLE = new GroovyElementType("tuple");

  GrVariableElementType VARIABLE = new GrVariableElementType();

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = new GrModifierListElementType("modifier list");

  //types
  GroovyElementType CLASS_TYPE_ELEMENT = new GroovyElementType("class type element"); //node

  IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);
}
