// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import groovy.transform.CompileStatic

@CompileStatic
class GroovyImplicitNullArgumentHintProviderTest extends InlayHintsProviderTestCase {

  def doTest(String text) {
    testProvider("test.groovy", text, new GroovyImplicitNullArgumentHintProvider(), new GroovyImplicitNullArgumentHintProvider.Settings())
  }

  void 'test call with implicit null argument'() {
    doTest """
def foo(String s) {}

foo(<# null #>)
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

foo(<# null #>)
"""
  }

  void 'test show hint on constructor'() {
    doTest """
class Foo {
  Foo(String s) {}
}

new Foo(<# null #>)
"""
  }
}
