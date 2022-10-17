// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class SafeDeleteJavaTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + "refactoring/safeDeleteJavaParameter/"

  void testGroovyCall() {
    doTest('''\
class A {
  void foo(int ba<caret>r) {}
}
''', '''\
new A().foo(2)
''', '''\
new A().foo()
''')
  }

  void testGroovyDocRef() {
    doTest('''\
class A {
  void foo(int ba<caret>r, long baz) {}
}
''', '''\
/**
@see A#foo(int,long)
*/
class X{}
''', '''\
/**
@see A#foo(long)
*/
class X{}
''')
  }
  
  void testGroovyTypeArgs() {
    doTest('''\
class A<<caret>T> {
}
''', '''\
class X {
  A<String> a = new A<String>()
}
''', '''\
class X {
  A a = new A()
}
''')
  }
  
  void testGroovyTypeArgs2() {
    doTest('''\
class A<<caret>T, K> {
}
''', '''\
class X {
  A<String, String> a = new A<String, String>()
}
''', '''\
class X {
  A<String> a = new A<String>()
}
''')
  }

  private void doTest(String java, String groovy, String groovyAfter) {
    myFixture.configureByText('test.java', java)
    def groovyFile = myFixture.addFileToProject('test.groovy', groovy)

    final PsiElement psiElement = TargetElementUtil
            .findTargetElement(myFixture.editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)

    SafeDeleteHandler.invoke(myFixture.project, [psiElement] as PsiElement[], true)

    assertEquals(groovyAfter, groovyFile.text)
  }
}
