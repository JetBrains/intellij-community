// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.i18n.I18nInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KtI18NInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  fun testFunctionParameters() {
    myFixture.enableInspections(I18nInspection())
    myFixture.configureByText("Foo.kt", """
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
    myFixture.configureByText("Foo.kt", """
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
    myFixture.configureByText("Foo.kt", """
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
    myFixture.configureByText("Foo.kt", """
       fun foo(@org.jetbrains.annotations.Nls <warning descr="[UNUSED_PARAMETER] Parameter 'message' is never used">message</warning>: String) { }
       fun bar() {
          var text = <warning descr="Hardcoded string literal: \"foo\""><warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'text' initializer is redundant">"foo"</warning></warning>
          text = <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>
          text += <warning descr="Hardcoded string literal: \"baz\"">"baz"</warning>
          foo(text)
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
    myFixture.configureByText("Foo.kt", """
       class Foo {
          @org.jetbrains.annotations.Nls var prop = "";
       
          fun foo(x : X) {
            // TODO: prop is resolved to getter 
            // but Kotlin properties do not propagate annotations to getter ultra-light method 
            prop = "value"
            // TODO: foo is not resolved to X.getFoo()
            x.foo = "value"
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testFunctionParametersOnlyNls() {
    val inspection = I18nInspection()
    inspection.setIgnoreForAllButNls(true)
    myFixture.enableInspections(inspection)
    myFixture.configureByText("Foo.kt", """
       class Foo {
          fun foo(@org.jetbrains.annotations.Nls s: String) {
            foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>)
            foo(<warning descr="Hardcoded string literal: \"templated ${"\$"}s end\"">"templated ${"\$"}s end"</warning>)
          }
        }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testConstructorParameter() {
    myFixture.enableInspections(I18nInspection())
    myFixture.configureByText("Foo.kt", """
      import org.jetbrains.annotations.Nls
      
      class B1: A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning>)
      class B2(a: Int): A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning> + a)
      open class A(@Nls val text: String)
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testReturnValues() {
    myFixture.enableInspections(I18nInspection())
    myFixture.configureByText("Foo.kt", """
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

}

