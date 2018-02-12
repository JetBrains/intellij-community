/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
