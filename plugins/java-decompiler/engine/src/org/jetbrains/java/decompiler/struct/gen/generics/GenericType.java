// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.ArrayList;
import java.util.List;

public class GenericType {

  public static final int WILDCARD_EXTENDS = 1;
  public static final int WILDCARD_SUPER = 2;
  public static final int WILDCARD_UNBOUND = 3;
  public static final int WILDCARD_NO = 4;

  public final int type;
  public final int arrayDim;
  public final String value;

  private final List<GenericType> enclosingClasses = new ArrayList<>();
  private final List<GenericType> arguments = new ArrayList<>();
  private final List<Integer> wildcards = new ArrayList<>();

  public GenericType(int type, int arrayDim, String value) {
    this.type = type;
    this.arrayDim = arrayDim;
    this.value = value;
  }

  public GenericType(String signature) {
    int type = 0;
    int arrayDim = 0;
    String value = null;

    int index = 0;
    loop:
    while (index < signature.length()) {
      switch (signature.charAt(index)) {
        case '[':
          arrayDim++;
          break;

        case 'T':
          type = CodeConstants.TYPE_GENVAR;
          value = signature.substring(index + 1, signature.length() - 1);
          break loop;

        case 'L':
          type = CodeConstants.TYPE_OBJECT;
          signature = signature.substring(index + 1, signature.length() - 1);

          while (true) {
            String cl = getNextClassSignature(signature);

            String name = cl;
            String args = null;

            int argStart = cl.indexOf("<");
            if (argStart >= 0) {
              name = cl.substring(0, argStart);
              args = cl.substring(argStart + 1, cl.length() - 1);
            }

            if (cl.length() < signature.length()) {
              signature = signature.substring(cl.length() + 1); // skip '.'
              GenericType type11 = new GenericType(CodeConstants.TYPE_OBJECT, 0, name);
              parseArgumentsList(args, type11);
              enclosingClasses.add(type11);
            }
            else {
              value = name;
              parseArgumentsList(args, this);
              break;
            }
          }

          break loop;

        default:
          value = signature.substring(index, index + 1);
          type = VarType.getType(value.charAt(0));
      }

      index++;
    }

    this.type = type;
    this.arrayDim = arrayDim;
    this.value = value;
  }

  private static String getNextClassSignature(String value) {
    int counter = 0;
    int index = 0;

    loop:
    while (index < value.length()) {
      switch (value.charAt(index)) {
        case '<':
          counter++;
          break;
        case '>':
          counter--;
          break;
        case '.':
          if (counter == 0) {
            break loop;
          }
      }

      index++;
    }

    return value.substring(0, index);
  }

  private static void parseArgumentsList(String value, GenericType type) {
    if (value == null) {
      return;
    }

    while (value.length() > 0) {
      String typeStr = getNextType(value);
      int len = typeStr.length();
      int wildcard = WILDCARD_NO;

      switch (typeStr.charAt(0)) {
        case '*':
          wildcard = WILDCARD_UNBOUND;
          break;
        case '+':
          wildcard = WILDCARD_EXTENDS;
          break;
        case '-':
          wildcard = WILDCARD_SUPER;
          break;
      }

      type.getWildcards().add(wildcard);

      if (wildcard != WILDCARD_NO) {
        typeStr = typeStr.substring(1);
      }

      type.getArguments().add(typeStr.length() == 0 ? null : new GenericType(typeStr));

      value = value.substring(len);
    }
  }

  public static String getNextType(String value) {
    int counter = 0;
    int index = 0;

    boolean contMode = false;

    loop:
    while (index < value.length()) {
      switch (value.charAt(index)) {
        case '*':
          if (!contMode) {
            break loop;
          }
          break;
        case 'L':
        case 'T':
          if (!contMode) {
            contMode = true;
          }
        case '[':
        case '+':
        case '-':
          break;
        default:
          if (!contMode) {
            break loop;
          }
          break;
        case '<':
          counter++;
          break;
        case '>':
          counter--;
          break;
        case ';':
          if (counter == 0) {
            break loop;
          }
      }

      index++;
    }

    return value.substring(0, index + 1);
  }

  public GenericType decreaseArrayDim() {
    assert arrayDim > 0 : this;
    return new GenericType(type, arrayDim - 1, value);
  }

  public List<GenericType> getArguments() {
    return arguments;
  }

  public List<GenericType> getEnclosingClasses() {
    return enclosingClasses;
  }

  public List<Integer> getWildcards() {
    return wildcards;
  }
}
