// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider

/**
 * @author peter
 */
@CompileStatic
class GroovyDocumentationTest extends LightCodeInsightFixtureTestCase {

  void testGenericMethod() {
    myFixture.configureByText 'a.groovy', '''
class Bar<T> { java.util.List<T> foo(T param); }
new Bar<String>().f<caret>oo();
  '''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """\
Bar
<a href="psi_element://java.util.List"><code>List</code></a>&lt;<a href="psi_element://java.lang.String"><code>String</code></a>&gt; foo (<a href="psi_element://java.lang.String"><code>String</code></a> param)"""
  }

  void testGenericField() {
    myFixture.configureByText 'a.groovy', '''
class Bar<T> { T field; }
new Bar<Integer>().fi<caret>eld
  '''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """\
Bar
<a href="psi_element://java.lang.Integer"><code>Integer</code></a> getField ()"""
  }

  void testLink() {
    doTest '''\
class Gr {
  /**
   * Use {@link #bar()} from class {@link Gr} instead
   */
  void foo() {}
  void bar() {}
}
new Gr().fo<caret>o()
''', '''\
<div class='definition'><pre><a href="psi_element://Gr"><code>Gr</code></a><br>void&nbsp;<b>foo</b>()</pre></div><div class='content'>
     Use <a href="psi_element://Gr#bar()"><code>bar()</code></a> from class <a href="psi_element://Gr"><code>Gr</code></a> instead
   <p></div><table class='sections'><p></table>'''
  }

  void 'test link with label'() {
    doTest '''\
/**
 * check this out {@link java.lang.CharSequence character sequences}
 */
def docs() {}

<caret>docs()
''', '''<div class='definition'><pre>\
<a href="psi_element://_"><code>_</code></a><br>\
<a href="psi_element://java.lang.Object"><code>Object</code></a>&nbsp;<b>docs</b>()</pre></div>\
<div class='content'>
   check this out <a href="psi_element://java.lang.CharSequence"><code>character sequences</code></a>
 <p></div>\
<table class='sections'><p></table>'''
  }

  void 'test link to method'() {
    doTest '''\
class Main {
  /**
   * Link 1: {@link #foo(String[])} 
   * <p>
   * Link 2: {@link #bar(String[])}
   * <p>
   * Link 3: {@link #bar(String[], Integer)}
   */
  static void docs() {}
  void foo(String[] args) {}
  void bar(String[] args) {}
  void bar(String[] args, Integer i) {}
}
Main.<caret>docs()
''', '''\
<div class='definition'><pre>\
<a href="psi_element://Main"><code>Main</code></a><br>\
static&nbsp;void&nbsp;<b>docs</b>()\
</pre></div>\
<div class='content'>
     Link 1: <a href="psi_element://Main#foo(java.lang.String[])"><code>foo(String[])</code></a> 
     <p>
     Link 2: <a href="psi_element://Main#bar(java.lang.String[])"><code>bar(String[])</code></a>
     <p>
     Link 3: <a href="psi_element://Main#bar(java.lang.String[], java.lang.Integer)"><code>bar(String[], Integer)</code></a>
   <p>\
</div>\
<table class='sections'><p></table>\
'''
  }

  void 'test untyped local variable'() {
    doTest '''\
def aa = 1
a<caret>a
''', '''\
<div class='definition'><pre><a href="psi_element://java.lang.Object"><code>Object</code></a> <b>aa</b></pre></div><table class='sections'></table><p>[inferred type] <a href="psi_element://java.lang.Integer"><code>Integer</code></a>'''
  }

  void 'test implicit closure parameter'() {
    doTest '''\
List<String> ss = []
ss.collect { i<caret>t }
''', '''\
<div class='definition'><pre><a href="psi_element://java.lang.Object"><code>Object</code></a> <b>it</b></pre></div><table class='sections'></table><p>[inferred type] <a href="psi_element://java.lang.String"><code>String</code></a>'''
  }

  private void doTest(String text, String doc) {
    myFixture.configureByText '_.groovy', text
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    def provider = new GroovyDocumentationProvider()
    def info = provider.generateDoc(ref.resolve(), ref.element)
    assertEquals(doc, info)
  }
}
