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

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.psi.PsiClass

class GroovyGoToTypeDeclarationTest extends LightJavaCodeInsightFixtureTestCase {

  void testGoToTypeDeclarationMethod() {
    myFixture.configureByText("g.groovy", """
class A {

  public def method() {
    return new B();
  }

  {
    method<caret>()
  }
}

class B {

}
""")

    def res = GotoTypeDeclarationAction.findSymbolType(myFixture.editor, myFixture.caretOffset)
    assertInstanceOf(res, PsiClass.class)
    assertEquals("B", ((PsiClass)res).getName())
  }

  void testGoToTypeDeclarationVariable() {
    myFixture.configureByText("g.groovy", """
class A {

  {
    def a = new B()
    println(a<caret>)
  }
}

class B {

}
""")

    def res = GotoTypeDeclarationAction.findSymbolType(myFixture.editor, myFixture.caretOffset)
    assertInstanceOf(res, PsiClass.class)
    assertEquals("B", ((PsiClass)res).getName())
  }

}
