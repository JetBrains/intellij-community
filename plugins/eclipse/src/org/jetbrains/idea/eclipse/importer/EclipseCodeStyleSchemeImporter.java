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


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public class EclipseCodeStyleSchemeImporter implements SchemeImporter<CodeStyleScheme>, EclipseXmlProfileElements {

  private static final Logger LOG = Logger.getInstance("#" + EclipseCodeStyleSchemeImporter.class.getName());
  
  private final static String PROGRAMMATIC_IMPORT_KEY = "<Programmatic>";
  
  private final EclipseImportMap myImportMap;

  public EclipseCodeStyleSchemeImporter() {
    myImportMap = new EclipseImportMap();
    myImportMap.load();    
  }

  @Override
  public String getSourceExtension() {
    return "xml";
  }

  @NotNull
  @Override
  public String[] readSchemeNames(@NotNull InputStream inputStream) throws SchemeImportException {
    final Set<String> names = new HashSet<String>();
    EclipseXmlProfileReader reader = new EclipseXmlProfileReader(new EclipseXmlProfileReader.OptionHandler() {
      @Override
      public void handleOption(@NotNull String eclipseKey, @NotNull String value) throws SchemeImportException {
        // Ignore
      }
      @Override
      public void handleName(String name) {
        names.add(name);
      }
    });
    reader.readSettings(inputStream);
    return ArrayUtil.toStringArray(names);
  }

  @Override
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
    if (key.contains("alignment") && value.matches("\\d*")) {
      return isAlignmentOn(value);
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

  private static boolean isAlignmentOn(@NotNull String value) {
    int packed = valueToInt(value);
    return (packed & 2) != 0;
  }
  
  private static void setProgrammatically(@NotNull Object object, @NotNull String key, @NotNull String value) throws SchemeImportException {
    if (object instanceof CodeStyleSettings) {
      CodeStyleSettings settings = (CodeStyleSettings)object;
      if (OPTION_REMOVE_JAVADOC_BLANK_LINES.equals(key)) {
        settings.JD_KEEP_EMPTY_LINES = !valueToBoolean(key, value);
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
    }
  }
}
