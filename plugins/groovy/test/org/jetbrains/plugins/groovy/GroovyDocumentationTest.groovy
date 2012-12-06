/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * @author peter
 */
class GroovyDocumentationTest extends LightCodeInsightFixtureTestCase {

  public void testGenericMethod() {
    myFixture.configureByText 'a.groovy', '''
class Bar<T> { java.util.List<T> foo(T param); }
new Bar<String>().f<caret>oo();
  '''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """\
Bar
<a href="psi_element://java.util.List"><code>List</code></a>&lt;<a href="psi_element://java.lang.String"><code>String</code></a>&gt; foo (<a href="psi_element://java.lang.String"><code>String</code></a> param)"""
  }

  public void testGenericField() {
    myFixture.configureByText 'a.groovy', '''
class Bar<T> { T field; }
new Bar<Integer>().fi<caret>eld
  '''
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assert CtrlMouseHandler.getInfo(ref.resolve(), ref.element) == """\
Bar
<a href="psi_element://java.lang.Integer"><code>Integer</code></a> getField ()"""
  }

}
