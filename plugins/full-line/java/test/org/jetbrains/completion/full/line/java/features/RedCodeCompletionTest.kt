package org.jetbrains.completion.full.line.java.features

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

  fun `test nulls in Java`() {
    myFixture.configureByFile("JavaNulls.java")
    testIfSuggestionsRefCorrect("null", "null,", "null;", "a", "\"str\"", "123", "0")
  }
}
