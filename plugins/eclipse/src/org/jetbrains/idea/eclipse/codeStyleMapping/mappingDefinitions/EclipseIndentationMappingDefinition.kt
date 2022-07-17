// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.TabCharacterConvertor
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addIndentationMapping() {
  onImportDo {
    indent.USE_RELATIVE_INDENTS = false
    indent.LABEL_INDENT_ABSOLUTE = false
    indent.LABEL_INDENT_SIZE = 0
  }
  "tabulation.char" mapTo
    compute(
      import = { value ->
        when (value) {
          EclipseFormatterOptions.TAB_CHAR_MIXED,
          EclipseFormatterOptions.TAB_CHAR_TAB -> indent.USE_TAB_CHARACTER = true
          EclipseFormatterOptions.TAB_CHAR_SPACE -> indent.USE_TAB_CHARACTER = false
          else -> throw UnexpectedIncomingValue(value)
        }
        eclipseTabChar = value
      },
      export = {
        if (indent.USE_TAB_CHARACTER)
          EclipseFormatterOptions.TAB_CHAR_MIXED
        else
          EclipseFormatterOptions.TAB_CHAR_SPACE
      }).convert(TabCharacterConvertor)
  "use_tabs_only_for_leading_indentations" mapTo
    field(indent::SMART_TABS)
      .convertBoolean()
  "indentation.size" mapTo
    compute(
      import = { value ->
        when (eclipseTabChar) {
          EclipseFormatterOptions.TAB_CHAR_MIXED -> indent.INDENT_SIZE = value
          EclipseFormatterOptions.TAB_CHAR_TAB -> { /* do nothing */ }
          EclipseFormatterOptions.TAB_CHAR_SPACE -> indent.TAB_SIZE = value
          else -> throw UnexpectedIncomingValue(value)
        }
      },
      export = {
        if (indent.USE_TAB_CHARACTER) indent.INDENT_SIZE
        else indent.TAB_SIZE
      }
    ).convertInt()
  "tabulation.size" mapTo
    compute(
      import = { value ->
        when (eclipseTabChar) {
          EclipseFormatterOptions.TAB_CHAR_MIXED -> indent.TAB_SIZE = value
          EclipseFormatterOptions.TAB_CHAR_TAB -> {
            indent.TAB_SIZE = value
            indent.INDENT_SIZE = value
          }
          EclipseFormatterOptions.TAB_CHAR_SPACE -> indent.INDENT_SIZE = value
          else -> throw UnexpectedIncomingValue(value)
        }
      },
      export = {
        if (indent.USE_TAB_CHARACTER) indent.TAB_SIZE
        else indent.INDENT_SIZE
      }
    ).convertInt()
  "text_block_indentation" mapTo
    compute(
      import = { value ->
        custom.ALIGN_MULTILINE_TEXT_BLOCKS = when (value) {
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_BY_ONE,
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_DEFAULT,
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_DO_NOT_TOUCH
          -> false
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_ON_COLUMN
          -> true
          else -> throw UnexpectedIncomingValue(value)
        }
      },
      export = {
        if (custom.ALIGN_MULTILINE_TEXT_BLOCKS)
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_ON_COLUMN
        else
          EclipseFormatterOptions.TEXT_BLOCK_INDENT_DEFAULT
      }
    )
  // region Indented elements
  "indent_body_declarations_compare_to_type_header" mapTo
    field(common::DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS)
      .invert()
      .convertBoolean()
  "indent_body_declarations_compare_to_enum_declaration_header" mapTo
    const(true)
      .convertBoolean()
  "indent_body_declarations_compare_to_enum_constant_header" mapTo
    const(true)
      .convertBoolean()
  "indent_body_declarations_compare_to_annotation_declaration_header" mapTo
    field(common::DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS)
      .doNotImport()
      .invert()
      .convertBoolean()
  "indent_body_declarations_compare_to_record_header" mapTo
    field(common::DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS)
      .doNotImport()
      .invert()
      .convertBoolean()
  "indent_statements_compare_to_body" mapTo
    const(true)
      .convertBoolean()
  "indent_statements_compare_to_block" mapTo
    const(true)
      .convertBoolean()
  "indent_switchstatements_compare_to_switch" mapTo
    field(common::INDENT_CASE_FROM_SWITCH)
      .convertBoolean()
  "indent_switchstatements_compare_to_cases" mapTo
    const(true)
      .convertBoolean()
  "indent_breaks_compare_to_cases" mapTo
    const(true)
      .convertBoolean()
  "indent_empty_lines" mapTo
    field(indent::KEEP_INDENTS_ON_EMPTY_LINES)
      .convertBoolean()
  // endregion
  // region Align items in columns
  "align_type_members_on_columns" mapTo
    field(common::ALIGN_GROUP_FIELD_DECLARATIONS)
      .convertBoolean()
  "align_variable_declarations_on_columns" mapTo
    field(common::ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS)
      .convertBoolean()
  "align_assignment_statements_on_columns" mapTo
    field(common::ALIGN_CONSECUTIVE_ASSIGNMENTS)
      .convertBoolean()
  "align_with_spaces" mapTo
    const(true)
      .convertBoolean()
  "align_fields_grouping_blank_lines" mapTo
    const(1)
      .convertInt()
  // endregion
}