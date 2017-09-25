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


  void testImplicitFieldType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText('''
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    def aa = "i'm string"
    def foo() { a<caret>a }
}
''').element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiClassType)
    assertTrue(type.canonicalText == CommonClassNames.JAVA_LANG_OBJECT)
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
