// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class BytecodeLineMappingTest : BasePlatformTestCase() {
  private fun getSelectionFromSource(text: String): Pair<Int, Int> {
    myFixture.configureByText(getTestName(false) + ".java", text)
    val sourceDocument = myFixture.editor.document

    val sourceStartOffset = myFixture.editor.selectionModel.selectionStart
    val sourceEndOffset = myFixture.editor.selectionModel.selectionEnd

    val startLine = sourceDocument.getLineNumber(sourceStartOffset)
    val endLine = sourceDocument.getLineNumber(sourceEndOffset)
    return startLine to endLine
  }

  private fun doTest(
    sourceWithSelection: String,
    fixture: Fixture,
    showDebugInfo: Boolean,
    expectedBytecodeSelection: String,
  ) {
    val (startLine, endLine) = getSelectionFromSource(sourceWithSelection)
    assertEquals(fixture.source, myFixture.editor.document.text) // ensure consistency
    assertEquals(fixture.bytecode, removeDebugInfo(fixture.bytecodeWithDebugInfo)) // ensure consistency

    val linesRange = mapLines(
      bytecodeWithDebugInfo = fixture.bytecodeWithDebugInfo,
      sourceStartLine = startLine,
      sourceEndLine = endLine,
      showDebugInfo = showDebugInfo,
    )
    val bytecodeSelectionStartLine = linesRange.first
    var bytecodeSelectionEndLine = linesRange.last
    bytecodeSelectionEndLine++ // because most substring() functions use exclusive indexing for "end"
    bytecodeSelectionEndLine++ // because string operations are 0-indexed but the editor is 1-indexed

    val bytecode = if (showDebugInfo) fixture.bytecodeWithDebugInfo else fixture.bytecode
    assertEquals(expectedBytecodeSelection, bytecode.lines().subList(fromIndex = bytecodeSelectionStartLine, toIndex = bytecodeSelectionEndLine).joinToString("\n"))
  }

  fun `test removeDebugInfo`() {
    val actualStrippedBytecode = removeDebugInfo(simple1.bytecodeWithDebugInfo)
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
      |
    """.trimMargin("|")

    assertEquals(expectedStrippedBytecode, actualStrippedBytecode)
    assertEquals(expectedStrippedBytecode, simple1.bytecode)
  }

  fun `test simple 1 method body start`() {

    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<caret>
          System.out.println("hello world");
        }
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source,
      fixture = simple1,
      showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
    """.trimMargin("|"),
    )
  }

  fun `test simple 1 method body start (show debug info)`() {

    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<caret>
          System.out.println("hello world");
        }
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source,
      fixture = simple1,
      showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L0
    """.trimMargin("|"),
    )
  }

  fun `test simple 1 method body - single line selected`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          <selection>System.out.println("hello world");</selection>
        }
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source,
      fixture = simple1,
      showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
    """.trimMargin("|"),
    )
  }

  fun `test simple 1 method body - single line selected (show debug info)`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          <selection>System.out.println("hello world");</selection>
        }
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L0
      |    LINENUMBER 5 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    RETURN
      |   L2
    """.trimMargin("|"),
    )
  }

  fun `test simple 1 method body - from brace to brace (show debug info)`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {<selection>
          System.out.println("hello world");
        </selection>}
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L0
      |    LINENUMBER 5 L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    LINENUMBER 6 L1
      |    RETURN
      |   L2
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L1
      |    RETURN
      |   L2
    """.trimMargin("|"),
    )
  }


  fun `test simple 1 method body end (show debug info)`() {
    val source = """
      package simple1;
      
      public class Main {
        public static void main(String[] args) {
          System.out.println("hello world");
        }<caret>
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L1
      |    LINENUMBER 6 L1
      |    RETURN
      |   L2
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
    """.trimMargin("|"),
    )
  }

  fun `test simple 1 class (show debug info)`() {
    val source = """
      package simple1;
      
      public class Main {<caret>
        public static void main(String[] args) {
          System.out.println("hello world");
        }
      }
      
    """.trimIndent()

    doTest(
      sourceWithSelection = source, fixture = simple1, showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L0
      |    LINENUMBER 3 L0
      |    ALOAD 0
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    RETURN
      |   L1
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
    """.trimMargin("|"),
    )
  }

  fun `test simple 2 - single field selected (show debug info)`() {
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = true,
      expectedBytecodeSelection = """
      |   L0
      |    LINENUMBER 4 L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    LDC "Charlie"
      |    PUTSTATIC simple2/Main.name : Ljava/lang/String;
      |   L1
      |    NEW java/lang/Object
      |    DUP
      |    INVOKESPECIAL java/lang/Object.<init> ()V
      |    PUTSTATIC simple2/Main.obj : Ljava/lang/Object;
      |    RETURN
      |    MAXSTACK = 2
    """.trimMargin("|"),
    )
  }

  fun `test simple 2 - two fields selected (show debug info)`() {
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = true,
      expectedBytecodeSelection = """
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
    """.trimMargin("|"),
    )
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
      |    LDC "hello world"
      |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
      |   L1
      |    ALOAD 0
      |    ARRAYLENGTH
      |    ISTORE 1
      |   L2
    """.trimMargin("|"),
    )
  }

  fun `test simple 2 method body - two lines selected (show debug info)`() {
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

    doTest(
      sourceWithSelection = source, fixture = simple2, showDebugInfo = true,
      expectedBytecodeSelection = """
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
    """.trimMargin("|"),
    )
  }

  fun `test simple 3 - conditional jumps (1)`() {
    val source = """
      package simple3;
      
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

    doTest(
      sourceWithSelection = source, fixture = simple3, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    ILOAD 1
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.booleanValue ()Z
      |    IF_ICMPNE L1
      |   L2
      |    LDC "bar"
      |    ARETURN
      |   L1
    """.trimMargin("|"),
    )
  }

  fun `test simple 3 - conditional jumps (1) (show debug info)`() {
    val source = """
      package simple3;
      
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

    doTest(
      sourceWithSelection = source, fixture = simple3, showDebugInfo = true,
      expectedBytecodeSelection = """
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
    """.trimMargin("|"),
    )
  }

  fun `test simple 3 - conditional jumps (2)`() {
    val source = """
      package simple3;
      
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

    doTest(
      sourceWithSelection = source, fixture = simple3, showDebugInfo = false,
      expectedBytecodeSelection = """
      |   L0
      |    GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
      |    ALOAD 0
      |    ILOAD 1
      |    INVOKEVIRTUAL simple3/Main.returnsBool (Z)Ljava/lang/Boolean;
      |    INVOKEVIRTUAL java/lang/Boolean.equals (Ljava/lang/Object;)Z
      |    IFEQ L1
      |   L2
      |    LDC "foo"
      |    ARETURN
      |   L1
    """.trimMargin("|"),
    )
  }

  /**
   * Bytecode fixtures, as compiled with javac 23.
   *
   * Bytecode without debug info were created with [removeDebugInfo].
   */
  companion object Fixtures {
    data class Fixture(
      val source: String,
      val bytecode: String,
      val bytecodeWithDebugInfo: String,
    )

    private fun createFixture(name: String): Fixture {
      return Fixture(
        source = PlatformTestUtil.loadFileText("$testDataPath/$name/Main.java"),
        bytecode = PlatformTestUtil.loadFileText("$testDataPath/$name/bytecode.txt"),
        bytecodeWithDebugInfo = PlatformTestUtil.loadFileText("$testDataPath/$name/bytecodeWithDebugInfo.txt"),
      )
    }

    private val testDataPath: String
      get() = PathManager.getHomePath() + "/community/plugins/byteCodeViewer/testData/lineMapping"

    private val simple1: Fixture = createFixture("simple1")

    private val simple2: Fixture = createFixture("simple2")

    private val simple3: Fixture = createFixture("simple3")
  }
}
