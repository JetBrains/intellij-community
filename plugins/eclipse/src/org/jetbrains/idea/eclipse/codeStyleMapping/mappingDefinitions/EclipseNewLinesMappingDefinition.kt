// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.ignored
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.util.convertBoolean
import org.jetbrains.idea.eclipse.codeStyleMapping.util.doNotImport
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertBracedCodeOnOneLine
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertInsert

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addNewLinesMapping() {
  "put_empty_statement_on_new_line" mapTo
    const(true)
      .convertBoolean()
  "insert_new_line_after_opening_brace_in_array_initializer" mapTo
    field(common::ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE)
      .convertInsert()
  "insert_new_line_before_closing_brace_in_array_initializer" mapTo
    field(common::ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE)
      .convertInsert()
  "insert_new_line_at_end_of_file_if_missing" mapTo
    field(this::isEnsureNewLineAtEOF)
      .convertInsert()
  // region In control statements
  "insert_new_line_before_else_in_if_statement" mapTo
    field(common::ELSE_ON_NEW_LINE)
      .convertInsert()
  "insert_new_line_before_catch_in_try_statement" mapTo
    field(common::CATCH_ON_NEW_LINE)
      .convertInsert()
  "insert_new_line_before_finally_in_try_statement" mapTo
    field(common::FINALLY_ON_NEW_LINE)
      .convertInsert()
  "insert_new_line_before_while_in_do_statement" mapTo
    field(common::WHILE_ON_NEW_LINE)
      .convertInsert()
  "insert_new_line_after_label" mapTo
    const(true)
      .convertInsert()
  // region 'if else'
  "keep_then_statement_on_same_line" mapTo
    field(common::KEEP_CONTROL_STATEMENT_IN_ONE_LINE)
      .convertBoolean()
  "keep_imple_if_on_one_line" mapTo
    field(common::KEEP_SIMPLE_BLOCKS_IN_ONE_LINE)
      .convertBoolean()
  "keep_else_statement_on_same_line" mapTo
    field(common::KEEP_CONTROL_STATEMENT_IN_ONE_LINE)
      .doNotImport()
      .convertBoolean()
  "compact_else_if" mapTo
    field(common::SPECIAL_ELSE_IF_TREATMENT)
      .convertBoolean()
  "format_guardian_clause_on_one_line" mapTo
    ignored()
  // endregion
  // region Simple loops
  "keep_simple_for_body_on_same_line" mapTo
    field(common::KEEP_CONTROL_STATEMENT_IN_ONE_LINE)
      .doNotImport()
      .convertBoolean()
  "keep_simple_while_body_on_same_line" mapTo
    field(common::KEEP_CONTROL_STATEMENT_IN_ONE_LINE)
      .doNotImport()
      .convertBoolean()
  "keep_simple_do_while_body_on_same_line" mapTo
    field(common::KEEP_CONTROL_STATEMENT_IN_ONE_LINE)
      .doNotImport()
      .convertBoolean()
  // endregion
  // endregion
  // region After annotations
  // see also Line Wrapping > Annotations region
  "insert_new_line_after_annotation_on_package" mapTo
    const(true)
      .convertInsert()
  "insert_new_line_after_annotation_on_type" mapTo
    compute(
      import = { /* do not import */ },
      export = { common.CLASS_ANNOTATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP }
    ).convertInsert()
  "insert_new_line_after_annotation_on_enum_constant" mapTo
    const(false)
      .convertInsert()
  "insert_new_line_after_annotation_on_parameter" mapTo
    compute(
      import = { /* do not import */ },
      export = { common.PARAMETER_ANNOTATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP }
    ).convertInsert()
  "insert_new_line_after_annotation_on_method" mapTo
    compute(
      import = { /* do not import */ },
      export = { common.METHOD_ANNOTATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP }
    ).convertInsert()
  "insert_new_line_after_annotation_on_local_variable" mapTo
    compute(
      import = { /* do not import */ },
      export = { common.VARIABLE_ANNOTATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP }
    ).convertInsert()
  "insert_new_line_after_type_annotation" mapTo
    const(false)
      .convertInsert()
  "insert_new_line_after_annotation_on_field" mapTo
    compute(
      import = { /* do not import */ },
      export = { common.FIELD_ANNOTATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP }
    ).convertInsert()
  // endregion
  // region Keep braced code on one line
  "keep_loop_body_block_on_one_line" mapTo
    field(common::KEEP_SIMPLE_BLOCKS_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_if_then_body_block_on_one_line" mapTo
    field(common::KEEP_SIMPLE_BLOCKS_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_lambda_body_block_on_one_line" mapTo
    field(common::KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE)
      .convertBracedCodeOnOneLine()
  "keep_code_block_on_one_line" mapTo
    field(common::KEEP_SIMPLE_BLOCKS_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_method_body_on_one_line" mapTo
    field(common::KEEP_SIMPLE_METHODS_IN_ONE_LINE)
      .convertBracedCodeOnOneLine()
  "keep_simple_getter_setter_on_one_line" mapTo
    field(common::KEEP_SIMPLE_METHODS_IN_ONE_LINE)
      .doNotImport()
      .convertBoolean()
  "keep_type_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .convertBracedCodeOnOneLine()
  "keep_anonymous_type_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_enum_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_enum_constant_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_record_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_record_constructor_on_one_line" mapTo
    field(common::KEEP_SIMPLE_METHODS_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  "keep_annotation_declaration_on_one_line" mapTo
    field(common::KEEP_SIMPLE_CLASSES_IN_ONE_LINE)
      .doNotImport()
      .convertBracedCodeOnOneLine()
  // endregion
}