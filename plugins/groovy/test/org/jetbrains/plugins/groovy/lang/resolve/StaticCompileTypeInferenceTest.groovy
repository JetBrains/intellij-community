// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

@CompileStatic
class StaticCompileTypeInferenceTest extends TypeInferenceTestBase {

  void testExplicitFieldType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    String aa = "i'm string"
    def foo() { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertTrue(type.canonicalText == CommonClassNames.JAVA_LANG_STRING)
  }


  void testImplicitWithInstanceofType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    def aa = 1
    def foo() { 
      if (aa instanceof String) {
        a<caret>a 
      }
   }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertEquals(CommonClassNames.JAVA_LANG_STRING, type.canonicalText)
  }

  void testExplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    Object aa = "i'm string"
    def foo() { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertTrue(type.canonicalText == CommonClassNames.JAVA_LANG_OBJECT)
  }

  void testImplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    def aa 
    def foo() { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.canonicalText)
  }

void testEnumObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    static enum E {
    FF, g, h
  } 
    def foo() { E.F<caret>F }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertEquals("Foo.E", type.canonicalText)
  }

  void testImplicitObjectTypeWithInitializer() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

class Foo {
    def aa = 1
    @CompileStatic
    def foo() { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.canonicalText)
  }

  void testImplicitParameterType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

class Foo {
    def foo(aa) { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type == null)
  }

  void 'test variable type'() {
    final GrReferenceExpression ref = configureByText('''\
@groovy.transform.CompileStatic
class Foo {
  def foo() {
    List<?> ll = ["a"]
    l<caret>l
  }
}
''').element as GrReferenceExpression
    assert ref.type.canonicalText == 'java.util.List<java.lang.String>'
  }
}
