/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.controlFlow

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.flow.GrControlFlowAnalyzerImpl
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class NewControlFlowTest extends LightCodeInsightFixtureTestCase {

  void 'test empty method'() {
    doTest '''
def foo() {}
''', '''
0: RETURN
'''
  }

  void 'test field reference'() {
    doTest '''
class Foo { def a }
def fieldReference(Foo f) {
  <caret>f.@a
}
''', '''
0: PUSH f
1: MEMBER_REFERENCE: f.@a
2: PUSH a|f
3: CHECK RETURN f.@a
4: RETURN
5: RETURN
'''
  }

  void 'test implicit getter'() {
    doTest '''
class Foo { def a }
def foo(Foo f) {
  <caret>f.a
}
''', '''
0: PUSH f
1: CALL_METHOD: f.a
2: CHECK RETURN f.a
3: RETURN
4: RETURN
'''
  }

  void 'test implicit getter on class with explicit getter'() {
    doTest '''
class Foo { 
  def a 
  def getA() {a}
}
def foo(Foo f) {
  <caret>f.a
}
''', '''
0: PUSH f
1: CALL_METHOD: f.a
2: CHECK RETURN f.a
3: RETURN
4: RETURN
'''
  }

  void 'test explicit getter'() {
    doTest '''
class Foo { def a }
def foo(Foo f) {
  <caret>f.getA()
}
''', '''
0: PUSH f
1: CALL_METHOD: f.getA()
2: CHECK RETURN f.getA()
3: RETURN
4: RETURN
'''
  }

  void 'test simple method call'() {
    doTest '''
class Foo { def foo (a,b,c) {} }
def foo(Foo f) {
  <caret>f.foo(1,2,3)
}
''', '''
0: PUSH f
1: PUSH 1
2: PUSH 2
3: PUSH 3
4: CALL_METHOD: f.foo(1,2,3)
5: CHECK RETURN f.foo(1,2,3)
6: RETURN
7: RETURN
'''
  }

  void 'test method call with named arguments'() {
    doTest '''
class Foo {
  def foo (a, b, c) {}
}
def foo(Foo f) {
  <caret>f.foo(a: 1, 2, c: 3, 4)
}
''', '''
0: PUSH f
1: PUSH 1
2: PUSH 3
3: PUSH 2
4: PUSH 4
5: CALL_METHOD: f.foo(a: 1, 2, c: 3, 4)
6: CHECK RETURN f.foo(a: 1, 2, c: 3, 4)
7: RETURN
8: RETURN
'''
  }

  void 'test method call with closure arguments'() {
  }

  void 'test unresolved variables'() {
    doTest '''
a + b
''', '''
0: PUSH <unknown>
1: PUSH <unknown>
2: BINOP +
3: CHECK RETURN a + b
4: RETURN
5: RETURN
'''
  }

  void 'test binary expression: plus'() {
    doTest '''
def foo(a, b) {
  a + b
}
''', '''
0: PUSH a
1: PUSH b
2: BINOP +
3: CHECK RETURN a + b
4: RETURN
5: RETURN
'''
  }

  void 'test binary expression: minus'() {
    doTest '''
def foo(a,b) { a-b }
''', '''
0: PUSH a
1: PUSH b
2: BINOP ...
3: CHECK RETURN a-b
4: RETURN
5: RETURN
'''
  }

  void 'test if'() {
    doTest '''
def foo(c, a, b) {
  if (c) a else b
}
''', '''
0: PUSH c
1: !cond?_goto 6
2: PUSH a
3: CHECK RETURN a
4: RETURN
5: GOTO: 9
6: PUSH b
7: CHECK RETURN b
8: RETURN
9: Finish IF statement; flushing []
10: RETURN
'''
  }

  void 'test if without else branch'() {
    doTest '''
def foo(c, a) {
  if (c) a
}
''', '''
0: PUSH c
1: !cond?_goto 5
2: PUSH a
3: CHECK RETURN a
4: RETURN
5: Finish IF statement; flushing []
6: RETURN
'''
  }

  void 'test unary logic negation'() {
    doTest '''
def foo(a) { !a }
''', '''
0: PUSH a
1: NOT
2: CHECK RETURN !a
3: RETURN
4: RETURN
'''
  }

  void 'test unary bitwise negation'() {
    doTest '''
def foo (a) { ~a }
'''
  }

  void 'test safe navigation with unresolved members'() {
    doTest '''
def foo (a) { a?.b?.c }
'''
  }

  void 'test safe navigation' () {
    
  }
  
  public void doTest(String text, expectedFlow = null) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    final file = (GroovyFile)myFixture.file
    final caretModel = myFixture.editor.caretModel
    final elementAtCaret = file.findElementAt(caretModel.currentCaret.offset)
    final owner = ControlFlowUtils.findControlFlowOwner(elementAtCaret)
    final flow = new GrControlFlowAnalyzerImpl<>(new GrDfaValueFactory(), owner).buildControlFlow()
    if (expectedFlow) {
      assertEquals(expectedFlow.trim(), (flow as String).trim())
    }
    else {
      println flow
    }
  }
}
