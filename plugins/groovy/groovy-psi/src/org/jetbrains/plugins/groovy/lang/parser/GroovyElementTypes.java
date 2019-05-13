// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrMethodElementType;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Dmitry.Krasilschikov, ilyas
 */
public interface GroovyElementTypes extends GroovyStubElementTypes, GroovyEmptyStubElementTypes {

  @Deprecated
  GrMethodElementType METHOD_DEFINITION = METHOD;

  GroovyElementType LITERAL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LITERAL;

  GrCodeBlockElementType CLOSABLE_BLOCK = CLOSURE;
  GrCodeBlockElementType OPEN_BLOCK = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.OPEN_BLOCK;
  GrCodeBlockElementType CONSTRUCTOR_BODY = CONSTRUCTOR_BLOCK;

  IElementType BLOCK_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_STATEMENT;

  IElementType ASSERT_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ASSERT_STATEMENT;
  // Expression statements
  IElementType LABELED_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LABELED_STATEMENT;
  @Deprecated GroovyElementType CALL_EXPRESSION = APPLICATION_EXPRESSION;
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
  IElementType ADDITIVE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ADDITIVE_EXPRESSION;
  IElementType MULTIPLICATIVE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.MULTIPLICATIVE_EXPRESSION;
  IElementType POWER_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.POWER_EXPRESSION;
  IElementType POWER_EXPRESSION_SIMPLE = new GroovyElementType("Simple power expression");
  IElementType PATH_PROPERTY_REFERENCE = PROPERTY_EXPRESSION;

  GroovyElementType PATH_METHOD_CALL = METHOD_CALL_EXPRESSION;

  IElementType PATH_INDEX_PROPERTY = INDEX_EXPRESSION;

  // Arguments
  IElementType ARGUMENTS = ARGUMENT_LIST;
  GroovyElementType NAMED_ARGUMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NAMED_ARGUMENT;
  GroovyElementType ARGUMENT_LABEL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARGUMENT_LABEL;
  GroovyElementType REFERENCE_EXPRESSION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REFERENCE_EXPRESSION;

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

  //statements
  IElementType IF_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IF_STATEMENT;
  IElementType SWITCH_STATEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SWITCH_STATEMENT;
  IElementType CASE_SECTION = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CASE_SECTION;

  IElementType CASE_LABEL = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CASE_LABEL;
  //for clauses
  IElementType FOR_IN_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FOR_IN_CLAUSE;
  IElementType FOR_TRADITIONAL_CLAUSE = TRADITIONAL_FOR_CLAUSE;
  IElementType CATCH_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CATCH_CLAUSE;
  IElementType FINALLY_CLAUSE = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FINALLY_CLAUSE;
  IElementType CLASS_INITIALIZER = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_INITIALIZER;

  IElementType CLASS_TYPE_ELEMENT = org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_TYPE_ELEMENT;
}
