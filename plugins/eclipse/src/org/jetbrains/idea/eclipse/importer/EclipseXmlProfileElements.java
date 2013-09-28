/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.eclipse.importer;

/**
 * @author Rustam Vishnyakov
 */
@SuppressWarnings("UnusedDeclaration")
public interface EclipseXmlProfileElements {
  String PROFILES_TAG = "profiles";
  String PROFILE_TAG = "profile";
  String NAME_ATTR = "name";
  String SETTING_TAG = "setting";
  String ID_ATTR = "id";
  String VALUE_ATTR = "value";
  
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
}
