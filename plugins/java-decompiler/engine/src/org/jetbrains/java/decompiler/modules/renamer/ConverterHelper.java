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
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConverterHelper implements IIdentifierRenamer {

  private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
    "abstract", "do", "if", "package", "synchronized", "boolean", "double", "implements", "private", "this", "break", "else", "import",
    "protected", "throw", "byte", "extends", "instanceof", "public", "throws", "case", "false", "int", "return", "transient", "catch",
    "final", "interface", "short", "true", "char", "finally", "long", "static", "try", "class", "float", "native", "strictfp", "void",
    "const", "for", "new", "super", "volatile", "continue", "goto", "null", "switch", "while", "default", "assert", "enum"));
  private static final Set<String> RESERVED_WINDOWS_NAMESPACE = new HashSet<>(Arrays.asList(
    "aux", "prn", "aux", "nul",
    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

  private int classCounter = 0;
  private int fieldCounter = 0;
  private int methodCounter = 0;
  private final Set<String> setNonStandardClassNames = new HashSet<>();

  @Override
  public boolean toBeRenamed(Type elementType, String className, String element, String descriptor) {
    String value = elementType == Type.ELEMENT_CLASS ? className : element;
    return value == null || value.length() == 0 || value.length() <= 2 || KEYWORDS.contains(value) || Character.isDigit(value.charAt(0))
      || elementType == Type.ELEMENT_CLASS && RESERVED_WINDOWS_NAMESPACE.contains(value.toLowerCase());
  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  @Override
  public String getNextClassName(String fullName, String shortName) {

    if (shortName == null) {
      return "class_" + (classCounter++);
    }

    int index = 0;
    while (Character.isDigit(shortName.charAt(index))) {
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
