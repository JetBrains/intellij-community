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
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;

import java.util.Arrays;
import java.util.HashSet;

public class TextUtil {
  private static final HashSet<String> KEYWORDS = new HashSet<>(Arrays.asList(
    "abstract", "default", "if", "private", "this", "boolean", "do", "implements", "protected", "throw", "break", "double", "import",
    "public", "throws", "byte", "else", "instanceof", "return", "transient", "case", "extends", "int", "short", "try", "catch", "final",
    "interface", "static", "void", "char", "finally", "long", "strictfp", "volatile", "class", "float", "native", "super", "while",
    "const", "for", "new", "switch", "continue", "goto", "package", "synchronized", "true", "false", "null", "assert"));

  public static void writeQualifiedSuper(TextBuffer buf, String qualifier) {
    ClassesProcessor.ClassNode classNode = (ClassesProcessor.ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS_NODE);
    if (!qualifier.equals(classNode.classStruct.qualifiedName)) {
      buf.append(DecompilerContext.getImportCollector().getShortName(ExprProcessor.buildJavaClassName(qualifier))).append('.');
    }
    buf.append("super");
  }

  public static String getIndentString(int length) {
    if (length == 0) return "";
    StringBuilder buf = new StringBuilder();
    String indent = (String)DecompilerContext.getProperty(IFernflowerPreferences.INDENT_STRING);
    while (length-- > 0) {
      buf.append(indent);
    }
    return buf.toString();
  }

  public static boolean isPrintableUnicode(char c) {
    int t = Character.getType(c);
    return t != Character.UNASSIGNED && t != Character.LINE_SEPARATOR && t != Character.PARAGRAPH_SEPARATOR &&
           t != Character.CONTROL && t != Character.FORMAT && t != Character.PRIVATE_USE && t != Character.SURROGATE;
  }

  public static String charToUnicodeLiteral(int value) {
    String sTemp = Integer.toHexString(value);
    sTemp = ("0000" + sTemp).substring(sTemp.length());
    return "\\u" + sTemp;
  }

  public static boolean isValidIdentifier(String id, int version) {
    return isJavaIdentifier(id) && !isKeyword(id, version);
  }

  private static boolean isJavaIdentifier(String id) {
    if (id.isEmpty() || !Character.isJavaIdentifierStart(id.charAt(0))) {
      return false;
    }

    for (int i = 1; i < id.length(); i++) {
      if (!Character.isJavaIdentifierPart(id.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  private static boolean isKeyword(String id, int version) {
    return KEYWORDS.contains(id) || version > CodeConstants.BYTECODE_JAVA_5 && "enum".equals(id);
  }
}