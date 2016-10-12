/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.importer.colors;

public interface EclipseColorThemeElements {
  String BACKGROUND_SUFFIX = "background";
  
  String COLOR_THEME_TAG = "colorTheme";
  String SEARCH_RESULT_TAG = "searchResultIndication";
  String FILTER_SEARCH_RESULT_TAG = "filteredSearchResultIndication";
  String OCCURENCE_TAG = "occurrenceIndication";
  String WRITE_OCCURENCE_TAG = "writeOccurrenceIndication";
  String FIND_SCOPE_TAG = "findScope";
  String DELETION_INDICATION_TAG = "deletionIndication";
  String SOURCE_HOVER_BACKGROUND_TAG = "sourceHoverBackground";
  String SINGLE_LINE_COMMENT_TAG = "singleLineComment";
  String MULTI_LINE_COMMENT_TAG = "multiLineComment";
  String COMMENT_TASK_TAG = "commentTaskTag";
  String JAVADOC_TAG = "javadoc";
  String JAVADOC_LINK_TAG = "javadocLink";
  String JAVADOC_TAG_TAG = "javadocTag";
  String JAVADOC_KEYWORD_TAG = "javadocKeyword";
  String CLASS_TAG = "class";
  String INTERFACE_TAG = "interface";
  String METHOD_TAG = "method";
  String METHOD_DECLARATION = "methodDeclaration";
  String BRACKET = "bracket";
  String NUMBER_TAG = "number";
  String STRING_TAG = "string";
  String OPERATOR_TAG = "operator";
  String KEYWORD_TAG = "keyword";
  String ANNOTATION_TAG = "annotation";
  String STATIC_METHOD_TAG = "staticMethod";
  String LOCAL_VARIABLE_TAG = "localVariable";
  String LOCAL_VARIABLE_DECL_TAG = "localVariableDeclaration";
  String FIELD_TAG = "field";
  String STATIC_FIELD_TAG = "staticField";
  String STATIC_FINAL_FIELD_TAG = "staticFinalField";
  String DEPRECATED_MEMBER_TAG = "deprecatedMember";
  String ENUM_TAG = "enum";
  String INHERITED_METHOD_TAG = "inheritedMethod";
  String ABSTRACT_METHOD_TAG = "abstractMethod";
  String PARAMETER_VAR_TAG = "parameterVariable";
  String TYPE_ARG_TAG = "typeArgument";
  String TYPE_PARAM_TAG = "typeParameter";
  String CONST_TAG = "constant";
  String BACKGROUND_TAG = "background";
  String CURR_LINE_TAG = "currentLine";
  String FOREGROUND_TAG = "foreground";
  String LINE_NUMBER_TAG = "lineNumber";
  String SELECTION_BACKGROUND_TAG = "selectionBackground";
  String SELECTION_FOREGROUND_TAG = "selectionForeground";
  
  String NAME_ATTR = "name";
  String COLOR_ATTR = "color";
  String BOLD_ATTR = "bold";
  String ITALIC_ATTR = "italic";
  String UNDERLINE_ATTR = "underline";
  String STRIKETHROUGH_ATTR = "strikethrough";
}
