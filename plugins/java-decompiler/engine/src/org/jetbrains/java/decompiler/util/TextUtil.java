/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
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
    append(buf, indent, length);
    return buf.toString();
  }

  public static void append(StringBuilder buf, String string, int times) {
    while (times-- > 0) buf.append(string);
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
  
  public static String getInstructionName(int opcode) {
    return opcodeNames[opcode];
  }
  
  private static final String[] opcodeNames = {
      "nop",                                //    "nop",
      "aconst_null",                //    "aconst_null",
      "iconst_m1",                        //    "iconst_m1",
      "iconst_0",                        //    "iconst_0",
      "iconst_1",                        //    "iconst_1",
      "iconst_2",                        //    "iconst_2",
      "iconst_3",                        //    "iconst_3",
      "iconst_4",                        //    "iconst_4",
      "iconst_5",                        //    "iconst_5",
      "lconst_0",                        //    "lconst_0",
      "lconst_1",                        //    "lconst_1",
      "fconst_0",                        //    "fconst_0",
      "fconst_1",                        //    "fconst_1",
      "fconst_2",                        //    "fconst_2",
      "dconst_0",                        //    "dconst_0",
      "dconst_1",                        //    "dconst_1",
      "bipush",                        //    "bipush",
      "sipush",                        //    "sipush",
      "ldc",                        //    "ldc",
      "ldc_w",                        //    "ldc_w",
      "ldc2_w",                        //    "ldc2_w",
      "iload",                        //    "iload",
      "lload",                        //    "lload",
      "fload",                        //    "fload",
      "dload",                        //    "dload",
      "aload",                        //    "aload",
      "iload_0",                        //    "iload_0",
      "iload_1",                        //    "iload_1",
      "iload_2",                        //    "iload_2",
      "iload_3",                        //    "iload_3",
      "lload_0",                        //    "lload_0",
      "lload_1",                        //    "lload_1",
      "lload_2",                        //    "lload_2",
      "lload_3",                        //    "lload_3",
      "fload_0",                        //    "fload_0",
      "fload_1",                        //    "fload_1",
      "fload_2",                        //    "fload_2",
      "fload_3",                        //    "fload_3",
      "dload_0",                        //    "dload_0",
      "dload_1",                        //    "dload_1",
      "dload_2",                        //    "dload_2",
      "dload_3",                        //    "dload_3",
      "aload_0",                        //    "aload_0",
      "aload_1",                        //    "aload_1",
      "aload_2",                        //    "aload_2",
      "aload_3",                        //    "aload_3",
      "iaload",                        //    "iaload",
      "laload",                        //    "laload",
      "faload",                        //    "faload",
      "daload",                        //    "daload",
      "aaload",                        //    "aaload",
      "baload",                        //    "baload",
      "caload",                        //    "caload",
      "saload",                        //    "saload",
      "istore",                        //    "istore",
      "lstore",                        //    "lstore",
      "fstore",                        //    "fstore",
      "dstore",                        //    "dstore",
      "astore",                        //    "astore",
      "istore_0",                        //    "istore_0",
      "istore_1",                        //    "istore_1",
      "istore_2",                        //    "istore_2",
      "istore_3",                        //    "istore_3",
      "lstore_0",                        //    "lstore_0",
      "lstore_1",                        //    "lstore_1",
      "lstore_2",                        //    "lstore_2",
      "lstore_3",                        //    "lstore_3",
      "fstore_0",                        //    "fstore_0",
      "fstore_1",                        //    "fstore_1",
      "fstore_2",                        //    "fstore_2",
      "fstore_3",                        //    "fstore_3",
      "dstore_0",                        //    "dstore_0",
      "dstore_1",                        //    "dstore_1",
      "dstore_2",                        //    "dstore_2",
      "dstore_3",                        //    "dstore_3",
      "astore_0",                        //    "astore_0",
      "astore_1",                        //    "astore_1",
      "astore_2",                        //    "astore_2",
      "astore_3",                        //    "astore_3",
      "iastore",                        //    "iastore",
      "lastore",                        //    "lastore",
      "fastore",                        //    "fastore",
      "dastore",                        //    "dastore",
      "aastore",                        //    "aastore",
      "bastore",                        //    "bastore",
      "castore",                        //    "castore",
      "sastore",                        //    "sastore",
      "pop",                        //    "pop",
      "pop2",                        //    "pop2",
      "dup",                        //    "dup",
      "dup_x1",                        //    "dup_x1",
      "dup_x2",                        //    "dup_x2",
      "dup2",                        //    "dup2",
      "dup2_x1",                        //    "dup2_x1",
      "dup2_x2",                        //    "dup2_x2",
      "swap",                        //    "swap",
      "iadd",                        //    "iadd",
      "ladd",                        //    "ladd",
      "fadd",                        //    "fadd",
      "dadd",                        //    "dadd",
      "isub",                        //    "isub",
      "lsub",                        //    "lsub",
      "fsub",                        //    "fsub",
      "dsub",                        //    "dsub",
      "imul",                        //    "imul",
      "lmul",                        //    "lmul",
      "fmul",                        //    "fmul",
      "dmul",                        //    "dmul",
      "idiv",                        //    "idiv",
      "ldiv",                        //    "ldiv",
      "fdiv",                        //    "fdiv",
      "ddiv",                        //    "ddiv",
      "irem",                        //    "irem",
      "lrem",                        //    "lrem",
      "frem",                        //    "frem",
      "drem",                        //    "drem",
      "ineg",                        //    "ineg",
      "lneg",                        //    "lneg",
      "fneg",                        //    "fneg",
      "dneg",                        //    "dneg",
      "ishl",                        //    "ishl",
      "lshl",                        //    "lshl",
      "ishr",                        //    "ishr",
      "lshr",                        //    "lshr",
      "iushr",                        //    "iushr",
      "lushr",                        //    "lushr",
      "iand",                        //    "iand",
      "land",                        //    "land",
      "ior",                        //    "ior",
      "lor",                        //    "lor",
      "ixor",                        //    "ixor",
      "lxor",                        //    "lxor",
      "iinc",                        //    "iinc",
      "i2l",                        //    "i2l",
      "i2f",                        //    "i2f",
      "i2d",                        //    "i2d",
      "l2i",                        //    "l2i",
      "l2f",                        //    "l2f",
      "l2d",                        //    "l2d",
      "f2i",                        //    "f2i",
      "f2l",                        //    "f2l",
      "f2d",                        //    "f2d",
      "d2i",                        //    "d2i",
      "d2l",                        //    "d2l",
      "d2f",                        //    "d2f",
      "i2b",                        //    "i2b",
      "i2c",                        //    "i2c",
      "i2s",                        //    "i2s",
      "lcmp",                        //    "lcmp",
      "fcmpl",                        //    "fcmpl",
      "fcmpg",                        //    "fcmpg",
      "dcmpl",                        //    "dcmpl",
      "dcmpg",                        //    "dcmpg",
      "ifeq",                        //    "ifeq",
      "ifne",                        //    "ifne",
      "iflt",                        //    "iflt",
      "ifge",                        //    "ifge",
      "ifgt",                        //    "ifgt",
      "ifle",                        //    "ifle",
      "if_icmpeq",                //    "if_icmpeq",
      "if_icmpne",                //    "if_icmpne",
      "if_icmplt",                //    "if_icmplt",
      "if_icmpge",                //    "if_icmpge",
      "if_icmpgt",                //    "if_icmpgt",
      "if_icmple",                //    "if_icmple",
      "if_acmpeq",                //    "if_acmpeq",
      "if_acmpne",                //    "if_acmpne",
      "goto",                        //    "goto",
      "jsr",                        //    "jsr",
      "ret",                        //    "ret",
      "tableswitch",                        //    "tableswitch",
      "lookupswitch",                        //    "lookupswitch",
      "ireturn",                        //    "ireturn",
      "lreturn",                        //    "lreturn",
      "freturn",                        //    "freturn",
      "dreturn",                        //    "dreturn",
      "areturn",                        //    "areturn",
      "return",                        //    "return",
      "getstatic",                //    "getstatic",
      "putstatic",                //    "putstatic",
      "getfield",                //    "getfield",
      "putfield",                //    "putfield",
      "invokevirtual",                //    "invokevirtual",
      "invokespecial",                //    "invokespecial",
      "invokestatic",                //    "invokestatic",
      "invokeinterface",                //    "invokeinterface",
      //"xxxunusedxxx",   //    "xxxunusedxxx", Java 6 and before
      "invokedynamic",                //    "invokedynamic", Java 7 and later
      "new",                                //    "new",
      "newarray",                //    "newarray",
      "anewarray",                //    "anewarray",
      "arraylength",                //    "arraylength",
      "athrow",                        //    "athrow",
      "checkcast",                //    "checkcast",
      "instanceof",                //    "instanceof",
      "monitorenter",                //    "monitorenter",
      "monitorexit",                //    "monitorexit",
      "wide",                        //    "wide",
      "multianewarray",                //    "multianewarray",
      "ifnull",                        //    "ifnull",
      "ifnonnull",                //    "ifnonnull",
      "goto_w",                        //    "goto_w",
      "jsr_w"                        //    "jsr_w"
    };  
}