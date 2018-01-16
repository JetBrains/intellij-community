/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.eclipse.importer;

public interface EclipseFormatterOptions {
  String VALUE_INSERT = "insert";
  String VALUE_DO_NOT_INSERT = "do not insert";
  String VALUE_FALSE = "false";
  String VALUE_TRUE = "true";

  String VALUE_NEXT_LINE = "next_line";
  String VALUE_NEXT_LINE_SHIFTED = "next_line_shifted";
  String VALUE_END_OF_LINE = "end_of_line";
  String VALUE_NEXT_LINE_IF_WRAPPED = "next_line_on_wrap";

  String TAB_CHAR_TAB = "tab";
  String TAB_CHAR_SPACE = "space";
  String TAB_CHAR_MIXED = "mixed";

  String OPTION_SPACE_AFTER_BINARY_OPERATOR = "org.eclipse.jdt.core.formatter.insert_space_after_binary_operator";
  String OPTION_REMOVE_JAVADOC_BLANK_LINES = "org.eclipse.jdt.core.formatter.comment.clear_blank_lines_in_javadoc_comment";
  String OPTION_NEW_LINE_AT_EOF = "org.eclipse.jdt.core.formatter.insert_new_line_at_end_of_file_if_missing";
  String OPTION_INDENT_CLASS_BODY_DECL = "org.eclipse.jdt.core.formatter.indent_body_declarations_compare_to_type_header";
  String OPTION_TAB_CHAR = "org.eclipse.jdt.core.formatter.tabulation.char";
  String OPTION_CONTINUATION_INDENT = "org.eclipse.jdt.core.formatter.continuation_indentation";
  String OPTION_TAB_SIZE = "org.eclipse.jdt.core.formatter.tabulation.size";

  String OPTION_ALIGN_EXPR_IN_ARRAY_INITIALIZER = "org.eclipse.jdt.core.formatter.alignment_for_expressions_in_array_initializer";
  String OPTION_ALIGN_ARGS_IN_ANNOTATION = "org.eclipse.jdt.core.formatter.alignment_for_arguments_in_annotation";
  String OPTION_ALIGN_ARGS_IN_METHOD_INVOCATION = "org.eclipse.jdt.core.formatter.alignment_for_arguments_in_method_invocation";
  String OPTION_ALIGN_SUPERCLASS_IN_TYPE_DECL = "org.eclipse.jdt.core.formatter.alignment_for_superclass_in_type_declaration";
  String OPTION_ALIGN_INTERFACES_IN_TYPE_DECL = "org.eclipse.jdt.core.formatter.alignment_for_superinterfaces_in_type_declaration";
  String OPTION_ALIGN_ASSIGNMENT = "org.eclipse.jdt.core.formatter.alignment_for_assignment";
  String OPTION_ALIGN_METHOD_DECL_PARAMETERS = "org.eclipse.jdt.core.formatter.alignment_for_parameters_in_method_declaration";
  String OPTION_ALIGN_BINARY_EXPR = "org.eclipse.jdt.core.formatter.alignment_for_binary_expression";
  String OPTION_ALIGN_THROWS_IN_METHOD_DECL = "org.eclipse.jdt.core.formatter.alignment_for_throws_clause_in_method_declaration";
  String OPTION_ALIGN_RESOURCES_IN_TRY = "org.eclipse.jdt.core.formatter.alignment_for_resources_in_try";
  String OPTION_ALIGN_METHOD_DECL = "org.eclipse.jdt.core.formatter.alignment_for_method_declaration";
  String OPTION_ALIGN_CHAINED_CALLS = "org.eclipse.jdt.core.formatter.alignment_for_selector_in_method_invocation";
  String OPTION_ALIGN_CONDITIONALS = "org.eclipse.jdt.core.formatter.alignment_for_conditional_expression";

  String OPTION_BLANK_LINES_BEFORE_FIRST_DECLARATION_IN_CLASS = "org.eclipse.jdt.core.formatter.blank_lines_before_first_class_body_declaration";
  String OPTION_EMPTY_LINES_TO_PRESERVE = "org.eclipse.jdt.core.formatter.number_of_empty_lines_to_preserve";

  String OPTION_SPACE_AFTER_CLOSING_BRACE_IN_BLOCK = "org.eclipse.jdt.core.formatter.insert_space_after_closing_brace_in_block";
  String OPTION_SPACE_BEFORE_OPENING_BRACE_IN_BLOCK = "org.eclipse.jdt.core.formatter.insert_space_before_opening_brace_in_block";

  String OPTION_JOIN_WRAPPED_LINES = "org.eclipse.jdt.core.formatter.join_wrapped_lines";

  int WRAP_MASK = 0x70;

  int DO_NOT_WRAP                       = 0x00;
  int WRAP_WHERE_NECESSARY              = 0x10;
  int WRAP_FIRST_OTHERS_WHERE_NECESSARY = 0x20;
  int WRAP_ALL_ON_NEW_LINE_EACH         = 0x30;
  int WRAP_ALL_INDENT_EXCEPT_FIRST      = 0x40;
  int WRAP_ALL_EXCEPT_FIRST             = 0x50;
}
