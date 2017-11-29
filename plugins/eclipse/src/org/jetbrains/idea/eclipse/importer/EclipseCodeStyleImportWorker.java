/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * @author Irina.Chernushina on 4/21/2015.
 */
public class EclipseCodeStyleImportWorker implements EclipseXmlProfileElements {
  private static final Logger LOG = Logger.getInstance(EclipseCodeStyleImportWorker.class);

  final static String PROGRAMMATIC_IMPORT_KEY = "<Programmatic>";

  private final EclipseImportMap myImportMap;

  public EclipseCodeStyleImportWorker() {
    myImportMap = new EclipseImportMap();
    myImportMap.load();
  }

  public void importScheme(@NotNull InputStream inputStream, final @Nullable String sourceScheme, final CodeStyleScheme scheme)
    throws SchemeImportException {
    final CodeStyleSettings settings = scheme.getCodeStyleSettings();
    EclipseXmlProfileReader reader = new EclipseXmlProfileReader(new EclipseXmlProfileReader.OptionHandler() {
      private String myCurrScheme;

      @Override
      public void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException {
        if (sourceScheme == null || myCurrScheme != null && myCurrScheme.equals(sourceScheme)) {
          setCodeStyleOption(settings, eclipseKey, value);
        }
      }
      @Override
      public void handleName(String name) {
        myCurrScheme = name;
      }
    });
    reader.readSettings(inputStream);
  }

  private void setCodeStyleOption(@NotNull CodeStyleSettings settings, @NotNull String key, @NotNull String value)
    throws SchemeImportException {
    EclipseImportMap.ImportDescriptor importDescriptor = myImportMap.getImportDescriptor(key);
    if (importDescriptor != null) {
      try {
        if (importDescriptor.isLanguageSpecific()) {
          if (importDescriptor.isCustomField()) {
            JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
            setValue(javaSettings, key, importDescriptor.getFieldName(), value);
          }
          else {
            CommonCodeStyleSettings languageSettings = settings.getCommonSettings(importDescriptor.getLanguage());
            if (languageSettings != null) {
              if (importDescriptor.isIndentOptions()) {
                CommonCodeStyleSettings.IndentOptions indentOptions = languageSettings.getIndentOptions();
                if (indentOptions != null) {
                  setValue(indentOptions, key, importDescriptor.getFieldName(), value);
                }
              }
              else {
                setValue(languageSettings, key, importDescriptor.getFieldName(), value);
              }
            }
          }
        }
        else {
          setValue(settings, key, importDescriptor.getFieldName(), value);
        }
      }
      catch (Exception e) {
        throw new SchemeImportException(e);
      }
    }
  }

