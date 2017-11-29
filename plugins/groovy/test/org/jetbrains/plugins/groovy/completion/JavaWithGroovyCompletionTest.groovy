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
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiMethod;

/**
 * @author peter
 */
class JavaWithGroovyCompletionTest extends GroovyCompletionTestBase {

  void "test using java keywords in member names"() {
    myFixture.addFileToProject 'a.groovy', '''
class Foo {
  static void "const"() {}
  static final int "continue" = 2;
}
'''
    myFixture.configureByText 'a.java', 'class Bar {{ con<caret> }}'
    myFixture.complete(CompletionType.BASIC, 2)
    assert !(myFixture.lookupElementStrings.contains('const'))
    assert !(myFixture.lookupElementStrings.contains('continue'))
  }

  void "test using java expression keywords in member names"() {
    myFixture.addFileToProject 'a.groovy', '''
class Foo {
  static void "this"() {}
}
'''
    myFixture.configureByText 'a.java', 'class Bar {{ this<caret> }}'
    myFixture.complete(CompletionType.BASIC, 2)
    assert !myFixture.lookupElements.find { it.lookupString == 'this' && it.object instanceof PsiMethod }
  }
}
