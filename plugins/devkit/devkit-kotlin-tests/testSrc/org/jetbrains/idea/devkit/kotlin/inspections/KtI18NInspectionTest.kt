// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KtI18NInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(com.intellij.codeInspection.i18n.I18nInspection())
  }

  fun testFunctionParameters() {
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

}