  private static void setValue(Object object, String key, String fieldName, String value) throws SchemeImportException {
    if (PROGRAMMATIC_IMPORT_KEY.equalsIgnoreCase(fieldName)) {
      setProgrammatically(object, key, value);
      return;
    }
    try {
      Field targetField = object.getClass().getField(fieldName);
      Class<?> fieldType = targetField.getType();
      if (fieldType.isPrimitive()) {
        if (Boolean.TYPE.equals(fieldType)) {
          targetField.setBoolean(object, valueToBoolean(key, value));
        }
        else if (Integer.TYPE.equals(fieldType)) {
          targetField.setInt(object, valueToInt(value));
        }
      }
      else if (fieldType.equals(String.class)) {
        targetField.set(object, value);
      }
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (NoSuchFieldException e) {
      LOG.error("Field '" + fieldName + "' does not exist in " + object.getClass().getName(), e);
    }
  }

  private static boolean valueToBoolean(@NotNull String key, @NotNull String value) throws SchemeImportException {
    if (VALUE_INSERT.equals(value) ||
        VALUE_TRUE.equals(value)) {
      return true;
    }
    if (!(VALUE_DO_NOT_INSERT.equals(value) ||
          VALUE_FALSE.equals(value))) {
      throw new SchemeImportException("Unrecognized boolean value: " + value + ", key: " + key);
    }
    return false;
  }

  private static int valueToInt(@NotNull String value) {
    if (VALUE_END_OF_LINE.equals(value)) return CommonCodeStyleSettings.END_OF_LINE;
    if (VALUE_NEXT_LINE.equals(value)) return CommonCodeStyleSettings.NEXT_LINE;
    if (VALUE_NEXT_LINE_SHIFTED.equals(value)) return CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    if (VALUE_NEXT_LINE_IF_WRAPPED.equals(value)) return CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    return Integer.parseInt(value);
  }

  private static class AlignmentAndWrapValueDecoder {
    int myEncodedValue;

    public AlignmentAndWrapValueDecoder(int encodedValue) {
      myEncodedValue = encodedValue;
    }

    public int getWrapType() {
      switch (getEclipseWrap()) {
        case WRAP_WHERE_NECESSARY:
        case WRAP_FIRST_OTHERS_WHERE_NECESSARY:
          return CommonCodeStyleSettings.WRAP_AS_NEEDED;
        case WRAP_ALL_EXCEPT_FIRST:
        case WRAP_ALL_INDENT_EXCEPT_FIRST:
        case WRAP_ALL_ON_NEW_LINE_EACH:
          return CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
      }
      return CommonCodeStyleSettings.DO_NOT_WRAP;
    }

    public int getFirstElementWrapType() {
      return isNewLineBeforeFirst() ? CommonCodeStyleSettings.WRAP_ALWAYS :
             getEclipseWrap() == WRAP_ALL_EXCEPT_FIRST ? CommonCodeStyleSettings.DO_NOT_WRAP :
             CommonCodeStyleSettings.WRAP_AS_NEEDED;
    }

    public boolean isFirstElementWrapped() {
      int eclipseWrapValue = getEclipseWrap();
      return eclipseWrapValue == WRAP_FIRST_OTHERS_WHERE_NECESSARY ||
             eclipseWrapValue == WRAP_ALL_INDENT_EXCEPT_FIRST ||
             eclipseWrapValue == WRAP_ALL_ON_NEW_LINE_EACH ||
             isNewLineBeforeFirst();
    }

    public boolean isNewLineBeforeFirst() {
      return (myEncodedValue & 1) != 0;
    }

    public boolean isAlignmentOn() {
      return (myEncodedValue & 2) != 0;
    }

    public int getEclipseWrap() {
      return myEncodedValue & WRAP_MASK;
    }
  }

  private static void setProgrammatically(@NotNull Object object, @NotNull String key, @NotNull String value) throws SchemeImportException {
    if (key.contains("alignment") && value.matches("\\d*") && object instanceof CommonCodeStyleSettings) {
      if (setAlignmentAndWrappingOptions((CommonCodeStyleSettings)object, key, value)) return;
    }
    if (object instanceof CodeStyleSettings) {
      CodeStyleSettings settings = (CodeStyleSettings)object;
      if (OPTION_REMOVE_JAVADOC_BLANK_LINES.equals(key)) {
        settings.getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_LINES = !valueToBoolean(key, value);
      }
      else if (OPTION_NEW_LINE_AT_EOF.equals(key)) {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        editorSettings.setEnsureNewLineAtEOF(valueToBoolean(key, value));
      }
    }
    else if (object instanceof CommonCodeStyleSettings) {
      CommonCodeStyleSettings commonSettings = (CommonCodeStyleSettings)object;
      if (OPTION_SPACE_AFTER_BINARY_OPERATOR.equals(key)) {
        boolean addSpace = valueToBoolean(key, value);
        commonSettings.SPACE_AROUND_ADDITIVE_OPERATORS =
        commonSettings.SPACE_AROUND_BITWISE_OPERATORS =
        commonSettings.SPACE_AROUND_LOGICAL_OPERATORS =
        commonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS =
        commonSettings.SPACE_AROUND_RELATIONAL_OPERATORS =
        commonSettings.SPACE_AROUND_SHIFT_OPERATORS =
        commonSettings.SPACE_AROUND_EQUALITY_OPERATORS =
          addSpace;
      }
      else if (OPTION_INDENT_CLASS_BODY_DECL.equals(key)) {
        commonSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = !valueToBoolean(key, value);
      }
      else if (OPTION_BLANK_LINES_BEFORE_FIRST_DECLARATION_IN_CLASS.equals(key)) {
        int intValue = valueToInt(value);
        commonSettings.BLANK_LINES_AFTER_CLASS_HEADER = intValue;
        commonSettings.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = intValue;
      }
      else if (OPTION_EMPTY_LINES_TO_PRESERVE.equals(key)) {
        int intValue = valueToInt(value);
        commonSettings.KEEP_BLANK_LINES_IN_CODE = intValue;
        commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = intValue;
        commonSettings.KEEP_BLANK_LINES_BEFORE_RBRACE = intValue;
      }
      else if (OPTION_SPACE_AFTER_CLOSING_BRACE_IN_BLOCK.equals(key)) {
        boolean insertSpace = valueToBoolean(key, value);
        commonSettings.SPACE_BEFORE_ELSE_KEYWORD = insertSpace;
        commonSettings.SPACE_BEFORE_CATCH_KEYWORD = insertSpace;
        commonSettings.SPACE_BEFORE_FINALLY_KEYWORD = insertSpace;
      }
      else if (OPTION_SPACE_BEFORE_OPENING_BRACE_IN_BLOCK.equals(key)) {
        boolean insertSpace = valueToBoolean(key, value);
        commonSettings.SPACE_BEFORE_IF_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_FOR_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_WHILE_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_DO_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_TRY_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_CATCH_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_FINALLY_LBRACE = insertSpace;
        commonSettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = insertSpace;
      }
      else if (OPTION_JOIN_WRAPPED_LINES.equals(key)) {
        commonSettings.KEEP_LINE_BREAKS = !valueToBoolean(key, value);
      }
    }
    else if (object instanceof CommonCodeStyleSettings.IndentOptions) {
      CommonCodeStyleSettings.IndentOptions indentOptions = (CommonCodeStyleSettings.IndentOptions)object;
      if (OPTION_TAB_CHAR.equals(key)) {
        if (TAB_CHAR_TAB.equals(value) || TAB_CHAR_MIXED.equals(value)) {
          indentOptions.USE_TAB_CHARACTER = true;
        }
        else if (TAB_CHAR_SPACE.equals(value)) {
          indentOptions.USE_TAB_CHARACTER = false;
        }
      }
      else if (OPTION_CONTINUATION_INDENT.equals(key)) {
        indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.TAB_SIZE * valueToInt(value);
      }
      else if (OPTION_TAB_SIZE.equals(key)) {
        int newTabSize = valueToInt(value);
        int continuationTabs = indentOptions.TAB_SIZE > 0 ? indentOptions.CONTINUATION_INDENT_SIZE / indentOptions.TAB_SIZE : -1;
        indentOptions.TAB_SIZE = newTabSize;
        if (continuationTabs >= 0) {
          indentOptions.CONTINUATION_INDENT_SIZE = continuationTabs * newTabSize;
        }
      }
    }
  }

  private static boolean setAlignmentAndWrappingOptions(@NotNull CommonCodeStyleSettings settings,
                                                        @NotNull String key,
                                                        @NotNull String value) {
    int encodedValue = Integer.parseInt(value);
    AlignmentAndWrapValueDecoder decoder = new AlignmentAndWrapValueDecoder(encodedValue);
    if (OPTION_ALIGN_ARGS_IN_ANNOTATION.equals(key)) {
      settings.FIELD_ANNOTATION_WRAP =
      settings.METHOD_ANNOTATION_WRAP =
      settings.PARAMETER_ANNOTATION_WRAP =
      settings.VARIABLE_ANNOTATION_WRAP =
      settings.CLASS_ANNOTATION_WRAP = decoder.getWrapType();
      return true;
    }
    else if (OPTION_ALIGN_EXPR_IN_ARRAY_INITIALIZER.equals(key)) {
      settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = decoder.isAlignmentOn();
      settings.ARRAY_INITIALIZER_WRAP = decoder.getWrapType();
      settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = decoder.isFirstElementWrapped();
      return true;
    }
    else if (OPTION_ALIGN_ARGS_IN_METHOD_INVOCATION.equals(key)) {
      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = decoder.isAlignmentOn();
      settings.CALL_PARAMETERS_WRAP = decoder.getWrapType();
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = decoder.isFirstElementWrapped();
      return true;
    }
    else if (OPTION_ALIGN_INTERFACES_IN_TYPE_DECL.equals(key)) {
      settings.ALIGN_MULTILINE_EXTENDS_LIST = decoder.isAlignmentOn();
      settings.EXTENDS_KEYWORD_WRAP = decoder.getFirstElementWrapType();
      settings.EXTENDS_LIST_WRAP = decoder.getWrapType();
      return true;
    }
    else if (OPTION_ALIGN_ASSIGNMENT.equals(key)) {
      settings.ALIGN_MULTILINE_ASSIGNMENT = decoder.isAlignmentOn();
      settings.ASSIGNMENT_WRAP = decoder.getWrapType();
      return true;
    }
    else if (OPTION_ALIGN_METHOD_DECL_PARAMETERS.equals(key)) {
      settings.ALIGN_MULTILINE_PARAMETERS = decoder.isAlignmentOn();
      settings.METHOD_PARAMETERS_WRAP = decoder.getWrapType();
      settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = decoder.isFirstElementWrapped();
      return true;
    }
    else if (OPTION_ALIGN_BINARY_EXPR.equals(key)) {
      settings.ALIGN_MULTILINE_BINARY_OPERATION = decoder.isAlignmentOn();
      settings.BINARY_OPERATION_WRAP = decoder.getWrapType();
      return true;
    }
    else if (OPTION_ALIGN_THROWS_IN_METHOD_DECL.equals(key)) {
      settings.ALIGN_MULTILINE_THROWS_LIST = decoder.isAlignmentOn();
      settings.THROWS_KEYWORD_WRAP = decoder.getFirstElementWrapType();
      settings.THROWS_LIST_WRAP = decoder.getWrapType();
      return true;
    }
    else if (OPTION_ALIGN_RESOURCES_IN_TRY.equals(key)) {
      settings.ALIGN_MULTILINE_RESOURCES = decoder.isAlignmentOn();
      settings.RESOURCE_LIST_WRAP = decoder.getWrapType();
      settings.RESOURCE_LIST_LPAREN_ON_NEXT_LINE = decoder.isFirstElementWrapped();
      return true;
    }
    else if (OPTION_ALIGN_CHAINED_CALLS.equals(key)) {
      settings.METHOD_CALL_CHAIN_WRAP = decoder.getWrapType();
      settings.ALIGN_MULTILINE_CHAINED_METHODS = decoder.isAlignmentOn();
    }
    else if (OPTION_ALIGN_CONDITIONALS.equals(key)) {
      settings.TERNARY_OPERATION_WRAP = decoder.getWrapType();
      settings.ALIGN_MULTILINE_TERNARY_OPERATION = decoder.isAlignmentOn();
    }
    return false;
  }
}
