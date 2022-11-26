package org.jetbrains.completion.full.line.java.formatters


internal sealed class JavaFilesPsiCodeFormatterTest : JavaPsiCodeFormatterTest() {
  protected abstract val folder: String

  protected fun doTest(vararg tokens: String) {
    testFile("$folder/${getTestName(true)}.java", tokens.asList(), "java")
  }

  internal class InsideFunctionTests : JavaFilesPsiCodeFormatterTest() {
    override val folder = "inside-function"

    fun testData1() = doTest("f")

    fun testData2() = doTest("System", ".", "out", ".", "println", "(")

    fun testData3() = doTest("f", "(", ")")

    fun testData4() = doTest("System", ".", "out", ".", "println", "(", "\"main functio")

    fun testData5() = doTest("in")

    fun testData6() = doTest()

    fun testData7() = doTest("int", "y", "=")

    fun testData8() = doTest("int", "y", "=")

    fun testData9() = doTest("Stream", ".", "of", "(", "1", ",", "2", ")", ".", "map", "(", "x", "->", "x", ")", ".", "forEach", "(")

    fun testData10() = doTest("int", "y", "=")

    fun testData11() = doTest("for", "(", "int", "i", "=", "0", ";", "i", "<", "10", ";")

    fun testData12() = doTest()

    fun testData13() = doTest("this", ".", "x")

    fun testData14() = doTest("f", "(", ")")

    fun testData15() = doTest()
  }

  internal class InsideClass : JavaFilesPsiCodeFormatterTest() {
    override val folder = "inside-class"

    fun testData1() = doTest("public", "seco")

    fun testData2() = doTest("private", "int")

    fun testData3() = doTest("pr")

    fun testData4() = doTest()

    fun testData5() = doTest("private")

    fun testData6() = doTest()

    fun testData7() = doTest("pri")

    fun testData8() = doTest("int")

    fun testData9() = doTest("in")

    fun testData10() = doTest("vo")

    fun testData11() = doTest("public", "vo")

    fun testData12() = doTest("public", "vo")

    fun testData13() = doTest("vo")

    fun testData14() = doTest("void", "f", "(")

    fun testData15() = doTest("void", "f", "(", "0", "<", "1", ",", "10", ",")

    // TODO optimize prefix
    fun testData16() = doTest("@", "Annotation1", "@", "Annotation2")

    // TODO optimize prefix
    fun testData17() = doTest("@", "Annotation1", "@", "Annotati")

    fun testData18() = doTest("vo")

    fun testData19() = doTest("public", "vo")

    fun testData20() = doTest("public", "void", "g")

    fun testData21() = doTest("private", "int", "x")

    // TODO keep javadoc in json
    fun testData22() = doTest("public", "static", "void", "mai")
  }

  internal class InsideFile : JavaFilesPsiCodeFormatterTest() {
    override val folder = "inside-file"

    fun testData1() = doTest()

    fun testData2() = doTest("package", "mock_data", ".")

    fun testData3() = doTest()

    fun testData4() = doTest()

    fun testData5() = doTest("import", "java", ".")

    fun testData6() = doTest("public", "class", "Ma")

    fun testData7() = doTest("public", "class", "List")

    fun testData8() = doTest("public", "class", "List", "extend")

    fun testData9() = doTest("public", "class", "List", "extends")

    fun testData10() = doTest("imp")

    fun testData11() = doTest("import")
  }
}
