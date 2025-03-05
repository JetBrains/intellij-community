// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface EclipseFormatterOptions {
  @NonNls String VALUE_INSERT = "insert";
  @NonNls String VALUE_DO_NOT_INSERT = "do not insert";
  String VALUE_FALSE = "false";
  String VALUE_TRUE = "true";

  @NonNls String BRACES_ONE_LINE_NEVER = "one_line_never";
  @NonNls String BRACES_ONE_LINE_IF_EMPTY = "one_line_if_empty";
  @NonNls String BRACES_ONE_LINE_IF_SINGLE_ITEM = "one_line_if_single_item";
  @NonNls String BRACES_ONE_LINE_IF_IN_WIDTH_LIMIT = "one_line_always";
  @NonNls String BRACES_ONE_LINE_PRESERVE_STATE = "one_line_preserve";

  @NonNls String PARENS_COMMON_LINES = "common_lines";
  @NonNls String PARENS_SEPARATE_LINES_IF_NOT_EMPTY = "separate_lines_if_not_empty";
  @NonNls String PARENS_PRESERVE_POSITIONS = "preserve_positions";
  @NonNls String PARENS_SEPARATE_LINES_IF_WRAPPED = "separate_lines_if_wrapped";
  @NonNls String PARENS_SEPARATE_LINES = "separate_lines";

  @NonNls String TEXT_BLOCK_INDENT_DO_NOT_TOUCH = "3";
  @NonNls String TEXT_BLOCK_INDENT_BY_ONE = "2";
  @NonNls String TEXT_BLOCK_INDENT_DEFAULT = "0";
  @NonNls String TEXT_BLOCK_INDENT_ON_COLUMN = "1";

  @NonNls String VALUE_NEXT_LINE = "next_line";
  @NonNls String VALUE_NEXT_LINE_SHIFTED = "next_line_shifted";
  @NonNls String VALUE_END_OF_LINE = "end_of_line";
  @NonNls String VALUE_NEXT_LINE_IF_WRAPPED = "next_line_on_wrap";

  @NonNls String TAB_CHAR_TAB = "tab";
  @NonNls String TAB_CHAR_SPACE = "space";
  @NonNls String TAB_CHAR_MIXED = "mixed";

  String FORMATTER_OPTIONS_PREFIX = "org.eclipse.jdt.core.formatter";

  String OPTION_ON_DEMAND_IMPORT_THRESHOLD = "org.eclipse.jdt.ui.ondemandthreshold";
  String OPTION_ON_DEMAND_STATIC_IMPORT_THRESHOLD = "org.eclipse.jdt.ui.staticondemandthreshold";

  String OPTION_IMPORT_ORDER = "org.eclipse.jdt.ui.importorder";
  @NonNls String OPTION_FORMATTER_PROFILE = "formatter_profile";

  int DEFAULT_IMPORTS_THRESHOLD = 99;

  int LINE_WRAP_POLICY_MASK = 0x70;

  enum LineWrapPolicy {
    DO_NOT_WRAP(0x00),
    WRAP_WHERE_NECESSARY(0x10),
    WRAP_FIRST_OTHERS_WHERE_NECESSARY(0x20),
    WRAP_ALL_ON_NEW_LINE_EACH(0x30),
    WRAP_ALL_INDENT_EXCEPT_FIRST(0x40),
    WRAP_ALL_EXCEPT_FIRST(0x50);

    public final int bits;

    LineWrapPolicy(int value) { this.bits = value; }
  }

  int INDENT_POLICY_MASK = 0x06;

  enum IndentationPolicy {
    DEFAULT_INDENTATION(0x00),
    INDENT_ON_COLUMN(0x02),
    INDENT_BY_ONE(0x04);

    public final int bits;

    IndentationPolicy(int value) { this.bits = value; }
  }

  int FORCE_SPLIT_MASK = 0x01;

  static @NonNls @NotNull String completeId(@NonNls @NotNull String postfix) {
    return String.format("%s.%s", FORMATTER_OPTIONS_PREFIX, postfix);
  }
}
