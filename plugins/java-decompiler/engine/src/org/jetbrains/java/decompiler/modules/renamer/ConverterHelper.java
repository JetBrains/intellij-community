/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.HashSet;

public class ConverterHelper implements IIdentifierRenamer {

  private static HashSet<String> setReserved = new HashSet<String>();

  static {
    setReserved.add("abstract");
    setReserved.add("do");
    setReserved.add("if");
    setReserved.add("package");
    setReserved.add("synchronized");
    setReserved.add("boolean");
    setReserved.add("double");
    setReserved.add("implements");
    setReserved.add("private");
    setReserved.add("this");
    setReserved.add("break");
    setReserved.add("else");
    setReserved.add("import");
    setReserved.add("protected");
    setReserved.add("throw");
    setReserved.add("byte");
    setReserved.add("extends");
    setReserved.add("instanceof");
    setReserved.add("public");
    setReserved.add("throws");
    setReserved.add("case");
    setReserved.add("false");
    setReserved.add("int");
    setReserved.add("return");
    setReserved.add("transient");
    setReserved.add("catch");
    setReserved.add("final");
    setReserved.add("interface");
    setReserved.add("short");
    setReserved.add("true");
    setReserved.add("char");
    setReserved.add("finally");
    setReserved.add("long");
    setReserved.add("static");
    setReserved.add("try");
    setReserved.add("class");
    setReserved.add("float");
    setReserved.add("native");
    setReserved.add("strictfp");
    setReserved.add("void");
    setReserved.add("const");
    setReserved.add("for");
    setReserved.add("new");
    setReserved.add("super");
    setReserved.add("volatile");
    setReserved.add("continue");
    setReserved.add("goto");
    setReserved.add("null");
    setReserved.add("switch");
    setReserved.add("while");
    setReserved.add("default");
    setReserved.add("assert");
    setReserved.add("enum");
  }

  private int class_counter = 0;

  private int field_counter = 0;

  private int method_counter = 0;

  private HashSet<String> setNonStandardClassNames = new HashSet<String>();

  public boolean toBeRenamed(int element_type, String classname, String element, String descriptor) {
    String value = (element_type == IIdentifierRenamer.ELEMENT_CLASS) ? classname : element;
    return value == null || value.length() == 0 || value.length() <= 2 || setReserved.contains(value) || Character.isDigit(value.charAt(0));
  }

  // TODO: consider possible conflicts with not renamed classes, fields and methods!
  // We should get all relevant information here.
  public String getNextClassname(String fullname, String shortname) {

    if (shortname == null) {
      return "class_" + (class_counter++);
    }

    int index = 0;
    while (Character.isDigit(shortname.charAt(index))) {
      index++;
    }

    if (index == 0 || index == shortname.length()) {
      return "class_" + (class_counter++);
    }
    else {
      String name = shortname.substring(index);

      if (setNonStandardClassNames.contains(name)) {
        return "Inner" + name + "_" + (class_counter++);
      }
      else {
        setNonStandardClassNames.add(name);
        return "Inner" + name;
      }
    }
  }

  public String getNextFieldname(String classname, String field, String descriptor) {
    return "field_" + (field_counter++);
  }

  public String getNextMethodname(String classname, String method, String descriptor) {
    return "method_" + (method_counter++);
  }

  // *****************************************************************************
  // static methods
  // *****************************************************************************

  public static String getSimpleClassName(String fullname) {
    return fullname.substring(fullname.lastIndexOf('/') + 1);
  }

  public static String replaceSimpleClassName(String fullname, String newname) {
    return fullname.substring(0, fullname.lastIndexOf('/') + 1) + newname;
  }
}
