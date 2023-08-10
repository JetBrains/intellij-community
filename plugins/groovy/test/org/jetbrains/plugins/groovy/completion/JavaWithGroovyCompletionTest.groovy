// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiMethod

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
