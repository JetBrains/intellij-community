package org.jetbrains.completion.full.line.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class RedCodeCompletionTest : FullLineCompletionTestCase() {
  override fun getBasePath(): String = "testData/completion/features/red-code"

  fun `test simple Java`() {
    myFixture.addFileToProject(
      "Base.java",
      """
                public class Base {
                    public int basePu;
                    protected int basePt;
                    private int basePv;
                    
                    public void foo() {}
                }
            """.trimIndent()
    )
    myFixture.addFileToProject(
      "Main.java",
      """
                public class Main extends Base {
                    public int a;
                    public int b;
                    
                    public int sum(int a, int b) {
                        return a + b;
                    }
                    
                    public int sum() {
                        return a + b;
                    }
                    
                    public void a() {}
                }
            """.trimIndent(),
    )
    myFixture.addFileToProject(
      "Test.java",
      """
                public class Test {
                    public void foo() {
                        Main main;
                        main.<caret>
                    }
                }
            """.trimIndent(),
    )

    myFixture.configureByFile("Test.java")

    testIfSuggestionsRefCorrect(
      *arrayOf(
        "a",
        "b",
        "sum(1, 2)",
        "sum",
        "basePu",
        "basePt",
        "basePv",
        "foo",
      ).let { arr -> arr + arr.map { "$it()" } }
    )
    clearFLCompletionCache()
    testIfSuggestionsRefInCorrect("none", "java", "Test", "protected", "public", "void")
  }

  fun `test simple Kotlin`() {
    myFixture.addFileToProject(
      "Base.kt",
      """
                open class Base {
                    val basePu: Int = 1
                    val basePt: Int = 2
                    val basePv: Int = 3

                    fun foo() {}
                }
            """.trimIndent()
    )
    myFixture.addFileToProject(
      "BaseKt.kt",
      """
                class Main : Base() {
                    val a: Int = 0
                    val b: Int = 0

                    fun sum(a: Int, b: Int): Int {
                        return a + b;
                    }

                    fun sum(): Int {
                        return a + b;
                    }

                    fun a() {}
                }
            """.trimIndent(),
    )
    myFixture.addFileToProject(
      "Test.kt",
      """
                class Test {
                    fun foo() {
                        val main = Main()
                        main.a()
                        main.<caret>
                    }
                }
            """.trimIndent(),
    )

    myFixture.configureByFile("Test.kt")

    testIfSuggestionsRefCorrect(
      *arrayOf(
        "a",
        "b",
        "sum",
        "basePu",
        "basePt",
        "basePv",
        "foo",
        "sum(1, 2)",
      ).let { arr -> arr + arr.map { "$it()" } }
    )
    testIfSuggestionsRefInCorrect("public", "void", "protected", "c", "none")
  }

  fun `test full Python`() {
    myFixture.addFileToProject(
      "another.py",
      """
                baseA = 1
                baseC = "str"
                class Base:
                    var1 = 1
                    def foo():
                        pass
                    def var2(a, b):
                        pass
                
            """.trimIndent()
    )
    myFixture.addFileToProject(
      "main.py",
      """
                import another
                from another import Base
                a = 1
                b = "23"
                def fzz():
                    pass
                class A(Base):
                    var3 = 1
                    def foo1():
                        pass
                new = A().<caret>
            """.trimIndent(),
    )

    myFixture.configureByFile("main.py")

    testIfSuggestionsRefCorrect(
      *arrayOf(
        "another",
        "a",
        "b",
        "fzz",
        "A",
        "var3",
        "foo1",
        "test",
        "another.baseA",
        "another.baseC",
        "another.Base",
        "another.var1",
        "another.foo",
        "another.var2",
        "def",
        "pass",
      ).let { arr -> arr + arr.map { "$it()" } }
    )
    testIfSuggestionsRefCorrect("none", "python", "main", "baseC", "baseA")
  }

  fun `test nulls in Kotlin`() {
    myFixture.configureByFile("KotlinNulls.kt")
    testIfSuggestionsRefCorrect("null", "null,", "null;", "a", "123", "0", "\"str\"")
  }

  fun `test nulls in Java`() {
    myFixture.configureByFile("JavaNulls.java")
    testIfSuggestionsRefCorrect("null", "null,", "null;", "a", "\"str\"", "123", "0")
  }

  fun `test constructor call in Kotlin`() {
    myFixture.configureByFile("KotlinConstructor.kt")
    testIfSuggestionsRefCorrect("Testing", "Testing()", "class")
    testIfSuggestionsRefInCorrect("a", "b", "a()", "b()")
  }

  fun `test numbers call in Kotlin`() {
    myFixture.configureByFile("IdentifiersAndNumbers.kt")
    testIfSuggestionsRefCorrect("1", "0.123", "null", "\"string\"", "'a'", "class", "fun", "test")
    testIfSuggestionsRefInCorrect("IdentifiersAndNumbers", "a", "b", "variable")
  }
}
