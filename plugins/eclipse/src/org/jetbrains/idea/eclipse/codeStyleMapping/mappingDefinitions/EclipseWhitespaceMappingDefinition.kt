// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.alsoSet
import org.jetbrains.idea.eclipse.codeStyleMapping.util.doNotImport
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertInsert

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addWhitespaceMapping() {
  // region Declarations
  // region Classes
  "insert_space_before_opening_brace_in_type_declaration" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .convertInsert()
  "insert_space_before_opening_brace_in_anonymous_type_declaration" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_superinterfaces" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_superinterfaces" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Fields
  "insert_space_before_comma_in_multiple_field_declarations" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_multiple_field_declarations" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Local variables
  "insert_space_before_comma_in_multiple_local_declarations" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_multiple_local_declarations" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Constructors
  "insert_space_before_opening_paren_in_constructor_declaration" mapTo
    field(common::SPACE_BEFORE_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_after_opening_paren_in_constructor_declaration" mapTo
    field(common::SPACE_WITHIN_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_paren_in_constructor_declaration" mapTo
    field(common::SPACE_WITHIN_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_parens_in_constructor_declaration" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_brace_in_constructor_declaration" mapTo
    field(common::SPACE_BEFORE_METHOD_LBRACE)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_constructor_declaration_parameters" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_constructor_declaration_parameters" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_constructor_declaration_throws" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_constructor_declaration_throws" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Methods
  "insert_space_before_opening_paren_in_method_declaration" mapTo
    field(common::SPACE_BEFORE_METHOD_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_method_declaration" mapTo
    field(common::SPACE_WITHIN_METHOD_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_method_declaration" mapTo
    field(common::SPACE_WITHIN_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_parens_in_method_declaration" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_PARENTHESES)
      .convertInsert()
  "insert_space_before_opening_brace_in_method_declaration" mapTo
    field(common::SPACE_BEFORE_METHOD_LBRACE)
      .convertInsert()
  "insert_space_before_comma_in_method_declaration_parameters" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .convertInsert()
  "insert_space_after_comma_in_method_declaration_parameters" mapTo
    field(common::SPACE_AFTER_COMMA)
      .convertInsert()
  "insert_space_before_ellipsis" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_ellipsis" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_comma_in_method_declaration_throws" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_method_declaration_throws" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Labels
  "insert_space_before_colon_in_labeled_statement" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_colon_in_labeled_statement" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region Annotations
  "insert_space_after_at_in_annotation" mapTo
    const(false)
      .convertInsert()
  "insert_space_before_opening_paren_in_annotation" mapTo
    field(common::SPACE_BEFORE_ANOTATION_PARAMETER_LIST)
      .convertInsert()
  "insert_space_after_opening_paren_in_annotation" mapTo
    field(common::SPACE_WITHIN_ANNOTATION_PARENTHESES)
      .convertInsert()
  "insert_space_before_comma_in_annotation" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_annotation" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_closing_paren_in_annotation" mapTo
    field(common::SPACE_WITHIN_ANNOTATION_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Enum types
  "insert_space_before_opening_brace_in_enum_declaration" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_enum_declarations" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_enum_declarations" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_paren_in_enum_constant" mapTo
    field(common::SPACE_BEFORE_METHOD_CALL_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_after_opening_paren_in_enum_constant" mapTo
    field(common::SPACE_WITHIN_METHOD_CALL_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_parens_in_enum_constant" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_enum_constant_arguments" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_enum_constant_arguments" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_paren_in_enum_constant" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_brace_in_enum_constant" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Annotation types
  "insert_space_before_at_in_annotation_type_declaration" mapTo
    const(true)
      .convertInsert()
  "insert_space_after_at_in_annotation_type_declaration" mapTo
    const(false)
      .convertInsert()
  "insert_space_before_opening_brace_in_annotation_type_declaration" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_paren_in_annotation_type_member_declaration" mapTo
    field(common::SPACE_BEFORE_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_parens_in_annotation_type_member_declaration" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Records
  "insert_space_before_opening_paren_in_record_declaration" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_opening_paren_in_record_declaration" mapTo
    field(custom::SPACE_WITHIN_RECORD_HEADER)
      .convertInsert()
  "insert_space_before_comma_in_record_components" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_record_components" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_paren_in_record_declaration" mapTo
    field(custom::SPACE_WITHIN_RECORD_HEADER)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_brace_in_record_declaration" mapTo
    field(common::SPACE_BEFORE_CLASS_LBRACE)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_brace_in_record_constructor" mapTo
    field(common::SPACE_BEFORE_METHOD_LBRACE)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Lambda
  "insert_space_before_lambda_arrow" mapTo
    field(common::SPACE_AROUND_LAMBDA_ARROW)
      .convertInsert()
  "insert_space_after_lambda_arrow" mapTo
    field(common::SPACE_AROUND_LAMBDA_ARROW)
      .doNotImport()
      .convertInsert()
  // endregion
  // endregion
  // region Control statements
  // region Blocks
  "insert_space_before_opening_brace_in_block" mapTo
    field(common::SPACE_BEFORE_IF_LBRACE)
      .alsoSet(common::SPACE_BEFORE_ELSE_LBRACE,
               common::SPACE_BEFORE_FOR_LBRACE,
               common::SPACE_BEFORE_WHILE_LBRACE,
               common::SPACE_BEFORE_DO_LBRACE,
               common::SPACE_BEFORE_TRY_LBRACE,
               common::SPACE_BEFORE_CATCH_LBRACE,
               common::SPACE_BEFORE_FINALLY_LBRACE,
               common::SPACE_BEFORE_SYNCHRONIZED_LBRACE)
      .convertInsert()
  "insert_space_after_closing_brace_in_block" mapTo
    field(common::SPACE_BEFORE_ELSE_KEYWORD)
      .alsoSet(common::SPACE_BEFORE_CATCH_KEYWORD,
               common::SPACE_BEFORE_WHILE_KEYWORD,
               common::SPACE_BEFORE_FINALLY_KEYWORD)
      .convertInsert()
  // endregion
  // region 'if else'
  "insert_space_before_opening_paren_in_if" mapTo
    field(common::SPACE_BEFORE_IF_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_if" mapTo
    field(common::SPACE_WITHIN_IF_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_if" mapTo
    field(common::SPACE_WITHIN_IF_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'for'
  "insert_space_before_opening_paren_in_for" mapTo
    field(common::SPACE_BEFORE_FOR_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_for" mapTo
    field(common::SPACE_WITHIN_FOR_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_for" mapTo
    field(common::SPACE_WITHIN_FOR_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_for_inits" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_for_inits" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_comma_in_for_increments" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_for_increments" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_semicolon_in_for" mapTo
    field(common::SPACE_BEFORE_SEMICOLON)
      .convertInsert()
  "insert_space_after_semicolon_in_for" mapTo
    field(common::SPACE_AFTER_SEMICOLON)
      .convertInsert()
  "insert_space_before_colon_in_for" mapTo
    field(custom::SPACE_BEFORE_COLON_IN_FOREACH)
      .convertInsert()
  "insert_space_after_colon_in_for" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region 'switch'
  "insert_space_before_colon_in_case" mapTo
    const(false)
      .convertInsert()
  "insert_space_before_colon_in_default" mapTo
    const(false)
      .convertInsert()
  "insert_space_before_arrow_in_switch_case" mapTo
    const(true)
      .convertInsert()
  "insert_space_after_arrow_in_switch_case" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_arrow_in_switch_default" mapTo
    const(true)
      .convertInsert()
  "insert_space_after_arrow_in_switch_default" mapTo
    const(true)
      .convertInsert()
  "insert_space_after_colon_in_case" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_comma_in_switch_case_expressions" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_switch_case_expressions" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_paren_in_switch" mapTo
    field(common::SPACE_BEFORE_SWITCH_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_switch" mapTo
    field(common::SPACE_WITHIN_SWITCH_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_switch" mapTo
    field(common::SPACE_WITHIN_SWITCH_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_opening_brace_in_switch" mapTo
    field(common::SPACE_BEFORE_SWITCH_LBRACE)
      .convertInsert()
  // endregion
  // region 'while' and 'do while'
  "insert_space_before_opening_paren_in_while" mapTo
    field(common::SPACE_BEFORE_WHILE_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_while" mapTo
    field(common::SPACE_WITHIN_WHILE_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_while" mapTo
    field(common::SPACE_WITHIN_WHILE_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'synchronized'
  "insert_space_before_opening_paren_in_synchronized" mapTo
    field(common::SPACE_BEFORE_SYNCHRONIZED_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_synchronized" mapTo
    field(common::SPACE_WITHIN_SYNCHRONIZED_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_synchronized" mapTo
    field(common::SPACE_WITHIN_SYNCHRONIZED_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'try-with-resources'
  "insert_space_before_opening_paren_in_try" mapTo
    field(common::SPACE_BEFORE_TRY_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_try" mapTo
    field(common::SPACE_WITHIN_TRY_PARENTHESES)
      .convertInsert()
  "insert_space_before_semicolon_in_try_resources" mapTo
    field(common::SPACE_BEFORE_SEMICOLON)
      .doNotImport()
      .convertInsert()
  "insert_space_after_semicolon_in_try_resources" mapTo
    field(common::SPACE_AFTER_SEMICOLON)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_paren_in_try" mapTo
    field(common::SPACE_WITHIN_TRY_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'catch'
  "insert_space_before_opening_paren_in_catch" mapTo
    field(common::SPACE_BEFORE_CATCH_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_catch" mapTo
    field(common::SPACE_WITHIN_CATCH_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_catch" mapTo
    field(common::SPACE_WITHIN_CATCH_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'assert'
  "insert_space_before_colon_in_assert" mapTo
    field(common::SPACE_BEFORE_COLON)
      .doNotImport()
      .convertInsert()
  "insert_space_after_colon_in_assert" mapTo
    field(common::SPACE_AFTER_COLON)
      .doNotImport()
      .convertInsert()
  // endregion
  // region 'return'
  "insert_space_before_parenthesized_expression_in_return" mapTo
    const(true)
      .convertInsert()
  // endregion
  // region 'throw'
  "insert_space_before_parenthesized_expression_in_throw" mapTo
    const(true)
      .convertInsert()
  // endregion
  "insert_space_before_semicolon" mapTo
    const(false)
      .convertInsert()
  // endregion
  // region Expressions
  // region Function invocations
  "insert_space_before_opening_paren_in_method_invocation" mapTo
    field(common::SPACE_BEFORE_METHOD_CALL_PARENTHESES)
      .convertInsert()
  "insert_space_after_opening_paren_in_method_invocation" mapTo
    field(common::SPACE_WITHIN_METHOD_CALL_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_method_invocation" mapTo
    field(common::SPACE_WITHIN_METHOD_CALL_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_parens_in_method_invocation" mapTo
    field(common::SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES)
      .convertInsert()
  "insert_space_before_comma_in_method_invocation_arguments" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .convertInsert()
  "insert_space_after_comma_in_method_invocation_arguments" mapTo
    field(common::SPACE_AFTER_COMMA)
      .convertInsert()
  "insert_space_before_comma_in_allocation_expression" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_allocation_expression" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_explicitconstructorcall_arguments" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_explicitconstructorcall_arguments" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Unary operators
  "insert_space_before_postfix_operator" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_postfix_operator" mapTo
    const(false)
      .convertInsert()
  "insert_space_before_prefix_operator" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_prefix_operator" mapTo
    field(common::SPACE_AROUND_UNARY_OPERATOR)
      .doNotImport()
      .convertInsert()
  "insert_space_before_unary_operator" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_unary_operator" mapTo
    field(common::SPACE_AROUND_UNARY_OPERATOR)
      .doNotImport()
      .convertInsert()
  "insert_space_after_not_operator" mapTo
    field(common::SPACE_AROUND_UNARY_OPERATOR)
      .convertInsert()
  // endregion
  // region Binary operators
  "insert_space_before_multiplicative_operator" mapTo
    field(common::SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
      .convertInsert()
  "insert_space_after_multiplicative_operator" mapTo
    field(common::SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_additive_operator" mapTo
    field(common::SPACE_AROUND_ADDITIVE_OPERATORS)
      .convertInsert()
  "insert_space_after_additive_operator" mapTo
    field(common::SPACE_AROUND_ADDITIVE_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_string_concatenation" mapTo
    field(common::SPACE_AROUND_ADDITIVE_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_after_string_concatenation" mapTo
    field(common::SPACE_AROUND_ADDITIVE_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_shift_operator" mapTo
    field(common::SPACE_AROUND_SHIFT_OPERATORS)
      .convertInsert()
  "insert_space_after_shift_operator" mapTo
    field(common::SPACE_AROUND_SHIFT_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_relational_operator" mapTo
    field(common::SPACE_AROUND_RELATIONAL_OPERATORS)
      .alsoSet(common::SPACE_AROUND_EQUALITY_OPERATORS)
      .convertInsert()
  "insert_space_after_relational_operator" mapTo
    field(common::SPACE_AROUND_RELATIONAL_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_bitwise_operator" mapTo
    field(common::SPACE_AROUND_BITWISE_OPERATORS)
      .convertInsert()
  "insert_space_after_bitwise_operator" mapTo
    field(common::SPACE_AROUND_BITWISE_OPERATORS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_logical_operator" mapTo
    field(common::SPACE_AROUND_LOGICAL_OPERATORS)
      .convertInsert()
  "insert_space_after_logical_operator" mapTo
    field(common::SPACE_AROUND_LOGICAL_OPERATORS)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Conditionals
  "insert_space_before_question_in_conditional" mapTo
    field(common::SPACE_BEFORE_QUEST)
      .convertInsert()
  "insert_space_after_question_in_conditional" mapTo
    field(common::SPACE_AFTER_QUEST)
      .convertInsert()
  "insert_space_before_colon_in_conditional" mapTo
    field(common::SPACE_BEFORE_COLON)
      .convertInsert()
  "insert_space_after_colon_in_conditional" mapTo
    field(common::SPACE_AFTER_COLON)
      .convertInsert()
  // endregion
  // region Assignments
  "insert_space_before_assignment_operator" mapTo
    field(common::SPACE_AROUND_ASSIGNMENT_OPERATORS)
      .convertInsert()
  "insert_space_after_assignment_operator" mapTo
    field(common::SPACE_AROUND_ASSIGNMENT_OPERATORS)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Parenthesized expressions
  "insert_space_before_opening_paren_in_parenthesized_expression" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_opening_paren_in_parenthesized_expression" mapTo
    field(common::SPACE_WITHIN_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_parenthesized_expression" mapTo
    field(common::SPACE_WITHIN_PARENTHESES)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Type casts
  "insert_space_after_opening_paren_in_cast" mapTo
    field(common::SPACE_WITHIN_CAST_PARENTHESES)
      .convertInsert()
  "insert_space_before_closing_paren_in_cast" mapTo
    field(common::SPACE_WITHIN_CAST_PARENTHESES)
      .doNotImport()
      .convertInsert()
  "insert_space_after_closing_paren_in_cast" mapTo
    field(common::SPACE_AFTER_TYPE_CAST)
      .convertInsert()
  // endregion
  // endregion
  // region Arrays
  // region Array declarations
  "insert_space_before_opening_bracket_in_array_type_reference" mapTo
    const(false)
      .convertInsert()
  "insert_space_between_brackets_in_array_type_reference" mapTo
    const(false)
      .convertInsert()
  // endregion
  // region Array allocation
  "insert_space_before_opening_bracket_in_array_allocation_expression" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_opening_bracket_in_array_allocation_expression" mapTo
    field(common::SPACE_WITHIN_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_bracket_in_array_allocation_expression" mapTo
    field(common::SPACE_WITHIN_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_brackets_in_array_allocation_expression" mapTo
    const(false)
      .convertInsert()
  // endregion
  // region Array initializers
  "insert_space_before_opening_brace_in_array_initializer" mapTo
    field(common::SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE)
      .convertInsert()
  "insert_space_after_opening_brace_in_array_initializer" mapTo
    field(common::SPACE_WITHIN_ARRAY_INITIALIZER_BRACES)
      .convertInsert()
  "insert_space_before_closing_brace_in_array_initializer" mapTo
    field(common::SPACE_WITHIN_ARRAY_INITIALIZER_BRACES)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_array_initializer" mapTo
    field(common::SPACE_BEFORE_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_after_comma_in_array_initializer" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_between_empty_braces_in_array_initializer" mapTo
    field(common::SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES)
      .convertInsert()
  // endregion
  // region Array element access
  "insert_space_before_opening_bracket_in_array_reference" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_opening_bracket_in_array_reference" mapTo
    field(common::SPACE_WITHIN_BRACKETS)
      .convertInsert()
  "insert_space_before_closing_bracket_in_array_reference" mapTo
    field(common::SPACE_WITHIN_BRACKETS)
      .doNotImport()
      .convertInsert()
  // endregion
  // endregion
  // region Parameterized types
  // region Type reference
  "insert_space_before_opening_angle_bracket_in_parameterized_type_reference" mapTo
    field(common::SPACE_BEFORE_TYPE_PARAMETER_LIST)
      .convertInsert()
  "insert_space_after_opening_angle_bracket_in_parameterized_type_reference" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .convertInsert()
  "insert_space_before_comma_in_parameterized_type_reference" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_parameterized_type_reference" mapTo
    field(common::SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS)
      .convertInsert()
  "insert_space_before_closing_angle_bracket_in_parameterized_type_reference" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Type arguments
  "insert_space_before_opening_angle_bracket_in_type_arguments" mapTo
    field(common::SPACE_BEFORE_TYPE_PARAMETER_LIST)
      .doNotImport()
      .convertInsert()
  "insert_space_after_opening_angle_bracket_in_type_arguments" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_type_arguments" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_type_arguments" mapTo
    field(common::SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_angle_bracket_in_type_arguments" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_after_closing_angle_bracket_in_type_arguments" mapTo
    field(custom::SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT)
      .convertInsert()
  // endregion
  // region Type parameters
  "insert_space_before_opening_angle_bracket_in_type_parameters" mapTo
    field(custom::SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER)
      .convertInsert()
  "insert_space_after_opening_angle_bracket_in_type_parameters" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_before_comma_in_type_parameters" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_comma_in_type_parameters" mapTo
    field(common::SPACE_AFTER_COMMA)
      .doNotImport()
      .convertInsert()
  "insert_space_before_closing_angle_bracket_in_type_parameters" mapTo
    field(custom::SPACES_WITHIN_ANGLE_BRACKETS)
      .doNotImport()
      .convertInsert()
  "insert_space_after_closing_angle_bracket_in_type_parameters" mapTo
    const(true)
      .convertInsert()
  "insert_space_before_and_in_type_parameter" mapTo
    field(custom::SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS)
      .convertInsert()
  "insert_space_after_and_in_type_parameter" mapTo
    field(custom::SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS)
      .doNotImport()
      .convertInsert()
  // endregion
  // region Wildcard type
  "insert_space_before_question_in_wildcard" mapTo
    const(false)
      .convertInsert()
  "insert_space_after_question_in_wildcard" mapTo
    const(false)
      .convertInsert()
  // endregion
  // endregion
}