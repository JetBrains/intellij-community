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

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes {

  /*
  Stub elements
   */
  GrStubElementType<GrTypeDefinitionStub, GrClassDefinition> CLASS_DEFINITION = CLASS_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrInterfaceDefinition> INTERFACE_DEFINITION = INTERFACE_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrEnumTypeDefinition> ENUM_DEFINITION = ENUM_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrAnnotationTypeDefinition> ANNOTATION_DEFINITION = ANNOTATION_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrAnonymousClassDefinition> ANONYMOUS_CLASS_DEFINITION = ANONYMOUS_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrTraitTypeDefinition> TRAIT_DEFINITION = TRAIT_TYPE_DEFINITION;
  GrStubElementType<GrTypeDefinitionStub, GrEnumConstantInitializer> ENUM_CONSTANT_INITIALIZER = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANT_INITIALIZER;

  GrStubElementType<GrFieldStub, GrEnumConstant> ENUM_CONSTANT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANT;
  GrStubElementType<GrFieldStub, GrField> FIELD = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FIELD;
  GrMethodElementType METHOD_DEFINITION = METHOD;
  GrStubElementType<GrMethodStub, GrMethod> ANNOTATION_METHOD = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_METHOD;

  GrReferenceListElementType<GrImplementsClause> IMPLEMENTS_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPLEMENTS_CLAUSE;
  GrReferenceListElementType<GrExtendsClause> EXTENDS_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EXTENDS_CLAUSE;

  IElementType NONE = new GroovyElementType("no token"); //not a node

  // Indicates the wrongway of parsing
  IElementType WRONGWAY = new GroovyElementType("Wrong way!");
  GroovyElementType LITERAL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LITERAL;
  //Packaging
  GrPackageDefinitionElementType PACKAGE_DEFINITION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PACKAGE_DEFINITION;

  GrCodeBlockElementType CLOSABLE_BLOCK = CLOSURE;
  GrCodeBlockElementType OPEN_BLOCK = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.OPEN_BLOCK;
  GrCodeBlockElementType CONSTRUCTOR_BODY = CONSTRUCTOR_BLOCK;

  IElementType BLOCK_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_STATEMENT;

  EmptyStubElementType<GrEnumConstantList> ENUM_CONSTANTS = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANTS;
  GrImportStatementElementType IMPORT_STATEMENT = IMPORT;
  IElementType IMPORT_ALIAS = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPORT_ALIAS;

  //Branch statements
  IElementType BREAK_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BREAK_STATEMENT;
  IElementType CONTINUE_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CONTINUE_STATEMENT;

  IElementType RETURN_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RETURN_STATEMENT;
  IElementType ASSERT_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ASSERT_STATEMENT;
  IElementType THROW_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.THROW_STATEMENT;
  // Expression statements
  IElementType LABELED_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LABELED_STATEMENT;
  GroovyElementType CALL_EXPRESSION = APPLICATION_EXPRESSION;
  IElementType COMMAND_ARGUMENTS = APPLICATION_ARGUMENT_LIST;
  IElementType CONDITIONAL_EXPRESSION = TERNARY_EXPRESSION;
  IElementType ELVIS_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ELVIS_EXPRESSION;
  IElementType ASSIGNMENT_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ASSIGNMENT_EXPRESSION;
  IElementType TUPLE_ASSIGNMENT_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TUPLE_ASSIGNMENT_EXPRESSION;
  IElementType LOGICAL_OR_EXPRESSION = LOR_EXPRESSION;
  IElementType LOGICAL_AND_EXPRESSION = LAND_EXPRESSION;
  IElementType INCLUSIVE_OR_EXPRESSION = BOR_EXPRESSION;
  IElementType EXCLUSIVE_OR_EXPRESSION = XOR_EXPRESSION;
  IElementType AND_EXPRESSION = BAND_EXPRESSION;
  IElementType REGEX_FIND_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX_FIND_EXPRESSION;
  IElementType REGEX_MATCH_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX_MATCH_EXPRESSION;
  IElementType EQUALITY_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EQUALITY_EXPRESSION;
  IElementType RELATIONAL_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RELATIONAL_EXPRESSION;
  IElementType SHIFT_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SHIFT_EXPRESSION;
  IElementType RANGE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RANGE_EXPRESSION;
  IElementType COMPOSITE_LSHIFT_SIGN = LEFT_SHIFT_SIGN;
  IElementType COMPOSITE_RSHIFT_SIGN = RIGHT_SHIFT_SIGN;
  IElementType COMPOSITE_TRIPLE_SHIFT_SIGN = RIGHT_SHIFT_UNSIGNED_SIGN;
  IElementType MORE_OR_EQUALS_SIGN = new GroovyElementType(">=");
  IElementType ADDITIVE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ADDITIVE_EXPRESSION;
  IElementType MULTIPLICATIVE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.MULTIPLICATIVE_EXPRESSION;
  IElementType POWER_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.POWER_EXPRESSION;
  IElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  IElementType UNARY_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.UNARY_EXPRESSION;
  IElementType CAST_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CAST_EXPRESSION;
  IElementType SAFE_CAST_EXPRESSION = AS_EXPRESSION;
  IElementType INSTANCEOF_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.INSTANCEOF_EXPRESSION;
  IElementType PATH_PROPERTY_REFERENCE = PROPERTY_EXPRESSION;

  GroovyElementType PATH_METHOD_CALL = METHOD_CALL_EXPRESSION;

  IElementType PATH_INDEX_PROPERTY = INDEX_EXPRESSION;
  IElementType PARENTHESIZED_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PARENTHESIZED_EXPRESSION;

  // Arguments
  IElementType ARGUMENTS = ARGUMENT_LIST;
  GroovyElementType NAMED_ARGUMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NAMED_ARGUMENT;
  IElementType SPREAD_ARGUMENT = SPREAD_LIST_ARGUMENT;
  GroovyElementType ARGUMENT_LABEL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARGUMENT_LABEL;
  GroovyElementType REFERENCE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REFERENCE_EXPRESSION;

  IElementType NEW_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NEW_EXPRESSION;

  IElementType BUILT_IN_TYPE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BUILT_IN_TYPE_EXPRESSION;

  // Lists & maps
  IElementType LIST_OR_MAP = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LIST_OR_MAP;
  // Type Elements
  IElementType ARRAY_TYPE = ARRAY_TYPE_ELEMENT;
  IElementType BUILT_IN_TYPE = PRIMITIVE_TYPE_ELEMENT;
  IElementType DISJUNCTION_TYPE_ELEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT;

  // GStrings
  IElementType GSTRING = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.GSTRING;
  IElementType GSTRING_INJECTION = STRING_INJECTION;
  IElementType GSTRING_CONTENT = STRING_CONTENT;


  IElementType REGEX = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX;
  //types
  IElementType REFERENCE_ELEMENT = CODE_REFERENCE;
  IElementType ARRAY_DECLARATOR = ARRAY_DECLARATION;

  IElementType TYPE_ARGUMENTS = TYPE_ARGUMENT_LIST;
  IElementType TYPE_ARGUMENT = WILDCARD_TYPE_ELEMENT;
  EmptyStubElementType<GrTypeParameterList> TYPE_PARAMETER_LIST = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_PARAMETER_LIST;

  GrStubElementType<GrTypeParameterStub, GrTypeParameter> TYPE_PARAMETER = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_PARAMETER;

  IStubElementType<GrReferenceListStub, GrReferenceList> TYPE_PARAMETER_EXTENDS_BOUND_LIST = TYPE_PARAMETER_BOUNDS_LIST;

  IElementType DEFAULT_ANNOTATION_VALUE = new GroovyElementType("default annotation value");

  GrMethodElementType CONSTRUCTOR_DEFINITION = CONSTRUCTOR;

  IElementType EXPLICIT_CONSTRUCTOR = CONSTRUCTOR_CALL_EXPRESSION;

  GrReferenceListElementType<GrThrowsClause> THROW_CLAUSE = THROWS_CLAUSE;
  //annotation
  IElementType ANNOTATION_ARRAY_INITIALIZER = ANNOTATION_ARRAY_VALUE;
  GrAnnotationArgumentListElementType ANNOTATION_ARGUMENTS = ANNOTATION_ARGUMENT_LIST;
  GrNameValuePairElementType ANNOTATION_MEMBER_VALUE_PAIR = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR;

  GrStubElementType<GrAnnotationStub, GrAnnotation> ANNOTATION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION;
  //parameters
  EmptyStubElementType<GrParameterList> PARAMETERS_LIST = PARAMETER_LIST;

  GrStubElementType<GrParameterStub, GrParameter> PARAMETER = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PARAMETER;

  EmptyStubElementType<GrTypeDefinitionBody> CLASS_BODY = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_BODY;

  EmptyStubElementType<GrEnumDefinitionBody> ENUM_BODY = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_BODY;
  //statements
  IElementType IF_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IF_STATEMENT;
  IElementType FOR_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FOR_STATEMENT;
  IElementType WHILE_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.WHILE_STATEMENT;
  IElementType SWITCH_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SWITCH_STATEMENT;
  IElementType CASE_SECTION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CASE_SECTION;

  IElementType CASE_LABEL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CASE_LABEL;
  //for clauses
  IElementType FOR_IN_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FOR_IN_CLAUSE;
  IElementType FOR_TRADITIONAL_CLAUSE = TRADITIONAL_FOR_CLAUSE;
  IElementType TRY_BLOCK_STATEMENT = TRY_STATEMENT;
  IElementType CATCH_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CATCH_CLAUSE;
  IElementType FINALLY_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FINALLY_CLAUSE;
  IElementType SYNCHRONIZED_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SYNCHRONIZED_STATEMENT;
  IElementType CLASS_INITIALIZER = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_INITIALIZER;

  EmptyStubElementType<GrVariableDeclaration> VARIABLE_DEFINITION_ERROR = new GrErrorVariableDeclarationElementType("variable definitions with errors");
  GrVariableDeclarationElementType VARIABLE_DEFINITION = VARIABLE_DECLARATION;
  IElementType TUPLE_DECLARATION = new GroovyElementType("tuple declaration");
  IElementType TUPLE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TUPLE;

  GrVariableElementType VARIABLE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.VARIABLE;

  //modifiers
  GrStubElementType<GrModifierListStub, GrModifierList> MODIFIERS = MODIFIER_LIST;

  //types
  IElementType CLASS_TYPE_ELEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_TYPE_ELEMENT;

  IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);
}
