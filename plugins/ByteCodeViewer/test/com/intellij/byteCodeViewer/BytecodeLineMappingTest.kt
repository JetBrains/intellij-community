// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BytecodeLineMappingTest : BasePlatformTestCase() {
  private fun bootstrapAndGetSelection(text: String): Pair<Int, Int> {
    myFixture.configureByText(getTestName(false) + ".java", text)
    val sourceDocument = myFixture.editor.document

    val sourceStartOffset = myFixture.editor.selectionModel.selectionStart
    val sourceEndOffset = myFixture.editor.selectionModel.selectionEnd

    val startLine = sourceDocument.getLineNumber(sourceStartOffset)
    val endLine = sourceDocument.getLineNumber(sourceEndOffset)
    return startLine to endLine
  }

  private fun doTest(
    source: String,
    bytecodeWithDebugInfo: String,
    bytecodeWithoutDebugInfo: String,
    expectedBytecodeSelection: String,
    stripDebugInfo: Boolean = false,
  ) {
    val (startLine, endLine) = bootstrapAndGetSelection(source)

    val linesRange = mapLines(
      bytecodeWithDebugInfo = bytecodeWithDebugInfo,
      sourceStartLine = startLine,
      sourceEndLine = endLine,
      stripDebugInfo = stripDebugInfo,
    )
    val bytecodeSelectionStart = linesRange.first
    var bytecodeSelectionEnd = linesRange.last
    bytecodeSelectionEnd++ // because most substring() functions use exclusive indexing for "end"
    bytecodeSelectionEnd++ // because string operations are 0-indexed but the editor is 1-indexed

    assertEquals(expectedBytecodeSelection, (if (stripDebugInfo) bytecodeWithoutDebugInfo else bytecodeWithDebugInfo).lines().subList(bytecodeSelectionStart, bytecodeSelectionEnd).joinToString("\n"))
  }

  fun `test removeDebugInfo`() {
    val actualStrippedBytecode = removeDebugInfo(sampleBytecode_1)
    val expectedStrippedBytecode = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple1/Main {
      |
      |  // compiled from: Main.java
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x9
      |  public static main([Ljava/lang/String;)V
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    RETURN
      |   L2
      |    MAXSTACK = 2
      |    MAXLOCALS = 1
      |}
    """.trimMargin("|")

    assertEquals(expectedStrippedBytecode, actualStrippedBytecode)
  }

  fun `test simple 1 method body start`() {

    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<caret>
          System.out.println("hello world");I
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |    LINENUMBER 5 L0
    """.trimMargin("|"))
  }

  fun `test simple 1 method body - line selected`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          <selection>System.out.println("hello world");</selection>
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |    LINENUMBER 5 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 6 L1
    """.trimMargin("|"))
  }

  fun `test simple 1 method body - from brace to brace`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<selection>
          System.out.println("hello world");
        </selection>}
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |    LINENUMBER 5 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 6 L1
      |    RETURN
      |   L2
      |    LOCALVARIABLE args [Ljava/lang/String; L0 L2 0
    """.trimMargin("|"))
  }

  fun `test simple 1 method body end`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          System.out.println("hello world");
        }<caret>
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |    LINENUMBER 6 L1
      |    RETURN
      |   L2
      |    LOCALVARIABLE args [Ljava/lang/String; L0 L2 0
    """.trimMargin("|"))
  }

  fun `test simple 1 class`() {
    val source = """
      package simple1;
      
      public class Main {<caret>
        public static void main(String[] args) {
          System.out.println("hello world");
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |    LINENUMBER 3 L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    LOCALVARIABLE this Lsimple1/Main; L0 L1 0
    """.trimMargin("|"))
  }

  fun `test simple 2 - single field selected`() {
    val source = """
      package simple2;
      
      public class Main {
        <selection>public static String name = "Charlie";</selection>
        public static Object obj = new Object();
        
        public static void main(String[] args) {
          System.out.println("hello world");
          int argCount = args.length;
          System.out.println("there are " + argCount + " args");
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_2, sampleBytecode_2_noDebugInfo, """
      |    LINENUMBER 4 L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
      |    LINENUMBER 5 L1
    """.trimMargin("|"))
  }

  fun `test simple 2 - two fields selected`() {
    val source = """
      package simple2;
      
      public class Main {
        <selection>public static String name = "Charlie";
        public static Object obj = new Object();</selection>
        
        public static void main(String[] args) {
          System.out.println("hello world");
          int argCount = args.length;
          System.out.println("there are " + argCount + " args");
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_2, sampleBytecode_2_noDebugInfo, """
      |    LINENUMBER 4 L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
      |    LINENUMBER 5 L1
      |    NEW java/lang/Object
      |    DUP
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    PUTSTATIC simple2/Main.obj : Ljava/lang/Object;
      |    RETURN
      |    MAXSTACK = 2
    """.trimMargin("|"))
  }

  fun `test simple 2 method body - two lines selected`() {
    val source = """
      package simple2;
      
      public class Main {
        public static String name = "Charlie";
        public static Object obj = new Object();
        
        public static void main(String[] args) {
          <selection>System.out.println("hello world");
          int argCount = args.length;</selection>
          System.out.println("there are " + argCount + " args");
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_2, sampleBytecode_2_noDebugInfo, """
      |    LINENUMBER 8 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 9 L1
      |    ALOAD 0
      |    ARRAYLENGTH
      |    ISTORE 1
      |   L2
      |    LINENUMBER 10 L2
    """.trimMargin("|"))
  }

  fun `test (strip debug info) simple 1 method body - single line selected`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          <selection>System.out.println("hello world");</selection>
        }
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
    """.trimMargin("|"), stripDebugInfo = true)
  }

  fun `test (strip debug info) simple 1 method body - from brace to brace`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<selection>
          System.out.println("hello world");
        </selection>}
      }
    """.trimIndent()

    doTest(source, sampleBytecode_1, sampleBytecode_1_noDebugInfo, """
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    RETURN
      |   L2
    """.trimMargin("|"), stripDebugInfo = true)
  }

  fun `test (strip debug info) simple 4 - works fine in presence of jumps (1)`() {
    val source = """
      package simple4;
      
      public class Main {
        String method1(boolean value) {
          if (value == true) {
            return "baz";
          }
          return "baz";
        }
      
        String method2(boolean value) {
          <selection>if (value == Boolean.TRUE) {
            return "bar";
          }</selection>
          return "baz";
        }
      
        String method3(boolean value) {
          if (value == Boolean.FALSE) {
            return "bar";
          }
          return "baz";
        }
      
        String method(boolean value) {
          if (Boolean.TRUE.equals(returnsBool(value))) {
            return "foo";
          }
          return "baz";
        }
        
        public Boolean returnsBool(boolean value) {
          return Math.random() > 0.5;
        }
      }
      """.trimIndent()

    doTest(source, sampleBytecode_3, sampleBytecode_3_noDebugInfo, """
      |   L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LDC "bar"
      |    ARETURN
      |   L1
    """.trimMargin("|"), stripDebugInfo = true)
  }

  fun `test (strip debug info) simple 4 - works fine in presence of jumps (2)`() {
    val source = """
      package simple4;
      
      public class Main {
        String method1(boolean value) {
          if (value == true) {
            return "baz";
          }
          return "baz";
        }
      
        String method2(boolean value) {
          if (value == Boolean.TRUE) {
            return "bar";
          }
          return "baz";
        }
      
        String method3(boolean value) {
          if (value == Boolean.FALSE) {
            return "bar";
          }
          return "baz";
        }
      
        String method(boolean value) {
          <selection>if (Boolean.TRUE.equals(returnsBool(value))) {
            return "foo";
          }</selection>
          return "baz";
        }
        
        public Boolean returnsBool(boolean value) {
          return Math.random() > 0.5;
        }
      }
      """.trimIndent()

    doTest(source, sampleBytecode_3, sampleBytecode_3_noDebugInfo, """
      |   L0
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    ALOAD 0
      |    ILOAD 1
      |    INVOKEVIRTUAL simple4/Main.returnsBool (Z)Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.equals (Ljava/lang/Object;)Z
      |    IFEQ L1
      |   L2
      |    LDC "foo"
      |    ARETURN
      |   L1
    """.trimMargin("|"), stripDebugInfo = true)
  }

  /**
   * Bytecode was compiled using javac 23.
   *
   * Human-readable sample bytecode was read with ASM ClassReader, with flags applied: SKIP_FRAMES
   *
   * NOT-TRUE: Human-readable sample bytecode without debug info was read with ASM ClassReader, with flags applied: SKIP_FRAMES, SKIP_DEBUG
   * Actually, it was created with [BytecodeLineMapping.removeDebugInfo].
   */
  companion object Fixtures {

    private val sampleBytecode_1 = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple1/Main {
      |
      |  // compiled from: Main.java
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    LINENUMBER 3 L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    LOCALVARIABLE this Lsimple1/Main; L0 L1 0
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x9
      |  public static main([Ljava/lang/String;)V
      |   L0
      |    LINENUMBER 5 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 6 L1
      |    RETURN
      |   L2
      |    LOCALVARIABLE args [Ljava/lang/String; L0 L2 0
      |    MAXSTACK = 2
      |    MAXLOCALS = 1
      |}
    """.trimMargin("|")

    private val sampleBytecode_1_noDebugInfo = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple1/Main {
      |
      |  // compiled from: Main.java
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x9
      |  public static main([Ljava/lang/String;)V
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    RETURN
      |   L2
      |    MAXSTACK = 2
      |    MAXLOCALS = 1
      |}
    """.trimMargin("|")

    private val sampleBytecode_2 = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple2/Main {
      |
      |  // compiled from: Main.java
      |  // access flags 0x19
      |  public final static INNERCLASS java/lang/invoke/MethodHandles${'$'}Lookup java/lang/invoke/MethodHandles Lookup
      |
      |  // access flags 0x9
      |  public static Ljava/lang/String; name
      |
      |  // access flags 0x9
      |  public static Ljava/lang/Object; obj
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    LINENUMBER 3 L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    LOCALVARIABLE this Lsimple2/Main; L0 L1 0
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x9
      |  public static main([Ljava/lang/String;)V
      |   L0
      |    LINENUMBER 8 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 9 L1
      |    ALOAD 0
      |    ARRAYLENGTH
      |    ISTORE 1
      |   L2
      |    LINENUMBER 10 L2
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    ILOAD 1
      |    INVOKEDYNAMIC makeConcatWithConstants(I)Ljava/lang/String; [
      |      // handle kind 0x6 : INVOKESTATIC
      |      java/lang/invoke/StringConcatFactory.makeConcatWithConstants(Ljava/lang/invoke/MethodHandles${'$'}Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
      |      // arguments:
      |      "there are \u0001 args"
      |    ]
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L3
      |    LINENUMBER 11 L3
      |    RETURN
      |   L4
      |    LOCALVARIABLE args [Ljava/lang/String; L0 L4 0
      |    LOCALVARIABLE argCount I L2 L4 1
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x8
      |  static <clinit>()V
      |   L0
      |    LINENUMBER 4 L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
      |    LINENUMBER 5 L1
      |    NEW java/lang/Object
      |    DUP
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    PUTSTATIC simple2/Main.obj : Ljava/lang/Object;
      |    RETURN
      |    MAXSTACK = 2
      |    MAXLOCALS = 0
      |}
    """.trimMargin("|")

    private val sampleBytecode_2_noDebugInfo = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple2/Main {
      |
      |  // access flags 0x19
      |  public final static INNERCLASS java/lang/invoke/MethodHandles${'$'}Lookup java/lang/invoke/MethodHandles Lookup
      |
      |  // access flags 0x9
      |  public static Ljava/lang/String; name
      |
      |  // access flags 0x9
      |  public static Ljava/lang/Object; obj
      |
      |  // access flags 0x1
      |  public <init>()V
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x9
      |  public static main([Ljava/lang/String;)V
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |    ALOAD 0
      |    ARRAYLENGTH
      |    ISTORE 1
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    ILOAD 1
      |    INVOKEDYNAMIC makeConcatWithConstants(I)Ljava/lang/String; [
      |      // handle kind 0x6 : INVOKESTATIC
      |      java/lang/invoke/StringConcatFactory.makeConcatWithConstants(Ljava/lang/invoke/MethodHandles${'$'}Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
      |      // arguments:
      |      "there are \u0001 args"
      |    ]
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |    RETURN
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x8
      |  static <clinit>()V
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |    NEW java/lang/Object
      |    DUP
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    PUTSTATIC simple2/Main.obj : Ljava/lang/Object;
      |    RETURN
      |    MAXSTACK = 2
      |    MAXLOCALS = 0
      |}
    """.trimMargin("|")

    private val sampleBytecode_3 = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple4/Main {
      |
      |  // compiled from: Main.java
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    LINENUMBER 3 L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    LOCALVARIABLE this Lsimple4/Main; L0 L1 0
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x0
      |  method1(Z)Ljava/lang/String;
      |   L0
      |    LINENUMBER 5 L0
      |    ILOAD 1
      |    ICONST_1
      |    IF_ICMPNE L1
      |   L2
      |    LINENUMBER 6 L2
      |    LDC "baz"
      |    ARETURN
      |   L1
      |    LINENUMBER 8 L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    LOCALVARIABLE this Lsimple4/Main; L0 L3 0
      |    LOCALVARIABLE value Z L0 L3 1
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method2(Z)Ljava/lang/String;
      |   L0
      |    LINENUMBER 12 L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LINENUMBER 13 L2
      |    LDC "bar"
      |    ARETURN
      |   L1
      |    LINENUMBER 15 L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    LOCALVARIABLE this Lsimple4/Main; L0 L3 0
      |    LOCALVARIABLE value Z L0 L3 1
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method3(Z)Ljava/lang/String;
      |   L0
      |    LINENUMBER 19 L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.FALSE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LINENUMBER 20 L2
      |    LDC "bar"
      |    ARETURN
      |   L1
      |    LINENUMBER 22 L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    LOCALVARIABLE this Lsimple4/Main; L0 L3 0
      |    LOCALVARIABLE value Z L0 L3 1
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method(Z)Ljava/lang/String;
      |   L0
      |    LINENUMBER 26 L0
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    ALOAD 0
      |    ILOAD 1
      |    INVOKEVIRTUAL simple4/Main.returnsBool (Z)Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.equals (Ljava/lang/Object;)Z
      |    IFEQ L1
      |   L2
      |    LINENUMBER 27 L2
      |    LDC "foo"
      |    ARETURN
      |   L1
      |    LINENUMBER 29 L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    LOCALVARIABLE this Lsimple4/Main; L0 L3 0
      |    LOCALVARIABLE value Z L0 L3 1
      |    MAXSTACK = 3
      |    MAXLOCALS = 2
      |
      |  // access flags 0x1
      |  public returnsBool(Z)Ljava/lang/Boolean;
      |   L0
      |    LINENUMBER 33 L0
      |    INVOKESTATIC java/lang/Math.random ()D
      |    LDC 0.5
      |    DCMPL
      |    IFLE L1
      |    ICONST_1
      |    GOTO L2
      |   L1
      |    ICONST_0
      |   L2
      |    INVOKESTATIC java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
      |    ARETURN
      |   L3
      |    LOCALVARIABLE this Lsimple4/Main; L0 L3 0
      |    LOCALVARIABLE value Z L0 L3 1
      |    MAXSTACK = 4
      |    MAXLOCALS = 2
      |}
    """.trimMargin("|")

    private val sampleBytecode_3_noDebugInfo = """
      |// class version 67.0 (67)
      |// access flags 0x21
      |public class simple4/Main {
      |
      |  // compiled from: Main.java
      |
      |  // access flags 0x1
      |  public <init>()V
      |   L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
      |    MAXSTACK = 1
      |    MAXLOCALS = 1
      |
      |  // access flags 0x0
      |  method1(Z)Ljava/lang/String;
      |   L0
      |    ILOAD 1
      |    ICONST_1
      |    IF_ICMPNE L1
      |   L2
      |    LDC "baz"
      |    ARETURN
      |   L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method2(Z)Ljava/lang/String;
      |   L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LDC "bar"
      |    ARETURN
      |   L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method3(Z)Ljava/lang/String;
      |   L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.FALSE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LDC "bar"
      |    ARETURN
      |   L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    MAXSTACK = 2
      |    MAXLOCALS = 2
      |
      |  // access flags 0x0
      |  method(Z)Ljava/lang/String;
      |   L0
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    ALOAD 0
      |    ILOAD 1
      |    INVOKEVIRTUAL simple4/Main.returnsBool (Z)Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.equals (Ljava/lang/Object;)Z
      |    IFEQ L1
      |   L2
      |    LDC "foo"
      |    ARETURN
      |   L1
      |    LDC "baz"
      |    ARETURN
      |   L3
      |    MAXSTACK = 3
      |    MAXLOCALS = 2
      |
      |  // access flags 0x1
      |  public returnsBool(Z)Ljava/lang/Boolean;
      |   L0
      |    INVOKESTATIC java/lang/Math.random ()D
      |    LDC 0.5
      |    DCMPL
      |    IFLE L1
      |    ICONST_1
      |    GOTO L2
      |   L1
      |    ICONST_0
      |   L2
      |    INVOKESTATIC java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
      |    ARETURN
      |   L3
      |    MAXSTACK = 4
      |    MAXLOCALS = 2
      |}
     """.trimMargin("|")
  }
}
