// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jdom.Element
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GroovyInlayParameterHintsProviderTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() {
    super.setUp()
    fixture.addFileToProject 'Foo.groovy', '''\
class Foo {
  Foo() {}
  Foo(a, b, c) {}
  void simple(a) {}
  void simple(a, b) {}
  void defaultArgs(a, b = 1,c) {}
  void mapArgs(Map m, a) {}
  void varArgs(a, def... b) {}
  void combo(Map m, a, b = 1, def ... c) {}
  def getAt(a) {}
}
'''
  }

  @Override
  void tearDown() throws Exception {
    ParameterNameHintsSettings.getInstance().loadState(new Element("element"))
    super.tearDown()
  }

  private void testInlays(String text) {
    fixture.configureByText '_.groovy', text.replaceAll(/<hint name="([.\w]*)">/, "")
    checkResult text.replaceAll("<caret>", "")
  }

  private void checkResult(String expected) {
    fixture.doHighlighting()

    def manager = ParameterHintsPresentationManager.instance
    def inlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
    Map<Integer, String> actualParameterHints = inlays.findAll {
      manager.isParameterHint(it)
    }.collectEntries {
      [(it.offset): manager.getHintText(it)]
    }

    def actualText = new StringBuilder(fixture.file.text)
    def offset = 0
    actualParameterHints.each { hintOffset, hintName ->
      if (hintName != null) {
        def hintText = hintName.substring(0, hintName.length() - 1)
        def hintString = /<hint name="$hintText">/
        actualText.insert(hintOffset + offset, hintString)
        offset += hintString.length()
      }
    }

    assertEquals(expected, actualText.toString())
  }

  void 'test method call expressions'() {
    testInlays '''\
def aa = new Object()
def foo = new Foo()
foo.with {
  simple(<hint name="a">null)
  simple(aa)
  simple(<hint name="a">-1, <hint name="b">+2)
  simple(<hint name="a">++1, <hint name="b">2++)
  simple(<hint name="a">--1, <hint name="b">2--)
  simple(<hint name="a">~1, -"foo")
  defaultArgs(<hint name="a">1, <hint name="c">2)
  defaultArgs(<hint name="a">1, <hint name="b">2, <hint name="c">3)
  mapArgs(foo: 1, <hint name="a">null, bar: 2)
  varArgs(<hint name="a">1, <hint name="...b">2, 3, 4)
  varArgs(<hint name="a">1, <hint name="...b">2, aa)
  varArgs(<hint name="a">1, <hint name="...b">aa, 3)
  varArgs(<hint name="a">1, aa, aa)
  combo(<hint name="a">1, foo: 10, <hint name="b">2, <hint name="...c">3, 4, bar: 20, 5)
}
'''
  }

  void 'test method call applications'() {
    testInlays '''\
def aa = new Object()
def foo = new Foo()
foo.with {
  simple <hint name="a">null
  simple aa
  defaultArgs <hint name="a">1, <hint name="c">2
  defaultArgs <hint name="a">1, <hint name="b">2, <hint name="c">3
  mapArgs foo: 1, <hint name="a">null, bar: 2
  varArgs <hint name="a">1, <hint name="...b">2, 3, 4
  varArgs <hint name="a">1, <hint name="...b">2, aa
  varArgs <hint name="a">1, <hint name="...b">aa, 3
  varArgs <hint name="a">1, aa, aa
  combo <hint name="a">1, foo: 10, <hint name="b">2, <hint name="...c">3, 4, bar: 20, 5
}
'''
  }

  void 'test new expression'() {
    testInlays 'new Foo(<hint name="a">1, <hint name="b">2, <hint name="c">4)'
  }

  void 'test constructor invocation'() {
    testInlays '''\
class Bar {
  Bar() {
    this(<hint name="a">1, <hint name="b">4, <hint name="c">5)
  }

  Bar(a, b, c) {}
}
'''
  }

  void 'test enum constants'() {
    testInlays '''\
enum Baz {
  ONE(<hint name="a">1, <hint name="b">2),
  TWO(<hint name="a">4, <hint name="b">3)
  Baz(a, b) {}
}
'''
  }

  void 'test no DGM inlays'() {
    testInlays '[].each {}'
  }

  void 'test closure arguments'() {
    testInlays '''\
new Foo().with {
  simple {}
  simple(<hint name="a">{})
  simple <hint name="a">null, <hint name="b">{}
}
'''
  }

  void 'test method with default arguments blacklist'() {
    testInlays '''\
new Foo().with {
   defaultArgs(<hint name="a">1, <hint name="c">2)
   defaultArgs(aa, <hint name="b"><caret>2, <hint name="c">3)
}
'''
    def intention = fixture.getAvailableIntention("Do not show hints for current method")
    assert intention
    fixture.launchAction intention
    checkResult '''\
new Foo().with {
   defaultArgs(1, 2)
   defaultArgs(aa, 2, 3)
}
'''
  }
}
