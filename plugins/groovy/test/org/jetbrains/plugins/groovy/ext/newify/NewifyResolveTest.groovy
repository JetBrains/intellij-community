/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

class NewifyResolveTest extends GroovyResolveTestCase {
  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() {
    super.setUp()
    fixture.addClass('''
class Aa {
  String name;
  public Aa(){}
}
''')

    fixture.addClass('''
class Cc {
  String name;
}
''')
  }

  void testAutoNewify() {
    checkResolve"""
@Newify
class B {
  def a = Aa.ne<caret>w()
}
""", PsiMethod

    checkResolve """
@Newify
class B {
  def a = Aa.ne<caret>w(name :"bar")
}
""", PsiMethod

    checkResolve"""
class B {
  @Newify
  def a = Aa.ne<caret>w(name :"bar")
}
""", PsiMethod

    checkResolve """
class B {
  @Newify
  def a (){ return Aa.ne<caret>w(name :"bar")}
}
""", PsiMethod

    checkResolve"""
class B {
  @Newify(auto = false)
  def a (){ return Aa.ne<caret>w()}
}
"""
  }

  void testNewifyByClass() {
    checkResolve """
@Newify(Aa)
class B {
  def a = A<caret>a()
}
""", PsiMethod, "Aa"

    checkResolve """
@Newify(Cc)
class B {
  def a = C<caret>c()
}
""", PsiMethod, "Cc"

    checkResolve"""
@Newify(Aa)
class B {
  def a = A<caret>a(name :"bar")
}
""", PsiMethod, "Aa"

    checkResolve"""
class B {
  @Newify(Aa)
  def a = A<caret>a(name :"bar")
}
""", PsiMethod, "Aa"

    checkResolve """
class B {
  @Newify(Aa)
  def a (){ return A<caret>a(name :"bar")}
}
""", PsiMethod, "Aa"


    checkResolve """
class B {
  @Newify
  def a (){ return A<caret>a(name :"bar")}
}
""", PsiClass

  }

  void checkResolve(@NotNull String text, @Nullable Class<?> refType = null, @Nullable String returnType = null) {
    def resolved = configureByText(text).resolve()
    if (refType != null) {
      assertInstanceOf(resolved, refType)
    } else {
      assertNull(resolved)
    }
    if (returnType != null && resolved instanceof PsiMethod) {
      assertEquals returnType, resolved.returnType.canonicalText
    }
  }
}
