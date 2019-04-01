// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.Groovy30Test
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class NestedBlocksDfaTest extends Groovy30Test implements TypingTest, HighlightingTest {

  @Before
  void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
  }

  @Test
  void 'outer reference'() {
    typingTest '''
def a = 1;
{
  <caret>a
}
''', JAVA_LANG_INTEGER
  }

  @Test
  void 'reassign in nested block'() {
    typingTest '''
def a = 1;
{
  a = 'str'
}
<caret>a
''', JAVA_LANG_STRING
  }

  @Test
  void 'reassign in double nested block'() {
    typingTest '''
def a = 1;
{
  {
    a = 'str'
  }
}
<caret>a
''', JAVA_LANG_STRING
  }

  @Test
  void 'defined in nested block'() {
    typingTest '''
{
  def a = 'str'
}
<caret>a
''', null
  }

  @Test
  void 'defined in while block'() {
    typingTest '''
while ("".equals("sad")) {
  def a = 'str'
}
<caret>a
''', null
  }

  @Test
  void 'defined in try block'() {
    typingTest '''
try {
    def a = 1
} finally {
    <caret>a
}
''', null
  }

  @Test
  void 'twice defined in nested blocks'() {
    typingTest '''
{
    def a = 1
} 

{
    def a = ""
}
<caret>a
''', null
  }

  @Test
  void 'binding defined in nested blocks'() {
    typingTest '''
{
    a = 1
} 

{
    a = ""
}
<caret>a
''', JAVA_LANG_STRING
  }

  @Test
  void 'binding and local var defined in nested blocks'() {
    typingTest '''
{
    a = 1
} 

{
   def a = ""
}
<caret>a
''', JAVA_LANG_INTEGER
  }

  @Test
  void 'filed and local var defined in nested block'() {
    typingTest '''
class A {
    def a = "String"
    def m() {
        {
            def a = 1
        }
        <caret>a

    }
}
''', JAVA_LANG_STRING
  }

  @Test
  void 'filed and local var defined in nested block 2'() {
    typingTest '''
class A {
    def a = "String"
    def m() {
        {
            def a = 1
            <caret>a
        }
    }
}
''', JAVA_LANG_INTEGER
  }

  @Test
  void 'highlight local field initialized twice'() {
    highlightingTest '''

    def a = "String";
    {
        def <error descr="Variable 'a' already defined">a</error> = 1
    }
'''
  }

  @Test
  void 'highlight local field in nested blocks'() {
    highlightingTest '''
{
    def a = "String";
}   

{
    def a = 1
}
'''
  }
}


