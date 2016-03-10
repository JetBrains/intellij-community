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

  private int packageCounter = 0;
  private int classCounter = 0;
  private int fieldCounter = 0;
  private int methodCounter = 0;
  private final Set<String> setNonStandardClassNames = new HashSet<>();
  private final Set<String> setKnownClassNames = new HashSet<>();
  private final Set<String> setKnownPackageNames = new HashSet<>();

  @Override
  public boolean shouldRenamePackage(String name) {
    if (name == null) {
      return false;
    }
    String lowerCaseName = name.toLowerCase();
    if (setKnownPackageNames.contains(lowerCaseName)) {
      return true;
    }
    setKnownPackageNames.add(lowerCaseName);
    for (String segment : lowerCaseName.split("/")) {
      if (RESERVED_WINDOWS_NAMESPACE.contains(segment)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean shouldRenameClass(String shortName, String fullName) {
    if (shortName == null || fullName == null) {
      return true;
    }
    try {
      return shortName.length() == 0 || shortName.length() <= 2
             || Character.isDigit(shortName.charAt(0))
             || KEYWORDS.contains(shortName)
             || RESERVED_WINDOWS_NAMESPACE.contains(shortName.toLowerCase())
             || setKnownClassNames.contains(fullName.toLowerCase());
    }
    finally {
      setKnownClassNames.add(fullName.toLowerCase());
    }
  }

  @Override
  public boolean shouldRenameField(String className, String field, String descriptor) {
    return field == null || field.length() == 0 || field.length() <= 2
           || Character.isDigit(field.charAt(0)) || KEYWORDS.contains(field);
  }

  @Override
  public boolean shouldRenameMethod(String className, String method, String descriptor) {
    return method == null || method.length() == 0 || method.length() <= 2
           || Character.isDigit(method.charAt(0)) || KEYWORDS.contains(method);
  }

  @Override
  public String getNextPackageName(String name) {
    return "package_" + (packageCounter++) + "/";
  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  @Override
  public String getNextClassName(String shortName, String fullName) {

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
    return getPackageName(fullName) + newName;
  }

  public static String getPackageName(String fullName) {
    return fullName.substring(0, fullName.lastIndexOf('/') + 1);
  }

  public static String replacePackageName(String fullName, String newName) {
    return newName + getSimpleClassName(fullName);
  }
}
