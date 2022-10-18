package org.jetbrains.completion.full.line.kotlin.supporters.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class RedCodeCompletionTest : FullLineCompletionTestCase() {
  override fun getBasePath(): String = "testData/completion/features/red-code"

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

  fun `test nulls in Kotlin`() {
    myFixture.configureByFile("KotlinNulls.kt")
    testIfSuggestionsRefCorrect("null", "null,", "null;", "a", "123", "0", "\"str\"")
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
