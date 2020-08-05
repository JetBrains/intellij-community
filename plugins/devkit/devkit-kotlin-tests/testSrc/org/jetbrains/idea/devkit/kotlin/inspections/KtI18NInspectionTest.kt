// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KtI18NInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  fun testFunctionParameters() {
    myFixture.enableInspections(com.intellij.codeInspection.i18n.I18nInspection())
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
  
  fun testFunctionParametersOnlyNls() {
    val inspection = com.intellij.codeInspection.i18n.I18nInspection()
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
    myFixture.enableInspections(com.intellij.codeInspection.i18n.I18nInspection())
    myFixture.configureByText("Foo.kt", """
      import org.jetbrains.annotations.Nls
      
      class B1: A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning>)
      class B2(a: Int): A(<warning descr="Hardcoded string literal: \"Text for i18n\"">"Text for i18n"</warning> + a)
      open class A(@Nls val text: String)
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testReturnValues() {
    myFixture.enableInspections(com.intellij.codeInspection.i18n.I18nInspection())
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

