// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.alsoSet
import org.jetbrains.idea.eclipse.codeStyleMapping.util.doNotImport
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.*

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addParenthesesPositionsMapping() {
  "parentheses_positions_in_method_delcaration" mapTo
    field(common::METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
      .alsoSet(common::METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_method_invocation" mapTo
    field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
      .alsoSet(common::CALL_PARAMETERS_RPAREN_ON_NEXT_LINE)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_enum_constant_declaration" mapTo
    field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
      .doNotImport()
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_record_declaration" mapTo
    field(custom::NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER)
      .alsoSet(custom::RPAREN_ON_NEW_LINE_IN_RECORD_HEADER)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_annotation" mapTo
    field(custom::NEW_LINE_AFTER_LPAREN_IN_ANNOTATION)
      .alsoSet(custom::RPAREN_ON_NEW_LINE_IN_ANNOTATION)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_NOT_EMPTY)
  "parentheses_positions_in_lambda_declaration" mapTo
    field(common::METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
      .doNotImport()
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_if_while_statement" mapTo
    const(false)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_for_statment" mapTo
    field(common::FOR_STATEMENT_LPAREN_ON_NEXT_LINE)
      .alsoSet(common::FOR_STATEMENT_RPAREN_ON_NEXT_LINE)
      .convertParenPosition(PARENS_SEPARATE_LINES)
  "parentheses_positions_in_switch_statement" mapTo
    const(false)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
  "parentheses_positions_in_try_clause" mapTo
    field(common::RESOURCE_LIST_LPAREN_ON_NEXT_LINE)
      .alsoSet(common::RESOURCE_LIST_RPAREN_ON_NEXT_LINE)
      .convertParenPosition(PARENS_SEPARATE_LINES)
  "parentheses_positions_in_catch_clause" mapTo
    const(false)
      .convertParenPosition(PARENS_SEPARATE_LINES_IF_WRAPPED)
}
