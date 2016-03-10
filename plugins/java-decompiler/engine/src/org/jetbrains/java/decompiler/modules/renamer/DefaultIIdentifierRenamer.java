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
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DefaultIIdentifierRenamer implements IIdentifierRenamer {
  //Packages, classes, fields, and methods may have these names due to obfuscation, but they're not valid names in Java.
  private static final Set<String> RESERVED_JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
    "abstract", "do", "if", "package", "synchronized", "boolean", "double", "implements", "private", "this", "break", "else", "import",
    "protected", "throw", "byte", "extends", "instanceof", "public", "throws", "case", "false", "int", "return", "transient", "catch",
    "final", "interface", "short", "true", "char", "finally", "long", "static", "try", "class", "float", "native", "strictfp", "void",
    "const", "for", "new", "super", "volatile", "continue", "goto", "null", "switch", "while", "default", "assert", "enum"));
  //Packages and Classes cannot be extracted from an archive if they have a variation of any of these names.
  private static final Set<String> RESERVED_WINDOWS_NAMESPACE = new HashSet<>(Arrays.asList(
    "aux", "prn", "aux", "nul",
    "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
    "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

  private int classCounter = 0;
  private int fieldCounter = 0;
  private int methodCounter = 0;
  private final Set<String> setNonStandardClassNames = new HashSet<>();
  private final Set<String> setKnownClassNames = new HashSet<>();

  @Override
  public boolean shouldRenameClass(String simpleName, String fullName) {
    if (simpleName == null || fullName == null) {
      return true;
    }
    simpleName = simpleName.toLowerCase();
    fullName = fullName.toLowerCase();
    if (simpleName.length() == 0 || simpleName.length() <= 2
        || Character.isDigit(simpleName.charAt(0))
        || RESERVED_JAVA_KEYWORDS.contains(simpleName)
        || RESERVED_WINDOWS_NAMESPACE.contains(simpleName)
        || setKnownClassNames.contains(fullName)) {
      return true;
    }
    setKnownClassNames.add(fullName);
    return false;
  }

  @Override
  public boolean shouldRenameField(String className, String field, String descriptor) {
    return field == null || field.length() == 0 || field.length() <= 2
           || Character.isDigit(field.charAt(0)) || RESERVED_JAVA_KEYWORDS.contains(field);
  }

  @Override
  public boolean shouldRenameMethod(String className, String method, String descriptor) {
    return method == null || method.length() == 0 || method.length() <= 2
           || Character.isDigit(method.charAt(0)) || RESERVED_JAVA_KEYWORDS.contains(method);
  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  @Override
  public String getNextClassName(String simpleName, String fullName, int accessFlags) {
    if (fullName == null) {
      return ConverterHelper.getClassPrefix(accessFlags) + (classCounter++);
    }

    int index = 0;
    while (Character.isDigit(fullName.charAt(index))) {
      index++;
    }
    if (index == 0 || index == fullName.length()) {
      return ConverterHelper.getClassPrefix(accessFlags) + (classCounter++);
    }
    else {
      String name = fullName.substring(index);

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
}