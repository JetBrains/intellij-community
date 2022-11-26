package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase

class RedCodeCompletionTest : FullLinePythonCompletionTestCase() {
  override fun getBasePath(): String = "testData/completion/features/red-code"

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
}
