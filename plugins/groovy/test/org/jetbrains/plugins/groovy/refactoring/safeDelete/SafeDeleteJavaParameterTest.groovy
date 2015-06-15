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
package org.jetbrains.plugins.groovy.refactoring.safeDelete

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class SafeDeleteJavaParameterTest extends LightGroovyTestCase {

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
  void foo(int ba<caret>r) {}
}
''', '''\
/**
@see A#foo(int)
*/
class X{}
''', '''\
/**
@see A#foo()
*/
class X{}
''')
  }

  private void doTest(String java, String groovy, String groovyAfter) {
    myFixture.configureByText('test.java', java)
    def groovyFile = myFixture.addFileToProject('test.groovy', groovy)

    final PsiElement psiElement = TargetElementUtil
            .findTargetElement(myFixture.editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    SafeDeleteHandler.invoke(myFixture.project, [psiElement] as PsiElement[], true)

    assertEquals(groovyAfter, groovyFile.text)
  }
}
