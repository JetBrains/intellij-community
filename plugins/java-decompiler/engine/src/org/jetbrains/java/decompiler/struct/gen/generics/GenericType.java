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
package org.jetbrains.java.decompiler.struct.gen.generics;

import org.jetbrains.java.decompiler.code.CodeConstants;

import java.util.ArrayList;
import java.util.List;

public class GenericType {

  public static final int WILDCARD_EXTENDS = 1;
  public static final int WILDCARD_SUPER = 2;
  public static final int WILDCARD_UNBOUND = 3;
  public static final int WILDCARD_NO = 4;

  public int type;

  public int arraydim;

  public String value;


  private List<GenericType> enclosingClasses = new ArrayList<GenericType>();

  private List<GenericType> arguments = new ArrayList<GenericType>();

  private List<Integer> wildcards = new ArrayList<Integer>();


  public GenericType(int type, int arraydim, String value) {
    this.type = type;
    this.arraydim = arraydim;
    this.value = value;
  }


  public GenericType(String strtype) {

    parseSignature(strtype);
  }

  private void parseSignature(String sig) {

    int index = 0;
    while (index < sig.length()) {

      switch (sig.charAt(index)) {
        case '[':
          arraydim++;
          break;
        case 'T':
          type = CodeConstants.TYPE_GENVAR;
          value = sig.substring(index + 1, sig.length() - 1);
          return;
        case 'L':
          type = CodeConstants.TYPE_OBJECT;
          sig = sig.substring(index + 1, sig.length() - 1);

          while (true) {
            String cl = getNextClassSignature(sig);

            String name = cl;
            String args = null;

            int argfrom = cl.indexOf("<");
            if (argfrom >= 0) {
              name = cl.substring(0, argfrom);
              args = cl.substring(argfrom + 1, cl.length() - 1);
            }

            if (cl.length() < sig.length()) {
              sig = sig.substring(cl.length() + 1); // skip '.'
              GenericType type = new GenericType(CodeConstants.TYPE_OBJECT, 0, name);
              parseArgumentsList(args, type);
              enclosingClasses.add(type);
            }
            else {
              value = name;
              parseArgumentsList(args, this);
              break;
            }
          }

          return;
        default:
          value = sig.substring(index, index + 1);
          type = getType(value.charAt(0));
      }

      index++;
    }
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

      String tstr = getNextType(value);
      int len = tstr.length();
      int wildcard = WILDCARD_NO;

      switch (tstr.charAt(0)) {
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
        tstr = tstr.substring(1);
      }

      type.getArguments().add(tstr.length() == 0 ? null : new GenericType(tstr));

      value = value.substring(len);
    }
  }

  public static String getNextType(String value) {

    int counter = 0;
    int index = 0;

    boolean contmode = false;

    loop:
    while (index < value.length()) {
      switch (value.charAt(index)) {
        case '*':
          if (!contmode) {
            break loop;
          }
          break;
        case 'L':
        case 'T':
          if (!contmode) {
            contmode = true;
          }
        case '[':
        case '+':
        case '-':
          break;
        default:
          if (!contmode) {
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

  private static int getType(char c) {
    switch (c) {
      case 'B':
        return CodeConstants.TYPE_BYTE;
      case 'C':
        return CodeConstants.TYPE_CHAR;
      case 'D':
        return CodeConstants.TYPE_DOUBLE;
      case 'F':
        return CodeConstants.TYPE_FLOAT;
      case 'I':
        return CodeConstants.TYPE_INT;
      case 'J':
        return CodeConstants.TYPE_LONG;
      case 'S':
        return CodeConstants.TYPE_SHORT;
      case 'Z':
        return CodeConstants.TYPE_BOOLEAN;
      case 'V':
        return CodeConstants.TYPE_VOID;
      case 'G':
        return CodeConstants.TYPE_GROUP2EMPTY;
      case 'N':
        return CodeConstants.TYPE_NOTINITIALIZED;
      case 'A':
        return CodeConstants.TYPE_ADDRESS;
      case 'X':
        return CodeConstants.TYPE_BYTECHAR;
      case 'Y':
        return CodeConstants.TYPE_SHORTCHAR;
      case 'U':
        return CodeConstants.TYPE_UNKNOWN;
      default:
        throw new RuntimeException("Invalid type");
    }
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
