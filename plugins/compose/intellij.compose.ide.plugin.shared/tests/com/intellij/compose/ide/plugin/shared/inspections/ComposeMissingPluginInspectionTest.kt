// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.lang.annotation.HighlightSeverity
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

abstract class ComposeMissingPluginInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableComposeInTest()
  }

  private data class ExpectedError(val text: String, val line: Int? = null)

  private infix fun String.atLine(line: Int) = ExpectedError(this, line)

  private fun expectedError(text: String) = ExpectedError(text)

  private fun assertHighlightedErrors(
    @Language("kotlin") code: String,
    vararg expected: ExpectedError,
  ) {
    myFixture.configureByText("Test.kt", code.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
      .filter { it.description == ComposeIdeBundle.message("compose.inspection.missing.plugin.name") }

    val document = myFixture.editor.document

    assertEquals(expected.size, errors.size)
    expected.zip(errors).forEach { (exp, actual) ->
      assertEquals(exp.text, actual.text)
      if (exp.line != null) {
        assertEquals(exp.line, document.getLineNumber(actual.startOffset))
      }
    }
  }

  fun `test composable call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
      @Composable fun MyComposable() {}
  
      fun caller() {
        MyComposable()
      }
    """,
    "MyComposable" atLine 4
  )

  fun `test multiple composable calls are all flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun A() {}
      @Composable fun B() {}
      @Composable fun C() {}

      fun caller() {
        A()
        B()
        C()
      }
    """,
    "A" atLine 7,
    "B" atLine 8,
    "C" atLine 9
  )

  fun `test composable call from another composable is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun Child() {}

      @Composable fun Parent() {
        Child()
      }
    """,
    "Child" atLine 5
  )

  fun `test deeply nested composable call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun level1() {
        fun level2() {
          fun level3() {
            run {
              listOf(1).forEach {
                MyComposable()
              }
            }
          }
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call button is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable
      fun Button(onClick: () -> Unit, content: @Composable () -> Unit) {}
      
      @Composable
      fun Text(text: String) {}

      @Composable
      fun MyFunction(){
        Button(onClick = {}) { 
          Text("Click me!")
        }
      }
    """,
    expectedError("Button"),
    expectedError("Text")
  )

  fun `test composable call inside nested function is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun outer() {
        fun inner() {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call with arguments is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable(text: String, count: Int) {}

      fun caller() {
        MyComposable("hello", 42)
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call with named arguments is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable(text: String, count: Int) {}

      fun caller() {
        MyComposable(text = "hello", count = 42)
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call with default parameter values is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable(text: String = "default") {}

      fun caller() {
        MyComposable()
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call with vararg parameter is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable(vararg items: String) {}

      fun caller() {
        MyComposable("a", "b", "c")
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call with generic parameter is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun <T> GenericComposable(item: T) {}

      fun caller() {
        GenericComposable(42)
      }
    """,
    expectedError("GenericComposable")
  )

  fun `test composable extension function call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun String.composableExtension() {}

      fun caller() {
        "hello".composableExtension()
      }
    """,
    expectedError("composableExtension")
  )

  fun `test composable operator function call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      class Wrapper {
        @Composable operator fun invoke() {}
      }

      fun caller() {
        val w = Wrapper()
        w()
      }
    """,
    expectedError("w")
  )

  fun `test composable infix function call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable
      infix fun Int.composableAdd(other: Int): Int = this + other

      fun caller() {
        1.composableAdd(2)
      }
    """,
    expectedError("composableAdd")
  )

  fun `test composable lambda parameter is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      fun TakesComposable(content: @Composable () -> Unit) {}

      fun caller() {
        TakesComposable {}
      }
    """
  )

  fun `test composable return type is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      fun returnsComposable(): @Composable () -> Unit = {}

      fun caller() {
        returnsComposable()()
      }
    """,
    expectedError("returnsComposable()")
  )

  fun `test composable call with trailing lambda is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun Container(content: @Composable () -> Unit) {}

      fun caller() {
        Container {
        }
      }
    """,
    expectedError("Container")
  )

  fun `test function with multiple composable lambda parameters is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      fun TakesMultipleComposables(
        header: @Composable () -> Unit,
        content: @Composable () -> Unit
      ) {}

      fun caller() {
        TakesMultipleComposables(header = {}, content = {})
      }
    """
  )

  fun `test nullable composable parameter is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
  
      fun TakesNullableComposable(content: (@Composable () -> Unit)?) {}
  
      fun caller() {
        TakesNullableComposable {}
      }
    """
  )

  fun `test higher-order composable parameter type is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
      @Composable fun MyComposable() {}
      fun takesHigherOrder(producer: () -> @Composable () -> Unit) {}
  
      fun caller() {
        takesHigherOrder { { MyComposable()} }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call via type alias is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      typealias MyContent = @Composable () -> Unit

      @Composable
      fun TakesContent(content: MyContent) { content() }

      fun caller() {
        TakesContent {}
      }
    """,
    expectedError("content"),
    expectedError("TakesContent")
  )

  fun `test composable call inside lambda is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        val action = {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside lambda with composable function type is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
  
      @Composable fun MyComposable() {}
  
      val foo: @Composable () -> Unit = { MyComposable() }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside anonymous function is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        val myAnonFunc = fun() {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside composable annotated lambda is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
  
      @Composable fun MyComposable() {}
  
      val foo = @Composable { MyComposable() }
    """,
    expectedError("MyComposable")
  )

  fun `test direct invocation of composable lambda variable is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      fun Wrapper(content: @Composable () -> Unit) {
        content()
      }
    """,
    expectedError("content")
  )

  fun `test composable call in top-level initializer is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun compute(): @Composable () -> Unit = {}

      val result = compute()
    """,
    expectedError("compute")
  )

  fun `test composable call in property getter is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun compute(): @Composable () -> Unit = {}

      val myProp: Any
        get() = compute()
    """,
    expectedError("compute")
  )

  fun `test composable call inside property setter is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      var myProp: String = ""
        set(value) {
          field = value
          MyComposable()
        }
      """,
    expectedError("MyComposable")
  )

  fun `test composable call inside class init block is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      class MyClass {
        init {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside companion object function is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      class MyClass {
        companion object {
          fun create() {
            MyComposable()
          }
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside object expression is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      interface Callback { fun execute() }

      fun caller() {
        val cb = object : Callback {
          override fun execute() {
            MyComposable()
          }
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside if branch is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller(condition: Boolean) {
        if (condition) {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside when branch is flagged`() = assertHighlightedErrors(
    """
        import androidx.compose.runtime.Composable

        @Composable fun MyComposable() {}

        fun caller(value: Int) {
          when (value) {
            1 -> MyComposable()
            else -> {}
          }
        }
      """,
    expectedError("MyComposable")
  )

  fun `test composable call inside forEach is flagged`() = assertHighlightedErrors(
    """
    import androidx.compose.runtime.Composable

    @Composable fun MyComposable() {}

    fun caller() {
      listOf(1, 2).forEach {
        MyComposable()
      }
    }
  """,
    expectedError("MyComposable")
  )

  fun `test composable call inside try block is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        try {
          MyComposable()
        } catch (_: Exception) {}
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside run block is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        run {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside let block is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        "test".let {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test composable call inside also block is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}

      fun caller() {
        "test".also {
          MyComposable()
        }
      }
    """,
    expectedError("MyComposable")
  )

  fun `test multiple files with composable calls across files are flagged`() {
    myFixture.addFileToProject("Composables.kt", """
        import androidx.compose.runtime.Composable

        @Composable fun SharedComposable() {}
      """.trimIndent())

    assertHighlightedErrors(
      """
        fun caller() {
          SharedComposable()
        }
      """,
      expectedError("SharedComposable")
    )
  }

  fun `test non-composable call is not flagged`() = assertHighlightedErrors(
    """
      fun regularFunction() {}

      fun caller() {
        regularFunction()
      }
      """
  )

  fun `test standard library call is not flagged`() = assertHighlightedErrors(
    """
      fun caller() {
        println("hello")
        listOf(1, 2, 3)
        mapOf("a" to 1)
      }
    """
  )

  fun `test constructor call is not flagged`() = assertHighlightedErrors(
    """
      class MyClass(val name: String)

      fun caller() {
        MyClass("test")
      }
    """
  )

  fun `test function with non-composable lambda parameter is not flagged`() = assertHighlightedErrors("""
      fun takesLambda(block: () -> Unit) {}

      fun caller() {
        takesLambda {}
      }
    """
  )

  fun `test function with no parameters is not flagged`() = assertHighlightedErrors(
    """
      fun simple() {}

      fun caller() {
        simple()
      }
    """
  )

  fun `test extension function without composable annotation is not flagged`() = assertHighlightedErrors(
    """
      fun String.myExtension(): Int = length

      fun caller() {
        "hello".myExtension()
      }
    """
  )

  fun `test higher order function with regular lambda is not flagged`() = assertHighlightedErrors(
    """
      fun higherOrder(transform: (Int) -> String) {}

      fun caller() {
        higherOrder { it.toString() }
      }
    """
  )

  fun `test file with only declarations and no calls is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun MyComposable() {}
      fun regularFunction() {}
      val myVal = 42
    """
  )

  fun `test empty file is not flagged`() = assertHighlightedErrors("")

  fun `test file with only imports is not flagged`() = assertHighlightedErrors("""import androidx.compose.runtime.Composable""")

  fun `test non-composable function with same name as composable is not flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      @Composable fun Greeting(name: String) {}
      fun Greeting() {}

      fun caller() {
        Greeting() // calls the non-composable overload
      }
    """
  )

  fun `test suspend function is not flagged`() = assertHighlightedErrors(
    """
      suspend fun mySuspend() {}

      suspend fun caller() {
        mySuspend()
      }
    """
  )

  fun `test infix function call is not flagged`() = assertHighlightedErrors(
    """
      infix fun Int.add(other: Int): Int = this + other

      fun caller() {
        1 add 2
      }
    """
  )

  fun `test movableContentOf call is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.annotation.RememberInComposition

      @Composable fun baseComposable() {}

      @RememberInComposition
      fun <T> movableContentOf(content: @Composable () -> T): @Composable () -> T = content

      fun caller() {
        val movableComposable: @Composable () -> Unit = movableContentOf { baseComposable() }
      }
    """,
    "movableContentOf" atLine 9,
    "baseComposable" atLine 9
  )

  fun `test FocusRequester constructor is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.annotation.RememberInComposition

      class FocusRequester @RememberInComposition constructor()

      fun caller() {
        val focusRequester = FocusRequester()
      }
    """,
    expectedError("FocusRequester")
  )

  fun `test composable property access via top-level property is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      val currentTheme: String
        @Composable get() = "dark"

      fun caller() {
        val t = currentTheme
      }
    """,
    expectedError("currentTheme")
  )

  fun `test composable property access is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      class Theme {
        val colors: String
          @Composable get() = "colors"
      }

      fun caller(theme: Theme) {
        val c = theme.colors
      }
    """,
    expectedError("colors")
  )

  fun `test non-composable property access is not flagged`() = assertHighlightedErrors(
    """
      class Theme {
        val colors: String = "colors"
      }

      fun caller(theme: Theme) {
        val c = theme.colors
      }
    """
  )

  fun `test composable property access with get annotation target is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      interface Typography {
        @get:Composable val labelTextStyle: String
      }

      fun caller(typography: Typography) {
        val s = typography.labelTextStyle
      }
    """,
    expectedError("labelTextStyle")
  )

  fun `test composable property access inside composable function is flagged`() = assertHighlightedErrors(
    """
      import androidx.compose.runtime.Composable

      val currentTheme: String
        @Composable get() = "dark"

      @Composable
      fun MyComposable() {
        val t = currentTheme
      }
    """,
    expectedError("currentTheme")
  )
}