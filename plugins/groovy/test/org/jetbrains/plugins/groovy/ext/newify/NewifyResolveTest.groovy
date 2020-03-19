// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.newify

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

class NewifyResolveTest extends GroovyResolveTestCase {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5

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

  void testNewifyByPattern() {
    checkResolve """
@Newify(pattern = /Aa/)
class B {
  def a = A<caret>a()
}
""", PsiMethod, "Aa"

    checkResolve """
@Newify(pattern = /Cc/)
class B {
  def a = C<caret>c()
}
""", PsiMethod, "Cc"

    checkResolve"""
@Newify(pattern = /[A-Z].*/)
class B {
  def a = A<caret>a(name :"bar")
}
""", PsiMethod, "Aa"

    checkResolve"""
class B {
  @Newify(pattern = /[A-Z].*/)
  def a = A<caret>a(name :"bar")
}
""", PsiMethod, "Aa"

    checkResolve """
class B {
  @Newify(pattern = /[A-Z].*/)
  def a (){ return A<caret>a(name :"bar")}
}
""", PsiMethod, "Aa"

    checkResolve """
class B {
  @Newify(pattern = /[a-z].*/)
  def a (){ return A<caret>a(name :"bar")}
}
"""

    checkResolve """
@Newify(pattern = /.*/)
class B {
  class zz {
  }
  
  def a() { return z<caret>z() }
}""", PsiMethod, "B.zz"
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
"""
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
