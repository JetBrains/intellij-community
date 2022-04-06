// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.idea.eclipse.codeStyleMapping.AllJavaCodeStyleSettings
import org.jetbrains.idea.eclipse.exporter.EclipseCodeStyleSchemeExporter
import org.jetbrains.idea.eclipse.importer.EclipseCodeStyleSchemeImporter.Companion.readEclipseXmlProfileOptions
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EclipseSettingsExportTest : LightPlatformTestCase() {
  private inline fun exportAndTestCodeStyleSettings(setup: AllJavaCodeStyleSettings.() -> Unit, test: Map<String, String>.() -> Unit) {
    val schemes = CodeStyleSchemes.getInstance()
    val scheme = schemes.createNewScheme(getTestName(false), null)
    val settings = AllJavaCodeStyleSettings.from(scheme.codeStyleSettings)
    val ensureNewLineAtEOFBackup = settings.isEnsureNewLineAtEOF

    settings.setup()

    try {
      val actual = ByteArrayOutputStream().use {
        EclipseCodeStyleSchemeExporter.exportCodeStyleSettings(scheme, it)
        ByteArrayInputStream(it.toByteArray()).readEclipseXmlProfileOptions()
      }

      actual.test()
    }
    finally {
      schemes.deleteScheme(scheme)
      settings.isEnsureNewLineAtEOF = ensureNewLineAtEOFBackup
    }
  }

  fun testExportCodeStyleSettingsToXmlProfile() {
    exportAndTestCodeStyleSettings(
      setup = {
        common.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true
        common.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true
        common.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false
        common.SPACE_WITHIN_ANNOTATION_PARENTHESES = false
        common.BLANK_LINES_AROUND_FIELD = 0
        common.SPACE_WITHIN_WHILE_PARENTHESES = false
        common.ELSE_ON_NEW_LINE = false
        common.ALIGN_GROUP_FIELD_DECLARATIONS = false
        common.SPACE_BEFORE_FOR_PARENTHESES = true
        common.SPACE_AROUND_ADDITIVE_OPERATORS = true
        common.SPACE_AROUND_BITWISE_OPERATORS = true
        common.SPACE_AROUND_EQUALITY_OPERATORS = true
        common.SPACE_AROUND_LOGICAL_OPERATORS = true
        common.FINALLY_ON_NEW_LINE = false
        common.CATCH_ON_NEW_LINE = false
        common.SPACE_BEFORE_WHILE_PARENTHESES = true
        common.BLANK_LINES_AFTER_PACKAGE = 1
        indent.CONTINUATION_INDENT_SIZE = 8
        common.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        common.BLANK_LINES_BEFORE_PACKAGE = 0
        common.SPACE_WITHIN_FOR_PARENTHESES = false
        common.SPACE_BEFORE_METHOD_PARENTHESES = false
        common.SPACE_WITHIN_CATCH_PARENTHESES = false
        common.SPACE_BEFORE_METHOD_CALL_PARENTHESES = false
        common.SPACE_WITHIN_CAST_PARENTHESES = false
        common.SPACE_AROUND_UNARY_OPERATOR = false
        common.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
        this.isEnsureNewLineAtEOF = false
        common.SPACE_WITHIN_TRY_PARENTHESES = false
        common.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false
        common.WHILE_ON_NEW_LINE = false
        custom.ENABLE_JAVADOC_FORMATTING = true
        common.SPACE_BEFORE_SEMICOLON = false
        common.BLANK_LINES_BEFORE_METHOD_BODY = 0
        common.SPACE_BEFORE_COLON = true
        common.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false
        common.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true
        common.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false
        common.SPACE_BEFORE_QUEST = true
        common.BLANK_LINES_BEFORE_IMPORTS = 1
        common.SPACE_AFTER_COLON = true
        common.SPACE_WITHIN_FOR_PARENTHESES = false
        common.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = true
        common.SPACE_BEFORE_SWITCH_PARENTHESES = true
        common.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false
        common.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
        common.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true
        common.SPACE_WITHIN_METHOD_PARENTHESES = false
        common.SPACE_BEFORE_CATCH_PARENTHESES = true
        common.SPACE_WITHIN_ANNOTATION_PARENTHESES = false
        common.BLANK_LINES_AFTER_IMPORTS = 1
        common.KEEP_FIRST_COLUMN_COMMENT = false
        common.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
        indent.USE_TAB_CHARACTER = true
        indent.SMART_TABS = true
        general.FORMATTER_TAGS_ENABLED = true
        general.FORMATTER_OFF_TAG = "@off_tag"
        general.FORMATTER_ON_TAG = "@on_tag"
        common.FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.PARAMETER_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
        common.CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true
        common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
        common.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        common.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
        common.ALIGN_MULTILINE_EXTENDS_LIST = true
        common.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_ASSIGNMENT = true
        common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_PARAMETERS = false
        common.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
        common.ALIGN_MULTILINE_BINARY_OPERATION = true
        common.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_THROWS_LIST = true
        common.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_RESOURCES = true
        common.RESOURCE_LIST_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.RESOURCE_LIST_LPAREN_ON_NEXT_LINE = false
        common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_CHAINED_METHODS = true
        common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ALIGN_MULTILINE_TERNARY_OPERATION = false
        common.BLANK_LINES_AFTER_CLASS_HEADER = 2
        common.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 2
        common.BLANK_LINES_AROUND_CLASS = 3
        common.KEEP_BLANK_LINES_IN_CODE = 5
        common.KEEP_BLANK_LINES_IN_DECLARATIONS = 5
        common.KEEP_BLANK_LINES_BEFORE_RBRACE = 5
        common.SPACE_BEFORE_ELSE_KEYWORD = true
        common.SPACE_BEFORE_FINALLY_KEYWORD = true
        common.SPACE_BEFORE_CATCH_KEYWORD = true
        common.SPACE_BEFORE_IF_LBRACE = true
        common.SPACE_BEFORE_FOR_LBRACE = true
        common.SPACE_BEFORE_WHILE_LBRACE = true
        common.SPACE_BEFORE_DO_LBRACE = true
        common.SPACE_BEFORE_TRY_LBRACE = true
        common.SPACE_BEFORE_CATCH_LBRACE = true
        common.SPACE_BEFORE_FINALLY_LBRACE = true
        common.SPACE_BEFORE_SYNCHRONIZED_LBRACE = true
        common.SPACE_BEFORE_METHOD_LBRACE = true
        common.SPACE_BEFORE_CLASS_LBRACE = true
        common.SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false
        common.KEEP_LINE_BREAKS = false
        common.ALIGN_CONSECUTIVE_ASSIGNMENTS = true
        common.ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true
        custom.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true
        common.ALIGN_MULTILINE_FOR = false
        custom.ALIGN_MULTILINE_RECORDS = true
        custom.ALIGN_MULTILINE_TEXT_BLOCKS = false
        common.ALIGN_THROWS_KEYWORD = false
        custom.ALIGN_TYPES_IN_MULTI_CATCH = false
        custom.ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM or CommonCodeStyleSettings.WRAP_AS_NEEDED
        common.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true
        common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
        common.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0
        custom.BLANK_LINES_AROUND_INITIALIZER = 1
        common.BLANK_LINES_AROUND_METHOD = 1
        common.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1
        common.BLANK_LINES_BEFORE_CLASS_END = 0
        common.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
        common.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
        common.ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
        general.FORMATTER_TAGS_ACCEPT_REGEXP = false
        common.FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false
        common.FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false
        common.FOR_STATEMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
        common.INDENT_CASE_FROM_SWITCH = false
        indent.INDENT_SIZE = 4
        indent.TAB_SIZE = 2
        custom.JD_ADD_BLANK_AFTER_DESCRIPTION = true
        custom.JD_ADD_BLANK_AFTER_PARM_COMMENTS = false
        custom.JD_ADD_BLANK_AFTER_RETURN = false
        custom.JD_ALIGN_EXCEPTION_COMMENTS = false
        custom.JD_ALIGN_PARAM_COMMENTS = false
        custom.JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
        custom.JD_INDENT_ON_CONTINUATION = true
        custom.JD_KEEP_EMPTY_LINES = true
        custom.JD_LEADING_ASTERISKS_ARE_ENABLED = true
        custom.JD_PARAM_DESCRIPTION_ON_NEW_LINE = true
        custom.JD_P_AT_EMPTY_LINES = false
        common.KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 5
        indent.KEEP_INDENTS_ON_EMPTY_LINES = false
        common.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false
        common.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
        common.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
        indent.LABEL_INDENT_ABSOLUTE = false
        indent.LABEL_INDENT_SIZE = 0
        common.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
        common.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
        common.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false
        common.MODIFIER_LIST_WRAP = false
        custom.MULTI_CATCH_TYPES_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
        custom.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = true
        custom.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
        common.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false
        custom.RECORD_COMPONENTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
        common.RESOURCE_LIST_RPAREN_ON_NEXT_LINE = true
        custom.RPAREN_ON_NEW_LINE_IN_ANNOTATION = true
        custom.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
        custom.SPACES_WITHIN_ANGLE_BRACKETS = false
        custom.SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT = true
        common.SPACE_AFTER_COMMA = true
        common.SPACE_AFTER_QUEST = true
        common.SPACE_AFTER_SEMICOLON = true
        common.SPACE_AFTER_TYPE_CAST = true
        common.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
        common.SPACE_AROUND_LAMBDA_ARROW = true
        common.SPACE_AROUND_MULTIPLICATIVE_OPERATORS = false
        common.SPACE_AROUND_RELATIONAL_OPERATORS = true
        common.SPACE_AROUND_SHIFT_OPERATORS = true
        custom.SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS = true
        custom.SPACE_BEFORE_COLON_IN_FOREACH = true
        common.SPACE_BEFORE_COMMA = false
        common.SPACE_BEFORE_ELSE_LBRACE = true
        common.SPACE_BEFORE_IF_PARENTHESES = true
        custom.SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER = false
        common.SPACE_BEFORE_SWITCH_LBRACE = true
        common.SPACE_BEFORE_TRY_PARENTHESES = true
        common.SPACE_BEFORE_TYPE_PARAMETER_LIST = false
        common.SPACE_BEFORE_WHILE_KEYWORD = true
        common.SPACE_WITHIN_BRACKETS = false
        common.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false
        common.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false
        common.SPACE_WITHIN_IF_PARENTHESES = false
        common.SPACE_WITHIN_PARENTHESES = false
        custom.SPACE_WITHIN_RECORD_HEADER = true
        common.SPACE_WITHIN_SWITCH_PARENTHESES = false
        common.SPECIAL_ELSE_IF_TREATMENT = true
        common.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true
        indent.USE_RELATIVE_INDENTS = false
        common.WRAP_COMMENTS = true
        common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
        common.RIGHT_MARGIN = 80
      },
      test = {
        assertIdEquals("tabulation.char", TAB_CHAR_MIXED)
        assertIdEquals("use_tabs_only_for_leading_indentations", VALUE_TRUE)
        assertIdEquals("indentation.size", "4")
        assertIdEquals("tabulation.size", "2")
        assertIdEquals("text_block_indentation", TEXT_BLOCK_INDENT_DEFAULT)
        assertIdEquals("indent_body_declarations_compare_to_type_header", VALUE_TRUE)
        assertIdEquals("indent_body_declarations_compare_to_enum_declaration_header", VALUE_TRUE)
        assertIdEquals("indent_empty_lines", VALUE_FALSE)
        assertIdEquals("align_type_members_on_columns", VALUE_FALSE)
        assertIdEquals("align_with_spaces", VALUE_TRUE)
        assertIdEquals("align_fields_grouping_blank_lines", "1")
        assertIdEquals("brace_position_for_type_declaration", VALUE_END_OF_LINE)
        assertIdEquals("brace_position_for_anonymous_type_declaration", VALUE_END_OF_LINE)
        assertIdEquals("brace_position_for_method_declaration", VALUE_NEXT_LINE_SHIFTED)
        assertIdEquals("brace_position_for_array_initializer", VALUE_END_OF_LINE)
        assertIdEquals("brace_position_for_lambda_body", VALUE_NEXT_LINE_IF_WRAPPED)
        assertIdEquals("parentheses_positions_in_annotation", PARENS_SEPARATE_LINES_IF_NOT_EMPTY)
        assertIdEquals("parentheses_positions_in_lambda_declaration", PARENS_SEPARATE_LINES_IF_WRAPPED)
        assertIdEquals("parentheses_positions_in_for_statment", PARENS_COMMON_LINES)
        assertIdEquals("insert_space_before_opening_brace_in_type_declaration", VALUE_INSERT)
        assertIdEquals("insert_space_before_opening_brace_in_anonymous_type_declaration", VALUE_INSERT)
        assertIdEquals("insert_space_before_comma_in_superinterfaces", VALUE_DO_NOT_INSERT)
        assertIdEquals("insert_space_after_comma_in_superinterfaces", VALUE_INSERT)
        assertIdEquals("number_of_empty_lines_to_preserve", "5")
        assertIdEquals("blank_lines_between_type_declarations", "3")
        assertIdNotPresent("number_of_blank_lines_at_end_of_method_body")
        assertIdEquals("insert_new_line_after_annotation_on_package", VALUE_INSERT)
        assertIdEquals("lineSplit", "80")
        assertIdEquals("continuation_indentation", "2")
        assertIdEquals("join_wrapped_lines", VALUE_TRUE)
        assertIdEquals("alignment_for_superclass_in_type_declaration", "82")
        assertIdEquals("alignment_for_multiple_fields", "16")
        assertIdEquals("alignment_for_method_declaration", "0")
        assertIdEquals("alignment_for_throws_clause_in_method_declaration", "35")
        assertIdEquals("alignment_for_parameters_in_method_declaration", "16")
        assertIdEquals("alignment_for_enum_constants", "0")
        assertIdEquals("alignment_for_record_components", "83")
        assertIdEquals("alignment_for_arguments_in_method_invocation", "80")
        assertIdEquals("alignment_for_selector_in_method_invocation", "48")
        assertIdEquals("alignment_for_additive_operator", "50")
        assertIdEquals("wrap_before_additive_operator", VALUE_TRUE)
        assertIdEquals("alignment_for_conditional_expression", "16")
        assertIdEquals("alignment_for_conditional_expression_chain", "0")
        assertIdEquals("wrap_before_conditional_operator", VALUE_TRUE)
        assertIdEquals("alignment_for_assignment", "16")
        assertIdEquals("wrap_before_assignment_operator", VALUE_FALSE)
        assertIdEquals("alignment_for_expressions_in_array_initializer", "49")
        assertIdEquals("alignment_for_expressions_in_for_loop_header", "0")
        assertIdEquals("alignment_for_compact_if", "16")
        assertIdEquals("alignment_for_resources_in_try", "83")
        assertIdEquals("alignment_for_union_type_in_multicatch", "16")
        assertIdEquals("wrap_before_or_operator_multicatch", VALUE_FALSE)
        assertIdEquals("alignment_for_assertion_message", "0")
        assertIdEquals("wrap_before_assertion_message_operator", VALUE_TRUE)
        assertIdEquals("alignment_for_parameterized_type_references", "0")
        assertIdEquals("alignment_for_annotations_on_package", "0")
        assertIdEquals("alignment_for_annotations_on_type", "49")
        assertIdEquals("alignment_for_annotations_on_enum_constant", "0")
        assertIdEquals("alignment_for_annotations_on_field", "49")
        assertIdEquals("alignment_for_annotations_on_method", "16")
        assertIdEquals("alignment_for_annotations_on_local_variable", "48")
        assertIdEquals("alignment_for_annotations_on_parameter", "0")
        assertIdEquals("alignment_for_arguments_in_annotation", "80")
        assertIdEquals("alignment_for_module_statements", "0")
        assertIdEquals("comment.line_length", "80")
        assertIdEquals("comment.align_tags_descriptions_grouped", VALUE_FALSE)
        assertIdEquals("comment.indent_parameter_description", VALUE_TRUE)
        assertIdEquals("comment.indent_tag_description", VALUE_TRUE)
        assertIdEquals("comment.indent_root_tags", VALUE_TRUE)
        assertIdEquals("use_on_off_tags", VALUE_TRUE)
      }
    )
  }

  fun testExportCodeStyleSettingsWithUseTab() {
    exportAndTestCodeStyleSettings(
      setup = {
        indent.apply {
          USE_TAB_CHARACTER = true
          INDENT_SIZE = 4
          TAB_SIZE = 3
          CONTINUATION_INDENT_SIZE = 9
        }
      },
      test = {
        assertIdEquals("tabulation.char", TAB_CHAR_MIXED)
        assertIdEquals("tabulation.size", "3")
        assertIdEquals("indentation.size", "4")
        assertIdEquals("continuation_indentation", "2")
        assertIdEquals("continuation_indentation_for_array_initializer", "2")
      }
    )
  }

  fun testExportCodeStyleSettingsWithoutUseTab() {
    exportAndTestCodeStyleSettings(
      setup = {
        indent.apply {
          USE_TAB_CHARACTER = false
          INDENT_SIZE = 4
          TAB_SIZE = 3
          CONTINUATION_INDENT_SIZE = 9
        }
      },
      test = {
        assertIdEquals("tabulation.char", TAB_CHAR_SPACE)
        assertIdEquals("tabulation.size", "4")
        assertIdEquals("indentation.size", "3")
        assertIdEquals("continuation_indentation", "2")
        assertIdEquals("continuation_indentation_for_array_initializer", "2")
      }
    )
  }

  companion object {
    fun Map<String, String>.assertIdNotPresent(idPostfix: String) {
      assertFalse(this.containsKey(completeId(idPostfix)))
    }

    fun Map<String, String>.assertIdEquals(idPostfix: String, expectedValue: String) {
      val id = completeId(idPostfix)
      val actualValue = this[id]
      assertNotNull("$id was not exported", actualValue)
      assertEquals("$id: values do not match", expectedValue, actualValue!!)
    }
  }
}

