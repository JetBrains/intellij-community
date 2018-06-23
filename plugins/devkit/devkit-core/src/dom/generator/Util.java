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

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package org.jetbrains.idea.devkit.dom.generator;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.xerces.xs.XSObject;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * @author Konstantin Bulenkov
 */
public class Util {
  public static final String ANONYMOUS_ELEM_TYPE_SUFFIX = "ElemType";
  public static final String ANONYMOUS_ATTR_TYPE_SUFFIX = "AttrType";
  public static final String TYPE_SUFFIX = "Type";
  
  //
  // Constants
  //
  public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

  // reserved names map
  public static final String[] RESERVED_NAMES_TABLE = {
          "abstract", "default", "if", "private", "this",
          "boolean", "do", "implements", "protected", "throw",
          "break", "double", "import", "public", "throws",
          "byte", "else", "instanceof", "return", "transient",
          "case", "extends", "int", "short", "try",
          "catch", "final", "interface", "static", "void",
          "char", "finally", "long", "strictfp", "volatile",
          "class", "float", "native", "super", "while",
          "const", "for", "new", "switch",
          "continue", "goto", "package", "synchronized"
  };
  public static final HashMap<String, String> RESERVED_NAMES_MAP;

  static {
    RESERVED_NAMES_MAP = new HashMap<>();
    for (String aRESERVED_NAMES_TABLE : RESERVED_NAMES_TABLE) {
      // RESERVED_NAMES_MAP.put(RESERVED_NAMES_TABLE[i], RESERVED_NAMES_TABLE[i]+"_");
      // as far as there is no actual field but setters/getters:
      RESERVED_NAMES_MAP.put(aRESERVED_NAMES_TABLE, aRESERVED_NAMES_TABLE);
    }
    //RESERVED_NAMES_MAP.put("class", "clazz");
  }


  static void log(String str) {
    System.out.println(str);
  }

  static void logwarn(String str) {
    System.out.println("[warn] " + str);
  }

  static void logerr(String str) {
    System.out.println("[error] " + str);
  }

  public static String pluralize(String suggestion) {
    // return suggestion+"List";
    final String VOWELS = "aeiouy";
    if (suggestion.endsWith("s") || suggestion.endsWith("x") ||
            suggestion.endsWith("ch")) {
      suggestion += "es";
    } else {
      int len = suggestion.length();
      if (suggestion.endsWith("y") && len > 1 && VOWELS.indexOf(suggestion.charAt(len - 2)) < 0) {
        suggestion = suggestion.substring(0, len - 1) + "ies";
      } else {
        suggestion += "s";
      }
    }
    return suggestion;
  }

  public static String toJavaFieldName(String xmlName) {
    String rc = toJavaName(xmlName);
    if (RESERVED_NAMES_MAP.containsKey(rc)) {
      rc = RESERVED_NAMES_MAP.get(rc);
    }
    return rc;
  }


  public static String computeEnumConstantName(String val, String typeName) {
    String id = val;
    for (int i = 1; i < id.length(); i++) {
      if (Character.isUpperCase(id.charAt(i))
              && Character.isLowerCase(id.charAt(i - 1))) {
        id = id.substring(0, i) + "_" + id.substring(i);
        i++;
      }
    }
    id = id.toUpperCase();
    id = id.replace('.', '_').replace('-', '_');
    if (id.length() < 2 || !Character.isJavaIdentifierStart(id.charAt(0))) {
      id = typeName + "_" + id;
    }
    return id;
  }


  public static String capitalize(String str) {
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  public static String decapitalize(String str) {
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  public static String toJavaName(String xmlName) {
    xmlName = xmlName.substring(xmlName.lastIndexOf(':') + 1);
    StringTokenizer st = new StringTokenizer(xmlName, "-");
    StringBuffer sb = new StringBuffer(st.nextToken());
    while (st.hasMoreTokens()) {
      sb.append(capitalize(st.nextToken()));
    }
    return sb.toString();
  }

  public static String toDefXmlTagName(XSObject xs) {
    String xmlName = xs.getName();
    if (xmlName.endsWith(TYPE_SUFFIX)) xmlName = xmlName.substring(0, xmlName.length() - 4);
    return xmlName;
  }

  public static String toDefXmlTagName(String tname) {
    String xmlName = tname;
    xmlName = StringUtil.trimEnd(xmlName, TYPE_SUFFIX);
    return xmlName;
  }


  public static boolean addToNameMap(Map<String, FieldDesc> fdMap, FieldDesc fd1, boolean merge) {
    boolean duplicates = false;
    FieldDesc fd2;
    if ((fd2 = fdMap.remove(fd1.name)) != null) {
      if (fd2.clType == FieldDesc.ATTR) {
        // attr <-> field
        fd2.name = fd1.name + "Attr";
        fdMap.put(fd2.name, fd2);
      } else if (merge) {
        fdMap.put(fd2.name, fd2);
        return false;
      } else {
        duplicates = true;
        fd2.name = fd1.name + "1";
        fd2.duplicateIndex = 1;
        fdMap.put(fd2.name, fd2);
        fd1.name = fd1.name + "2";
        fd1.duplicateIndex = 2;
      }
    } else if ((fd2 = fdMap.get(fd1.name + "1")) != null) {
      int id = 2;
      while (fdMap.containsKey(fd1.name + id)) id++;
      fd1.name = fd1.name + id;
      fd1.duplicateIndex = id;
    }
    fdMap.put(fd1.name, fd1);
    return duplicates;
  }

  public static String expandProperties(final String str, final Map<String, String> map) {
    if (str.indexOf("${") == -1) return str;
    int state = 0;
    final StringBuilder result = new StringBuilder();
    final StringBuilder variable = new StringBuilder();
    for (int i=0; i<str.length(); i++) {
      final char ch = str.charAt(i);
      switch (state) {
        case 0:
          if (ch == '$') state = 1;
          else result.append(ch);
          break;
        case 1:
          if (ch == '{') {
            state = 2;
            variable.setLength(0);
          }
          else {
            state = 0;
            result.append('$').append(ch);
          }
          break;
        case 2:
          if (ch == '}') {
            final String value = map.get(variable.toString());
            result.append(value == null? variable : value);
            state = 0;
          }
          else {
            variable.append(ch);
          }
          break;
      }
    }
    return result.toString();
  }
}

