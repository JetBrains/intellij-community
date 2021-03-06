// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package org.jetbrains.idea.devkit.dom.generator;

import org.jetbrains.annotations.NonNls;

/**
 * @author Konstantin Bulenkov
 */
public class FieldDesc implements Comparable<FieldDesc> {
  final static int STR = 1;
  final static int BOOL = 2;
  final static int OBJ = 3;
  final static int ATTR = 4;
  final static int DOUBLE = 5;
  final static int SIMPLE = 6;

  public FieldDesc(String name, String def) {
    this.name = name;
    this.def = def;
  }

  public FieldDesc(int clType, String name, String type, String elementType, String def, boolean required) {
    this.clType = clType;
    this.name = name;
    this.type = type;
    this.elementType = elementType;
    this.def = def;
    this.required = required;
  }

  int clType = STR;
  String name;
  String type;
  String elementType;
  String def;
  boolean required;

  int idx;
  String tagName;
  String elementName;
  String comment;
  FieldDesc[] choice;
  boolean choiceOpt;

  String documentation;
  String simpleTypesString;
  int duplicateIndex = -1;
  int realIndex;
  String contentQualifiedName;

  @Override
  public int compareTo(FieldDesc o) {
    return name.compareTo(o.name);
  }

  @NonNls
  public String toString() {
    return "Field: " + name + ";" + type + ";" + elementName + ";" + elementType;
  }

}
