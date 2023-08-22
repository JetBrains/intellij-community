// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.NoSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

@CompileStatic
class GroovyImplicitNullArgumentHintProviderTest extends InlayHintsProviderTestCase {

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5
  }

  def doTest(@Language("Groovy") String text) {
    doTestProvider("test.groovy", text, new GroovyImplicitNullArgumentHintProvider(), new NoSettings())
  }

  void 'test call with implicit null argument'() {
    doTest """
def foo(String s) {}

foo(/*<# null #>*/)
"""
  }

  void 'test no hint if parameter is primitive'() {
    doTest """
def foo(short s) {}

foo()
"""
  }

  void 'test no hint if parameter is vararg'() {
    doTest """
def foo(String... s) {}

foo()
"""
  }

  void 'test show hint if parameter type is omitted'() {
    doTest """
def foo(s) {}

foo(/*<# null #>*/)
"""
  }

  void 'test show hint on constructor'() {
    doTest """
class Foo {
  Foo(String s) {}
}

new Foo(/*<# null #>*/)
"""
  }

  void 'test don\'t show hint with @CompileStatic'() {
    doTest """
@groovy.transform.CompileStatic
class Foo {
  def foo(String s) {}

  def bar() { foo<error>()</error> }
}
"""
  }
}
