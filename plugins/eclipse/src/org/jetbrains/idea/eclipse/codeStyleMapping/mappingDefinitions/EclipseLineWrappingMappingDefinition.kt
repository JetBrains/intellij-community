// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.mappingDefinitions

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.idea.eclipse.codeStyleMapping.EclipseJavaCodeStyleMappingDefinitionBuilder
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.*
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.EclipseWrapValue
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.keywordFollowedByListWrap
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.*

internal fun EclipseJavaCodeStyleMappingDefinitionBuilder.addLineWrappingMapping() {
  "lineSplit" mapTo
    field(this::safeRightMargin)
      .convertInt()
  fun exportContinuationIndentation(): Int {
    return if (indent.INDENT_SIZE == 0)
      0
    else
      indent.CONTINUATION_INDENT_SIZE / indent.INDENT_SIZE
  }
  // Note: Indentation mappings must be imported first
  "continuation_indentation" mapTo
    compute(
      import = { value ->
        indent.CONTINUATION_INDENT_SIZE = indent.INDENT_SIZE * value
      },
      export = ::exportContinuationIndentation
    ).convertInt()
  "continuation_indentation_for_array_initializer" mapTo
    compute(
      import = { /* do not import */ },
      export = ::exportContinuationIndentation
    ).convertInt()
  "join_wrapped_lines" mapTo
    field(common::KEEP_LINE_BREAKS)
      .invert()
      .convertBoolean()
  "wrap_outer_expressions_when_nested" mapTo
    const(false)
      .convertBoolean()
  // region Wrapping settings
  // region Class Declarations
  "alignment_for_superclass_in_type_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::EXTENDS_KEYWORD_WRAP),
      field(common::EXTENDS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_EXTENDS_LIST)
    ).doNotImport()
  "alignment_for_superinterfaces_in_type_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::EXTENDS_KEYWORD_WRAP),
      field(common::EXTENDS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_EXTENDS_LIST)
    )
  "alignment_for_multiple_fields" mapTo
    const(EclipseWrapValue().apply {
      lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
    }).convertWrap()
  // endregion
  // region Constructor declarations
  "alignment_for_parameters_in_constructor_declaration" mapTo
    listInBracketsWrap(
      field(common::METHOD_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS),
      field(common::METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
    ).doNotImport()
  "alignment_for_throws_clause_in_constructor_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::THROWS_KEYWORD_WRAP),
      field(common::THROWS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_THROWS_LIST)
    ).doNotImport()
  // endregion
  // region Method Declarations
  onImportDo {
    common.ALIGN_THROWS_KEYWORD = false
  }
  "alignment_for_method_declaration" mapTo
    compute(
      import = { value ->
        common.MODIFIER_LIST_WRAP = value.lineWrapPolicy != LineWrapPolicy.DO_NOT_WRAP
                                    && value.lineWrapPolicy != LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
                                    && value.isForceSplit
      },
      export = {
        EclipseWrapValue().apply {
          if (common.MODIFIER_LIST_WRAP) {
            lineWrapPolicy = LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY
            indentationPolicy = IndentationPolicy.INDENT_BY_ONE
            isForceSplit = true
          }
        }
      }
    ).convertWrap()
  "alignment_for_throws_clause_in_method_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::THROWS_KEYWORD_WRAP),
      field(common::THROWS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_THROWS_LIST)
    )
  "alignment_for_parameters_in_method_declaration" mapTo
    listInBracketsWrap(
      field(common::METHOD_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS),
      field(common::METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE)
    )
  // endregion
  // region 'enum' declaration
  "alignment_for_enum_constants" mapTo
    listWrap(
      field(common::ENUM_CONSTANTS_WRAP),
      const(false)
    )
  "alignment_for_arguments_in_enum_constant" mapTo
    listInBracketsWrap(
      field(common::CALL_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS_IN_CALLS),
      field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    ).doNotImport()
  "alignment_for_superinterfaces_in_enum_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::EXTENDS_KEYWORD_WRAP),
      field(common::EXTENDS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_EXTENDS_LIST)
    ).doNotImport()
  // endregion
  // region Record declarations
  "alignment_for_record_components" mapTo
    listInBracketsWrap(
      field(custom::RECORD_COMPONENTS_WRAP),
      field(custom::ALIGN_MULTILINE_RECORDS),
      field(custom::NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER)
    )
  "alignment_for_superinterfaces_in_record_declaration" mapTo
    keywordFollowedByListWrap(
      field(common::EXTENDS_KEYWORD_WRAP),
      field(common::EXTENDS_LIST_WRAP),
      field(common::ALIGN_MULTILINE_EXTENDS_LIST)
    ).doNotImport()
  // endregion
  // region Function Calls
  "alignment_for_arguments_in_method_invocation" mapTo
    listInBracketsWrap(
      field(common::CALL_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS_IN_CALLS),
      field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    )
  "alignment_for_selector_in_method_invocation" mapTo
    compute(
      import = { value ->
        common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = false
        common.ALIGN_MULTILINE_CHAINED_METHODS = value.isAligned
        when (value.lineWrapPolicy) {
          LineWrapPolicy.DO_NOT_WRAP -> {
            common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
          }
          LineWrapPolicy.WRAP_WHERE_NECESSARY -> {
            if (value.isForceSplit) {
              common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
            }
          }
          LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
            common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
            common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
          }
          LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
          LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
          LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
        }
      },
      export = {
        EclipseWrapValue().apply {
          isAligned = common.ALIGN_MULTILINE_CHAINED_METHODS
          when (common.METHOD_CALL_CHAIN_WRAP) {
            CommonCodeStyleSettings.DO_NOT_WRAP -> {
              lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
            }
            CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
            }
            CommonCodeStyleSettings.WRAP_ALWAYS -> {
              if (common.WRAP_FIRST_METHOD_IN_CALL_CHAIN) {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
                isAligned = false
              }
              else
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
              isForceSplit = true
            }
            else /* chop down if long */ -> {
              if (common.WRAP_FIRST_METHOD_IN_CALL_CHAIN) {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
                isAligned = false
              }
              else
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
            }
          }
        }
      }
    ).convertWrap()
  "alignment_for_arguments_in_explicit_constructor_call" mapTo
    listInBracketsWrap(
      field(common::CALL_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS_IN_CALLS),
      field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    ).doNotImport()
  "alignment_for_arguments_in_allocation_expression" mapTo
    listInBracketsWrap(
      field(common::CALL_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS_IN_CALLS),
      field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    ).doNotImport()
  "alignment_for_arguments_in_qualified_allocation_expression" mapTo
    listInBracketsWrap(
      field(common::CALL_PARAMETERS_WRAP),
      field(common::ALIGN_MULTILINE_PARAMETERS_IN_CALLS),
      field(common::CALL_PARAMETERS_LPAREN_ON_NEXT_LINE)
    ).doNotImport()
  // endregion
  // region Binary expressions
  "alignment_for_multiplicative_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_multiplicative_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  "alignment_for_additive_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    )
  "wrap_before_additive_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .convertBoolean()
  "alignment_for_string_concatenation" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_string_concatenation" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  "alignment_for_shift_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_shift_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  "alignment_for_relational_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_relational_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  "alignment_for_bitwise_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_bitwise_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  "alignment_for_logical_operator" mapTo
    listWrap(
      field(common::BINARY_OPERATION_WRAP),
      field(common::ALIGN_MULTILINE_BINARY_OPERATION)
    ).doNotImport()
  "wrap_before_logical_operator" mapTo
    field(common::BINARY_OPERATION_SIGN_ON_NEXT_LINE)
      .doNotImport()
      .convertBoolean()
  // endregion
  // region Other expressions
  "alignment_for_conditional_expression" mapTo
    compute(
      import = { value ->
        when (value.lineWrapPolicy) {
          LineWrapPolicy.DO_NOT_WRAP -> {
            common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
          }
          LineWrapPolicy.WRAP_WHERE_NECESSARY,
          LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
            if (value.isForceSplit) {
              common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
            }
          }
          LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
          LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST,
          LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
        }
      },
      export = {
        EclipseWrapValue().apply {
          when (common.TERNARY_OPERATION_WRAP) {
            CommonCodeStyleSettings.DO_NOT_WRAP -> {
              lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
            }
            CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
            }
            CommonCodeStyleSettings.WRAP_ALWAYS -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              isForceSplit = true
            }
            else /* chop down if long */ -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
            }
          }
        }
      }
    ).convertWrap()
  "alignment_for_conditional_expression_chain" mapTo
    compute(
      import = { value ->
        common.ALIGN_MULTILINE_TERNARY_OPERATION = value.lineWrapPolicy != LineWrapPolicy.DO_NOT_WRAP
      },
      export = {
        EclipseWrapValue().apply {
          if (common.ALIGN_MULTILINE_TERNARY_OPERATION) {
            when (common.TERNARY_OPERATION_WRAP) {
              CommonCodeStyleSettings.DO_NOT_WRAP -> {
                lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
              }
              CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              }
              CommonCodeStyleSettings.WRAP_ALWAYS -> {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
                isForceSplit = true
              }
              else /* chop down if long */ -> {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              }
            }
          }
          else {
            lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
          }
        }
      }
    ).convertWrap()
  "wrap_before_conditional_operator" mapTo
    field(common::TERNARY_OPERATION_SIGNS_ON_NEXT_LINE)
      .convertBoolean()
  "alignment_for_assignment" mapTo
    compute(
      import = { value ->
        common.ALIGN_MULTILINE_ASSIGNMENT = value.isAligned
        when (value.lineWrapPolicy) {
          LineWrapPolicy.DO_NOT_WRAP -> {
            common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
          }
          LineWrapPolicy.WRAP_WHERE_NECESSARY,
          LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
            if (value.isForceSplit) {
              common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
            }
          }
          LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
          LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
          LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
            common.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
          }
        }
      },
      export = {
        EclipseWrapValue().apply {
          when (common.ASSIGNMENT_WRAP) {
            CommonCodeStyleSettings.DO_NOT_WRAP -> {
              lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
            }
            CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
            }
            CommonCodeStyleSettings.WRAP_ALWAYS -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              isForceSplit = true
            }
            else /* chop down if long */ -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
            }
          }
        }
      }
    ).convertWrap()
  "wrap_before_assignment_operator" mapTo
    field(common::PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE)
      .convertBoolean()
  "alignment_for_expressions_in_array_initializer" mapTo
    compute(
      import = { value ->
        common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false
        common.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = value.isAligned
        when (value.lineWrapPolicy) {
          LineWrapPolicy.DO_NOT_WRAP -> {
            common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
          }
          LineWrapPolicy.WRAP_WHERE_NECESSARY -> {
            if (value.isForceSplit) {
              common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
            }
          }
          LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
            common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
            common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
          }
          LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
          LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
            common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
            if (value.isForceSplit) {
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
          LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
        }
      },
      export = {
        EclipseWrapValue().apply {
          isAligned = common.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION
          when (common.ARRAY_INITIALIZER_WRAP) {
            CommonCodeStyleSettings.DO_NOT_WRAP -> {
              lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
            }
            CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
            }
            CommonCodeStyleSettings.WRAP_ALWAYS -> {
              if (common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE) {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
                isAligned = false
              }
              else {
                lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
              }
              isForceSplit = true
            }
            else /* chop down if long */ -> {
              lineWrapPolicy = if (common.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE)
                LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              else
                LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
            }
          }
        }
      }
    ).convertWrap()
  // endregion
  // region Statements
  "alignment_for_expressions_in_for_loop_header" mapTo
    listInBracketsWrap(
      field(common::FOR_STATEMENT_WRAP),
      field(common::ALIGN_MULTILINE_FOR),
      field(common::FOR_STATEMENT_LPAREN_ON_NEXT_LINE)
    )
  "alignment_for_compact_if" mapTo
    const(
      EclipseWrapValue().apply {
        lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
      }
    ).convertWrap()
  "alignment_for_compact_loops" mapTo
    const(
      EclipseWrapValue().apply {
        lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
      }
    ).convertWrap()
  "alignment_for_resources_in_try" mapTo
    listInBracketsWrap(
      field(common::RESOURCE_LIST_WRAP),
      field(common::ALIGN_MULTILINE_RESOURCES),
      field(common::RESOURCE_LIST_LPAREN_ON_NEXT_LINE)
    )
  "alignment_for_union_type_in_multicatch" mapTo
    listInBracketsWrap(
      field(custom::MULTI_CATCH_TYPES_WRAP),
      field(custom::ALIGN_TYPES_IN_MULTI_CATCH),
      const(false)
    )
  "wrap_before_or_operator_multicatch" mapTo
    const(false)
      .convertBoolean()
  "alignment_for_assertion_message" mapTo
    compute(
      import = { value ->
        when (value.lineWrapPolicy) {
          LineWrapPolicy.DO_NOT_WRAP -> {
            common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
          }
          LineWrapPolicy.WRAP_WHERE_NECESSARY,
          LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
            if (value.isForceSplit) {
              common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
            }
          }
          LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
          LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
            if (value.isForceSplit) {
              common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
            }
            else {
              common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
            }
          }
          LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
            common.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
          }
        }
      },
      export = {
        EclipseWrapValue().apply {
          when (common.ASSERT_STATEMENT_WRAP) {
            CommonCodeStyleSettings.DO_NOT_WRAP -> {
              lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
            }
            CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
            }
            CommonCodeStyleSettings.WRAP_ALWAYS -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
              isForceSplit = true
            }
            else /* chop down if long */ -> {
              lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
            }
          }
        }
      }
    ).convertWrap()
  "wrap_before_assertion_message_operator" mapTo
    field(common::ASSERT_STATEMENT_COLON_ON_NEXT_LINE)
      .convertBoolean()
  // endregion
  // region Parametrized types
  "alignment_for_parameterized_type_references" mapTo
    const(EclipseWrapValue.doNotWrap())
      .convertWrap()
  "alignment_for_type_arguments" mapTo
    const(EclipseWrapValue.doNotWrap())
      .convertWrap()
  "alignment_for_type_parameters" mapTo
    const(EclipseWrapValue.doNotWrap())
      .convertWrap()
  // endregion
  // region Annotations
  "alignment_for_annotations_on_package" mapTo
    annotationWrap(const(CommonCodeStyleSettings.DO_NOT_WRAP))
  "alignment_for_annotations_on_type" mapTo
    annotationWrap(field(common::CLASS_ANNOTATION_WRAP))
  "alignment_for_annotations_on_enum_constant" mapTo
    annotationWrap(const(CommonCodeStyleSettings.DO_NOT_WRAP))
  "alignment_for_annotations_on_field" mapTo
    annotationWrap(field(common::FIELD_ANNOTATION_WRAP))
  "alignment_for_annotations_on_method" mapTo
    annotationWrap(field(common::METHOD_ANNOTATION_WRAP))
  "alignment_for_annotations_on_local_variable" mapTo
    annotationWrap(field(common::VARIABLE_ANNOTATION_WRAP))
  "alignment_for_annotations_on_parameter" mapTo
    annotationWrap(field(common::PARAMETER_ANNOTATION_WRAP))
  "alignment_for_type_annotations" mapTo
    annotationWrap(field(common::FIELD_ANNOTATION_WRAP))
      .doNotImport()
  "alignment_for_arguments_in_annotation" mapTo
    listInBracketsWrap(
      field(custom::ANNOTATION_PARAMETER_WRAP),
      field(custom::ALIGN_MULTILINE_ANNOTATION_PARAMETERS),
      field(custom::NEW_LINE_AFTER_LPAREN_IN_ANNOTATION)
    )
  // endregion
  // region Module descriptions
  "alignment_for_module_statements" mapTo
    annotationWrap(const(CommonCodeStyleSettings.DO_NOT_WRAP))
  // endregion
  // endregion
}