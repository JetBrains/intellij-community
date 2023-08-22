// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.ignored
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.convertInsert

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addCommentsMapping() {
  // there is no distinct right margin for comments in IDEA
  "comment.line_length" mapTo
    field(this::safeRightMargin)
      .doNotImport()
      .convertInt()
  "comment.count_line_length_from_starting_position" mapTo
    const(false)
      .convertBoolean()
  "comment.format_javadoc_comments" mapTo
    field(custom::ENABLE_JAVADOC_FORMATTING)
      .alsoSet(common::WRAP_COMMENTS)
      .convertBoolean()
  "comment.format_block_comments" mapTo
    const(false)
      .convertBoolean()
  "comment.format_line_comments" mapTo
    const(false)
      .convertBoolean()
  "format_line_comment_starting_on_first_column" mapTo
    const(false)
      .convertBoolean()
  "comment.format_header" mapTo
    const(true)
      .convertBoolean()
  "comment.preserve_white_space_between_code_and_line_comments" mapTo
    const(true)
      .convertBoolean()
  "never_indent_line_comments_on_first_column" mapTo
    field(common::KEEP_FIRST_COLUMN_COMMENT)
      .convertBoolean()
  "never_indent_block_comments_on_first_column" mapTo
    const(false)
      .convertBoolean()
  "join_lines_in_comments" mapTo
    const(false)
      .convertBoolean()
  // region Javadocs
  onImportDo {
    custom.JD_LEADING_ASTERISKS_ARE_ENABLED = true
    custom.JD_P_AT_EMPTY_LINES = false
    custom.JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
  }
  "comment.format_html" mapTo
    const(false)
      .convertBoolean()
  "comment.format_source_code" mapTo
    const(false)
      .convertBoolean()
  "comment.insert_new_line_before_root_tags" mapTo
    field(custom::JD_ADD_BLANK_AFTER_DESCRIPTION)
      .convertInsert()
  "comment.insert_new_line_between_different_tags" mapTo
    field(custom::JD_ADD_BLANK_AFTER_PARM_COMMENTS)
      .alsoSet(custom::JD_ADD_BLANK_AFTER_RETURN)
      .convertInsert()
  "comment.align_tags_names_descriptions" mapTo
    const(false)
      .convertBoolean()
  "comment.align_tags_descriptions_grouped" mapTo
    compute(
      import = { value ->
        custom.JD_ALIGN_PARAM_COMMENTS = value
        custom.JD_ALIGN_EXCEPTION_COMMENTS = value
      },
      export = {
        custom.JD_ALIGN_PARAM_COMMENTS && !custom.JD_PARAM_DESCRIPTION_ON_NEW_LINE
      }
    ).convertBoolean()
  "comment.insert_new_line_for_parameter" mapTo
    field(custom::JD_PARAM_DESCRIPTION_ON_NEW_LINE)
      .convertInsert()
  "comment.indent_parameter_description" mapTo
    compute(
      import = { value -> custom.JD_INDENT_ON_CONTINUATION = value },
      export = {
        (custom.JD_INDENT_ON_CONTINUATION && !custom.JD_ALIGN_PARAM_COMMENTS)
        || custom.JD_PARAM_DESCRIPTION_ON_NEW_LINE
      }
    )
      .convertBoolean()
  "comment.indent_tag_description" mapTo
    compute(
      import = { /* do not import */ },
      export = {
        (custom.JD_INDENT_ON_CONTINUATION && !custom.JD_ALIGN_PARAM_COMMENTS)
        || custom.JD_PARAM_DESCRIPTION_ON_NEW_LINE
      }
    )
      .convertBoolean()
  "comment.indent_root_tags" mapTo
    compute(
      import = { /* do not import */ },
      export = { custom.JD_INDENT_ON_CONTINUATION && !custom.JD_ALIGN_PARAM_COMMENTS }
    ).convertBoolean()
  "comment.new_lines_at_javadoc_boundaries" mapTo
    const(true)
      .convertBoolean()
  "comment.clear_blank_lines_in_javadoc_comment" mapTo
    field(custom::JD_KEEP_EMPTY_LINES)
      .invert()
      .convertBoolean()
  // endregion
  // region Block comments
  // IDEA does not ever format block comments
  "comment.new_lines_at_block_boundaries" mapTo
    ignored()
  "comment.clear_blank_lines_in_block_comment" mapTo
    ignored()
  // endregion
}