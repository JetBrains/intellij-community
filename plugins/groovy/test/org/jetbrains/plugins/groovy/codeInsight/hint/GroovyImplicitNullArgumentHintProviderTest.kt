// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyImplicitNullArgumentHintProviderTest : DeclarativeInlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_2_5
  }

  fun doTest(@Language("Groovy") text: String) {
    doTestProvider("test.groovy", text, GroovyImplicitNullArgumentHintProvider())
  }

  fun `test call with implicit null argument`() = doTest("""
def foo(String s) {}

foo(/*<# null #>*/)
""")

  fun `test no hint if parameter is primitive`() = doTest("""
def foo(short s) {}

foo()
""")

  fun `test no hint if parameter is vararg`() = doTest("""
def foo(String... s) {}

foo()
""")

  fun `test show hint if parameter type is omitted`() = doTest("""
def foo(s) {}

foo(/*<# null #>*/)
""")

  fun `test show hint on constructor`() {
    doTest("""
class Foo {
  Foo(String s) {}
}

new Foo(/*<# null #>*/)
""")
  }

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
