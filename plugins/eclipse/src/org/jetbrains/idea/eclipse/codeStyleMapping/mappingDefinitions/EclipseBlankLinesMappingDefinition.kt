// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.ignored
import org.jetbrains.idea.eclipse.codeStyleMapping.util.alsoSet
import org.jetbrains.idea.eclipse.codeStyleMapping.util.doNotImport
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertBlankLines

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addBlankLinesMapping() {
  "number_of_empty_lines_to_preserve" mapTo
    field(common::KEEP_BLANK_LINES_IN_CODE)
      .alsoSet(common::KEEP_BLANK_LINES_IN_DECLARATIONS,
               common::KEEP_BLANK_LINES_BEFORE_RBRACE,
               common::KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER)
      .convertBlankLines()
  // region Blank lines within compilation unit
  "blank_lines_before_package" mapTo
    field(common::BLANK_LINES_BEFORE_PACKAGE)
      .convertBlankLines()
  "blank_lines_after_package" mapTo
    field(common::BLANK_LINES_AFTER_PACKAGE)
      .convertBlankLines()
  "blank_lines_before_imports" mapTo
    field(common::BLANK_LINES_BEFORE_IMPORTS)
      .convertBlankLines()
  "blank_lines_between_import_groups" mapTo
    ignored()
  "blank_lines_after_imports" mapTo
    field(common::BLANK_LINES_AFTER_IMPORTS)
      .convertBlankLines()
  "blank_lines_between_type_declarations" mapTo
    field(common::BLANK_LINES_AROUND_CLASS)
      .convertBlankLines()
  // endregion
  // region Blank lines within type declarations
  "blank_lines_before_first_class_body_declaration" mapTo
    field(common::BLANK_LINES_AFTER_CLASS_HEADER)
      .alsoSet(common::BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER)
      .convertBlankLines()
  "blank_lines_after_last_class_body_declaration" mapTo
    field(common::BLANK_LINES_BEFORE_CLASS_END)
      .convertBlankLines()
  "blank_lines_before_new_chunk" mapTo
    field(custom::BLANK_LINES_AROUND_INITIALIZER)
      .convertBlankLines()
  "blank_lines_before_member_type" mapTo
    field(common::BLANK_LINES_AROUND_CLASS)
      .doNotImport()
      .convertBlankLines()
  "blank_lines_before_field" mapTo
    field(common::BLANK_LINES_AROUND_FIELD)
      .alsoSet(common::BLANK_LINES_AROUND_FIELD_IN_INTERFACE)
      .convertBlankLines()
  "blank_lines_before_abstract_method" mapTo
    field(common::BLANK_LINES_AROUND_METHOD_IN_INTERFACE)
      .convertBlankLines()
  "blank_lines_before_method" mapTo
    field(common::BLANK_LINES_AROUND_METHOD)
      .convertBlankLines()
  // endregion
  // region Blank lines within method/constructor declarations
  "number_of_blank_lines_at_beginning_of_method_body" mapTo
    field(common::BLANK_LINES_BEFORE_METHOD_BODY)
      .convertBlankLines()
  "number_of_blank_lines_at_end_of_method_body" mapTo
    ignored()
  "number_of_blank_lines_at_beginning_of_code_block" mapTo
    ignored()
  "number_of_blank_lines_at_end_of_code_block" mapTo
    ignored()
  "number_of_blank_lines_before_code_block" mapTo
    ignored()
  "number_of_blank_lines_after_code_block" mapTo
    ignored()
  "blank_lines_between_statement_group_in_switch" mapTo
    ignored()
  // endregion
}