// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeStyle.bean;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class CodeStyleBeanAccessorGenerator {

  private enum ValueType {
    BOOLEAN,
    INT,
    STRING,
    WRAP,
    BRACE_STYLE,
    FORCE_BRACES,
    ENUM,
    OTHER
  }

  private final static String BOOL_GETTER_PREFIX = "is";
  private final static String GETTER_PREFIX = "get";
  private final static String SETTER_PREFIX = "set";

  private final @NotNull Field myField;
  private final @NotNull Class myContainerClass;
  private final @NotNull String myFieldName;
  private final @NotNull Set<String> myImports = ContainerUtil.newHashSet();

  /**
   * Rules for method names which differ from field names for better readability.
   */
  private final static Map<String, String> FIELD_TO_METHOD_NAME_MAP = ContainerUtilRt.newHashMap();

  static {
    addMapping("SPACE_AFTER_SEMICOLON", "SpaceAfterForSemicolon");
    addMapping("SPACE_BEFORE_SEMICOLON", "SpaceBeforeForSemicolon");
    addMapping("LPAREN", "LeftParen");
    addMapping("RPAREN", "RightParen");
    addMapping("JD", "JavaDoc");
    addMapping("RBRACE", "RightBrace");
    addMapping("LBRACE", "LeftBrace");
    addMapping("INSTANCEOF", "InstanceOf");
    addMapping("DOWHILE", "DoWhile");
    addMapping("INDENT_SIZE", "Indent");
    addMapping("CONTINUATION_INDENT_SIZE", "ContinuationIndent");
    addMapping("PARM", "Param");
  }

  public CodeStyleBeanAccessorGenerator(@NotNull Field field) {
    myField = field;
    myContainerClass = field.getDeclaringClass();
    myFieldName = field.getName();
  }

  private static void addMapping(@NotNull String fieldName, @NotNull String methodName) {
    FIELD_TO_METHOD_NAME_MAP.put(fieldName, methodName);
  }

  String getGetterName() {
    return (getValueType() == ValueType.BOOLEAN ? BOOL_GETTER_PREFIX : GETTER_PREFIX) + fieldToMethodName();
  }

  private ValueType getValueType() {
    Class<?> fieldType = myField.getType();
    String name = fieldType.getName();
    if (fieldType.isPrimitive()) {
      if ("int".equals(name)) {
        if (myFieldName.endsWith("_WRAP")) {
          return ValueType.WRAP;
        }
        else if (myFieldName.endsWith("BRACE_STYLE")) {
          return ValueType.BRACE_STYLE;
        }
        else if (myFieldName.endsWith("_BRACE_FORCE")) {
          return ValueType.FORCE_BRACES;
        }
        return ValueType.INT;
      }
      else if ("boolean".equals(name)) {
        return ValueType.BOOLEAN;
      }
    }
    else if ("java.lang.String".equals(name)) {
      return ValueType.STRING;
    }
    else if (fieldType.isEnum()) {
      return ValueType.ENUM;
    }
    return ValueType.OTHER;
  }


  private String fieldToMethodName() {
    if (FIELD_TO_METHOD_NAME_MAP.containsKey(myFieldName)) return FIELD_TO_METHOD_NAME_MAP.get(myFieldName);
    StringBuilder nameBuilder = new StringBuilder();
    String[] chunks = myFieldName.split("_");
    for (String chunk : chunks) {
      appendNamePart(nameBuilder, chunk);
    }
    return nameBuilder.toString();
  }


  @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
  private static void appendNamePart(@NotNull StringBuilder nameBuilder, @NotNull String chunk) {
    if (chunk.length() > 0) {
      if (FIELD_TO_METHOD_NAME_MAP.containsKey(chunk)) {
        nameBuilder.append(FIELD_TO_METHOD_NAME_MAP.get(chunk));
      }
      else {
        nameBuilder.append(chunk.substring(0, 1).toUpperCase());
        if (chunk.length() > 1) {
          nameBuilder.append(chunk.substring(1).toLowerCase());
        }
      }
    }
  }

  String getSetterName() {
    return SETTER_PREFIX + fieldToMethodName();
  }

  @Nullable
  String getTypeString() {
    Class<?> fieldType = myField.getType();
    ValueType valueType = getValueType();
    switch (valueType) {
      case WRAP:
        myImports.add("com.intellij.formatting.WrapType");
        return "WrapType";
      case BRACE_STYLE:
        myImports.add("com.intellij.formatting.BraceStyle");
        return "BraceStyle";
      case FORCE_BRACES:
        myImports.add("com.intellij.formatting.ForceBraces");
        return "ForceBraces";
      case INT:
      case BOOLEAN:
        return fieldType.getSimpleName();
      case ENUM:
      case STRING:
        myImports.add(fieldType.getCanonicalName());
        return fieldType.getSimpleName();
      case OTHER:
        return null;
    }
    return null;
  }

  boolean isFieldSupported() {
    return myField.getType().getCanonicalName() != null &&
           myField.getAnnotation(Deprecated.class) == null;
  }

  String getContainerClassAccessor(boolean isWriteAccess) {
    if (myContainerClass == CommonCodeStyleSettings.class) {
      return "getCommonSettings()";
    }
    else if (myContainerClass == CommonCodeStyleSettings.IndentOptions.class) {
      return "getIndentOptions(" + isWriteAccess + ")";
    }
    myImports.add(myContainerClass.getName());
    return "getCustomSettings(" + myContainerClass.getSimpleName() + ".class)";
  }

  void generateGetter(StringBuilder output) {
    String typeString = getTypeString();
    if (typeString != null) {
      output
        .append("public ")
        .append(getTypeString())
        .append(' ')
        .append(getGetterName())
        .append("() {\n");
      final ValueType valueType = getValueType();
      if (valueType == ValueType.WRAP) {
        output
          .append("return intToWrapType(")
          .append(getContainerClassAccessor(false))
          .append(".").append(myFieldName)
          .append(");\n");
      }
      else if (valueType == ValueType.BRACE_STYLE || valueType == ValueType.FORCE_BRACES) {
        output
          .append("return ").append(typeString).append(".fromInt(")
          .append(getContainerClassAccessor(false))
          .append(".").append(myFieldName)
          .append(");\n");
      }
      else {
        output
          .append("return ")
          .append((getContainerClassAccessor(false)))
          .append(".").append(myFieldName).append(";\n");
      }
      output
        .append("}\n");
    }
    else {
      output.append("\n // TODO: Implement ")
            .append(myFieldName)
            .append(" getter manually (unsupported type).\n");
    }
  }

  void generateSetter(StringBuilder output) {
    final String typeString = getTypeString();
    if (typeString != null) {
      output
        .append("public void ")
        .append(getSetterName())
        .append("(")
        .append(typeString).append(" value")
        .append("){");
      output
        .append(getContainerClassAccessor(true))
        .append(".").append(myFieldName).append("= ");
      final ValueType valueType = getValueType();
      if (valueType == ValueType.WRAP) {
        output.append("wrapTypeToInt(");
      }
      output.append("value");
      if (valueType == ValueType.BRACE_STYLE || valueType == ValueType.FORCE_BRACES) {
        output.append(".intValue()");
      }
      if (valueType == ValueType.WRAP) {
        output.append(")");
      }
      output.append(";");
      output
        .append("}\n");
    }
    else {
      output.append("\n // TODO: Implement ")
            .append(myFieldName)
            .append(" setter manually (unsupported type).\n");
    }
  }

  @NotNull
  Set<String> getImports() {
    return myImports;
  }
}
