// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ConverterHelper implements IIdentifierRenamer {
  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
    "abstract", "do", "if", "package", "synchronized", "boolean", "double", "implements", "private", "this", "break", "else", "import",
    "protected", "throw", "byte", "extends", "instanceof", "public", "throws", "case", "false", "int", "return", "transient", "catch",
    "final", "interface", "short", "true", "char", "finally", "long", "static", "try", "class", "float", "native", "strictfp", "void",
    "const", "for", "new", "super", "volatile", "continue", "goto", "null", "switch", "while", "default", "assert", "enum"));
  private static final Set<String> RESERVED_WINDOWS_NAMESPACE = new HashSet<>(Arrays.asList(
    "con", "prn", "aux", "nul",
    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

  private int classCounter = 0;
  private int fieldCounter = 0;
  private int methodCounter = 0;
  private final Set<String> setNonStandardClassNames = new HashSet<>();

  @Override
  public boolean toBeRenamed(Type elementType, String className, String element, String descriptor) {
    String value = elementType == Type.ELEMENT_CLASS ? className : element;
    return value == null ||
           value.length() <= 2 ||
           Character.isDigit(value.charAt(0)) ||
           KEYWORDS.contains(value) ||
           elementType == Type.ELEMENT_CLASS && (
             RESERVED_WINDOWS_NAMESPACE.contains(value.toLowerCase(Locale.US)) ||
             value.length() > 255 - ".class".length());
  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  @Override
  public String getNextClassName(String fullName, String shortName) {
    if (shortName == null) {
      return "class_" + (classCounter++);
    }

    int index = 0;
    while (index < shortName.length() && Character.isDigit(shortName.charAt(index))) {
      index++;
    }

    if (index == 0 || index == shortName.length()) {
      return "class_" + (classCounter++);
    }
    else {
      String name = shortName.substring(index);
      if (setNonStandardClassNames.contains(name)) {
        return "Inner" + name + "_" + (classCounter++);
      }
      else {
        setNonStandardClassNames.add(name);
        return "Inner" + name;
      }
    }
  }

  @Override
  public String getNextFieldName(String className, String field, String descriptor) {
    return "field_" + (fieldCounter++);
  }

  @Override
  public String getNextMethodName(String className, String method, String descriptor) {
    return "method_" + (methodCounter++);
  }

  // *****************************************************************************
  // static methods
  // *****************************************************************************

  public static String getSimpleClassName(String fullName) {
    return fullName.substring(fullName.lastIndexOf('/') + 1);
  }

  public static String replaceSimpleClassName(String fullName, String newName) {
    return fullName.substring(0, fullName.lastIndexOf('/') + 1) + newName;
  }
}