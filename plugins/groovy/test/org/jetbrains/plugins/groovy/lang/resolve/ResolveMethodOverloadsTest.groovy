// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST

@CompileStatic
class ResolveMethodOverloadsTest extends GroovyLatestTest implements ResolveTest {

  @Test
  void 'void argument List vs Object'() {
    def method = resolveTest 'def foo(Object o); def foo(List l); void bar(); <caret>foo(bar())', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText(JAVA_LANG_OBJECT)
  }

  @Test
  void 'null argument List vs Object'() {
    def method = resolveTest 'def foo(Object o); def foo(List l); <caret>foo(null)', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText(JAVA_LANG_OBJECT)
  }

  @Test
  void 'null argument List vs erased Object'() {
    def method = resolveTest '''\
def <R> void bar(List<R> l) {}
def <R> void bar(R r) {}
<caret>bar(null)
''', PsiMethod
    assert method.parameterList.parameters.last().type.equalsToText('R')
  }

  @Test
  void 'list equals null'() {
    def method = resolveTest 'void usage(List<String> l) { l.<caret>equals(null) }', PsiMethod
    assert method.containingClass.qualifiedName == JAVA_UTIL_LIST
    assert method.parameterList.parameters.last().type.equalsToText(JAVA_LANG_OBJECT)
  }

  @Test
  void 'list == null'() {
    def method = resolveTest 'void usage(List<String> l) { l <caret>== null }', PsiMethod
    assert method.containingClass.qualifiedName == JAVA_UTIL_LIST
    assert method.parameterList.parameters.last().type.equalsToText(JAVA_LANG_OBJECT)
  }
}
