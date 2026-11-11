// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.junit.jupiter.api.Test

@TestApplication
class GroovyImplicitNullArgumentHintProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  fun doTest(@Language("Groovy") text: String) {
    doTestProvider(myFixture, "test.groovy", text, GroovyImplicitNullArgumentHintProvider())
  }

  @Test
  fun `test call with implicit null argument`(): Unit = doTest("""
def foo(String s) {}

foo(/*<# null #>*/)
""")

  @Test
  fun `test no hint if parameter is primitive`(): Unit = doTest("""
def foo(short s) {}

foo()
""")

  @Test
  fun `test no hint if parameter is vararg`(): Unit = doTest("""
def foo(String... s) {}

foo()
""")

  @Test
  fun `test show hint if parameter type is omitted`(): Unit = doTest("""
def foo(s) {}

foo(/*<# null #>*/)
""")

  @Test
  fun `test show hint on constructor`() {
    doTest("""
class Foo {
  Foo(String s) {}
}

new Foo(/*<# null #>*/)
""")
  }

  @Test
  fun `test don't show hint with @CompileStatic`() {
    doTest("""
@groovy.transform.CompileStatic
class Foo {
  def foo(String s) {}

  def bar() { foo<error>()</error> }
}
""")
  }
}
