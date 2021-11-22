// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInspection.i18n.I18nInspection
import com.intellij.codeInspection.i18n.NlsInfo
import com.intellij.psi.PsiClassOwner
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.Nls

class KtI18NInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_8
  }
  
  // To avoid Java highlighting
  private fun configureKt(fileText: String) {
    myFixture.configureByText("Foo.kt", fileText)
  }

  fun testFunctionParameters() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
       class Foo {
          fun foo(s: String) {
            foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>)
            foo(<warning descr="Hardcoded string literal: \"templated ${"\$"}s end\"">"templated ${"\$"}s end"</warning>)
            foo(<warning descr="Hardcoded string literal: \"concatenated \"">"concatenated "</warning> + s + <warning descr="Hardcoded string literal: \" end\"">" end"</warning>)
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testPropagateToReceiver() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
       public fun String.trimIndent(): String = this
       fun foo(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String) { }
       fun bar() {
          foo("foo bar")
          foo("foo bar".trimIndent())
       }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testPropagateThroughLocal() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
       fun foo(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String) { }
       fun foo1(<warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String) { }
       fun bar() {
          var text = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'text' initializer is redundant">"foo"</warning>
          text = "bar"
          foo(text)
          var text2 = <warning descr="Hardcoded string literal: \"foo\""><warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'text2' initializer is redundant">"foo"</warning></warning>
          text2 = <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>
          foo1(text2)
       }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testPropagateThroughLocalNls() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    configureKt("""
       fun foo(@org.jetbrains.annotations.Nls(capitalization=org.jetbrains.annotations.Nls.Capitalization.Title) <warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String) { }
       fun bar() {
          var text = <warning descr="Hardcoded string literal: \"foo\""><warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'text' initializer is redundant">"foo"</warning></warning>
          text = <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>
          text += <warning descr="Hardcoded string literal: \"baz\"">"baz"</warning>
          foo(text)
       }
    """.trimIndent())
    val parameterNls = NlsInfo.forModifierListOwner((file as PsiClassOwner).classes[0].methods[0].parameterList.parameters[0])
    assertTrue(parameterNls is NlsInfo.Localized)
    assertEquals(Nls.Capitalization.Title, (parameterNls as NlsInfo.Localized).capitalization)
    myFixture.testHighlighting()
  }

  fun testPropagateThroughLocalNlsFix() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    configureKt("""
       fun foo(@org.jetbrains.annotations.Nls(capitalization=org.jetbrains.annotations.Nls.Capitalization.Title) message: String) { }
       fun bar(text : String) {
         foo(te<caret>xt)
       }
    """.trimIndent())
    val parameterNls = NlsInfo.forModifierListOwner((file as PsiClassOwner).classes[0].methods[0].parameterList.parameters[0])
    assertTrue(parameterNls is NlsInfo.Localized)
    assertEquals(Nls.Capitalization.Title, (parameterNls as NlsInfo.Localized).capitalization)
    myFixture.doHighlighting()
    val action = myFixture.getAvailableIntention(QuickFixBundle.message("create.annotation.text", "Nls"))
    assertNotNull(action)
    myFixture.launchAction(action!!)
    myFixture.checkResult("""
      import org.jetbrains.annotations.Nls

      fun foo(@org.jetbrains.annotations.Nls(capitalization=org.jetbrains.annotations.Nls.Capitalization.Title) message: String) { }
      fun bar(@Nls text : String) {
        foo(text)
      }
    """.trimIndent())
  }
  
  fun testPropagateThroughElvisNls() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    configureKt("""
        @org.jetbrains.annotations.Nls
        fun bar(param: String?): String {
          return <warning descr="Reference to non-localized string is used where localized string is expected">param</warning> ?: ""
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testPropertyAssignment() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    myFixture.addClass(
      """public class X {
        |native @org.jetbrains.annotations.Nls String getFoo();
        |native void setFoo(@org.jetbrains.annotations.Nls String s);
        |}""".trimMargin())
    configureKt("""
       class Foo : X() {
          @org.jetbrains.annotations.Nls var prop = "";
       
          fun foo(x : X) {
            prop = <warning descr="Hardcoded string literal: \"kotlin setter\"">"kotlin setter"</warning>
            x.foo = <warning descr="Hardcoded string literal: \"java qualified setter\"">"java qualified setter"</warning>
            foo = <warning descr="Hardcoded string literal: \"java superclass setter\"">"java superclass setter"</warning>
            this.foo = <warning descr="Hardcoded string literal: \"java superclass qualified setter\"">"java superclass qualified setter"</warning>
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testAnnotationValues() {
    myFixture.enableInspections(I18nInspection())
    myFixture.addClass("""
       import org.jetbrains.annotations.*;
       @interface Foo {
         @NonNls String value();
         @NonNls String value2() default "test";
       }

       @interface Foos {
         Foo[] value() default {};
       }
    """.trimIndent())
    configureKt("""
       @Foo("Single param")
       class X

       @Foo(value = "Named param", value2 = "Named param2")
       class Y

       @Foos(Foo("Nested"))
       class Z

       @Foos(value = [Foo("Nested named")])
       class T
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testKotlinAnnotationValues() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
      import org.jetbrains.annotations.*

      // TODO: support annotations without @get: prefix
      annotation class Foo(@get:NonNls val value: String, @get:NonNls val value2: String = "test")

      annotation class Foos(vararg val value: Foo = [])

      @Foo("Single param")
      class X
      
      @Foo(value = "Named param", value2 = "Named param2")
      class Y
      
      @Foos(Foo("Nested"))
      class Z
      
      @Foos(value = [Foo("Nested named")])
      class T
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testNamedParameters() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun foo(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning>: Int = 0, 
                @org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'b' is never used">b</warning>: String, 
                <warning descr="[UNUSED_PARAMETER] Parameter 'c' is never used">c</warning>: String) {}
        
        fun test() {
          foo(b = "foo bar", c = <warning descr="Hardcoded string literal: \"violation\"">"violation"</warning>)
          foo(c = <warning descr="Hardcoded string literal: \"violation\"">"violation"</warning>, b = "foo bar")
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testWhen() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun foo(@org.jetbrains.annotations.NonNls nonNls: String, foo: String) {
          when (nonNls) {
            "foo bar" -> {}
          }
          @org.jetbrains.annotations.NonNls val <warning descr="[UNUSED_VARIABLE] Variable 'nonNls2' is never used">nonNls2</warning> = when (true) {
            true -> "foo bar2"
            else -> "foo bar3"
          }
          when (foo) {
            <warning descr="Hardcoded string literal: \"foo bar\"">"foo bar"</warning> -> {}
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testIf() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun foo() {
          @org.jetbrains.annotations.NonNls val prefix = if (true) "foo bar" else ""
          val <warning descr="[UNUSED_VARIABLE] Variable 'suffix' is never used">suffix</warning> = if (true) <warning descr="Hardcoded string literal: \"foo bar\"">"foo bar"</warning> else prefix
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testStringBuilder() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun foo() {
          @org.jetbrains.annotations.NonNls val buffer = StringBuilder()
          buffer.append("foo bar")
          val other = StringBuilder()
          other.append(<warning descr="Hardcoded string literal: \"foo bar\"">"foo bar"</warning>)
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testStringBuilder2() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    configureKt("""
        import org.jetbrains.annotations.*

        fun foo() {
          @Nls val buffer = StringBuilder()
          fill(buffer)
          consume(buffer.toString())
        }
        
        fun consume(@Nls <warning descr="[UNUSED_PARAMETER] Parameter 's' is never used">s</warning> : String) {}
        fun fill(<warning descr="[UNUSED_PARAMETER] Parameter 'sb' is never used">sb</warning>: StringBuilder) {}
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testFunctionReturn() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun debug(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'lazyMessage' is never used">lazyMessage</warning>: () -> String) {}
        fun debug2(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'lazyMessage' is never used">lazyMessage</warning>: java.util.function.Supplier<String>) {}
    
        fun test() {
            debug { "foo" }
            debug2 { "foo" }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testExtensionMethod() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        class Test {}

        fun Test.weigh(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'id' is never used">id</warning>: String): Boolean = true

        fun test() {
          fun Test.weighLocal(@org.jetbrains.annotations.NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'id' is never used">id</warning>: String): Boolean = true
        
          val <warning descr="[UNUSED_VARIABLE] Variable 'test' is never used">test</warning> = Test().weigh("priority")
          // TODO: parameter for local function doesn't report annotations
          val <warning descr="[UNUSED_VARIABLE] Variable 'test2' is never used">test2</warning> = Test().weighLocal(<warning descr="Hardcoded string literal: \"priority\"">"priority"</warning>)
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testLocals() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun test() {
          @org.jetbrains.annotations.NonNls var <warning descr="[ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE] Variable 'x' is assigned but never accessed">x</warning>: String = "foo bar"
          <warning descr="[UNUSED_VALUE] The value '\"bar foo\"' assigned to 'var x: String defined in test' is never used">x =</warning> "bar foo"
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testConcatenation() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        fun test() {
          @org.jetbrains.annotations.NonNls val <warning descr="[UNUSED_VARIABLE] Variable 'id' is never used">id</warning> = "foo:" + "bar"
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testList() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
       import org.jetbrains.annotations.*
       import java.util.*
       
       public fun <T> listOf(vararg elements: T): List<T> = Arrays.asList(*elements)
       
       class X {
         companion object {
           @NonNls private val LIST: List<String> = listOf("Foo bar", "Boo far")
         }
       }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testCompareWithNonNls() {
    myFixture.enableInspections(I18nInspection())
    myFixture.addClass("public interface Y {@org.jetbrains.annotations.NonNls String getNonNls();}")
    configureKt("""
       import org.jetbrains.annotations.*
       
       @NonNls
       fun getLastWord(@NonNls string: String): String = string
       
       class X {
         @NonNls
         fun getNonNls() = "hello"

         @NonNls
         val prop = "hello"
      
         fun test(@NonNls word: String, x: Array<X>, y: Array<Y>): Boolean {
           return word == "Hello world1" &&
                  this.getNonNls() != "Hello world2" &&
                  x[0].prop != "Hello world3" &&
                  y[0].nonNls != "Hello world4" &&
                  getLastWord(word) != "Hello world5"
         }
       }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testException() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        public typealias UnsupportedOperationException = java.lang.UnsupportedOperationException
        class Err(message:String): Throwable(message)

        fun test2() {
          throw Err("foo bar")
        }
      
        fun test() {
          throw UnsupportedOperationException("foo bar")
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testNoException() {
    val inspection = I18nInspection()
    inspection.ignoreForExceptionConstructors = false
    myFixture.enableInspections(inspection)
    configureKt("""
        class Err(message:String): Throwable(message)
      
        fun test() {
          throw Err(<warning descr="Hardcoded string literal: \"foo bar\"">"foo bar"</warning>)
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testFunctionParametersOnlyNls() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    configureKt("""
       class Foo {
          fun foo(@org.jetbrains.annotations.Nls s: String) {
            foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>)
            foo(<warning descr="Hardcoded string literal: \"templated ${"\$"}s end\"">"templated ${"\$"}s end"</warning>)
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testDslNls() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    configureKt("""
        class ReferredClass {
          val instance = "value"
          companion object {
            const val static = "value"
          }
        }
        val a = createContext<Any> {
          another {
            myFun(<warning descr="Hardcoded string literal: \"Highlight\"">"Highlight"</warning>, ReferredClass.static)
            myFun(<warning descr="Hardcoded string literal: \"Highlight\"">"Highlight"</warning>, ReferredClass().instance)
          }
        }
        fun <T : Any> createContext(<warning descr="[UNUSED_PARAMETER] Parameter 'exec' is never used">exec</warning>: ContextClass<T>.() -> Unit) { }
        class WrapperClass
        class ContextClass<T : Any> {
          fun another(<warning descr="[UNUSED_PARAMETER] Parameter 'exec' is never used">exec</warning>: WrapperClass.() -> Unit) { }
          fun WrapperClass.myFun(@org.jetbrains.annotations.Nls <warning descr="[UNUSED_PARAMETER] Parameter 'text' is never used">text</warning>: String, <warning descr="[UNUSED_PARAMETER] Parameter 'any' is never used">any</warning>: T) { }
        }
        """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testScopeFunctionsNls() {
    val inspection = I18nInspection()
    inspection.run {  }
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    configureKt("""
        import org.jetbrains.annotations.Nls

        fun showMessage(@Nls <warning descr="[UNUSED_PARAMETER] Parameter 'text' is never used">text</warning>: String) {}
        
        public inline fun <T, R> T.run(block: T.() -> R): R { return block() }
        public inline fun <T, R> T.let(block: (T) -> R): R { return block(this) }
        
        class Window {
          val name: String = "Name"
        }
        
        @Nls
        fun localize(name: String) = <warning descr="Hardcoded string literal: \"Hello ${'$'}name\"">"Hello ${'$'}<warning descr="Reference to non-localized string is used where localized string is expected">name</warning>"</warning>
        
        fun mainFunction() {
          val window = Window()
        
          val resultWithRun = window.run { localize("${'$'}name") }
          val resultWithoutRun = localize(window.name)
          val resultWithLet = window.let { localize("${'$'}{it.name}") }
          val resultWithLetNonLocalized = window.let { <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning> }
        
          showMessage(resultWithRun)
          showMessage(resultWithoutRun)
          showMessage(resultWithLet)
          showMessage(resultWithLetNonLocalized)
        }
        """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testDefaultValues() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        import org.jetbrains.annotations.NonNls
        
        fun foo1(<warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String = <warning descr="Hardcoded string literal: \"Hello World\"">"Hello World"</warning>) {}
        fun foo2(@NonNls <warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String = "Hello World") {}
        """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testConstructorParameter() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
      import org.jetbrains.annotations.Nls
      
      class B1: A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning>)
      class B2(a: Int): A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning> + a)
      open class A(@Nls val text: String)
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testReturnValues() {
    myFixture.enableInspections(I18nInspection())
    configureKt("""
        val a = <warning descr="Hardcoded string literal: \"Test text\"">"Test text"</warning>
        val b get() = <warning descr="Hardcoded string literal: \"Test text\"">"Test text"</warning>
        val c: String
            get() {
                return <warning descr="Hardcoded string literal: \"Test text\"">"Test text"</warning>
            }

        fun a() = <warning descr="Hardcoded string literal: \"Test text\"">"Test text"</warning>
        fun b(): String {
            return <warning descr="Hardcoded string literal: \"Test text\"">"Test text"</warning>
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testReturnWithWhen() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    configureKt("""
        import org.jetbrains.annotations.*
        
        private const val LEFT = "Left"
        private const val RIGHT = "Right"
        private const val NONE = "NONE"
        
        @Nls
        fun message(str: String): String = message(str)
        
        @Nls
        fun optionName(@NonNls option: String): String = when (option) {
          LEFT -> message("combobox.tab.placement.left")
          RIGHT -> message("combobox.tab.placement.right")
          else -> message("combobox.tab.placement.none")
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testStartsWith() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    myFixture.addClass("package kotlin;public class String {public boolean startsWith(String string);}")
    configureKt("""
        import org.jetbrains.annotations.*
        fun test(@Nls x : String) {
            if (x.startsWith(<warning descr="Hardcoded string literal: \"Hello\"">"Hello"</warning>)) {}
            if (x == <warning descr="Hardcoded string literal: \"Hello\"">"Hello"</warning>) {}
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testTypeUseAnnotation() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    inspection.setReportUnannotatedReferences(true)
    myFixture.enableInspections(inspection)
    myFixture.addClass("package kotlin;public class String {public boolean startsWith(String string);}")
    myFixture.addClass("import org.jetbrains.annotations.*;\n" +
                       "import java.lang.annotation.*;\n" +
                       "@Nls(capitalization = Nls.Capitalization.Sentence)\n" +
                       "@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})\n" +
                       "public @interface Label { }")
    configureKt("""
        import org.jetbrains.annotations.*

        fun getString(): @Label String {return <warning descr="Hardcoded string literal: \"str\"">"str"</warning>}
        @Nls
        fun test(): String {
            return getString()
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

}
