/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;


import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class MissingReturnTest extends LightGroovyTestCase {

  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}highlighting/missingReturn";
  }

  public void testMissingReturnWithLastLoop() throws Throwable { doTest(); }
  public void testMissingReturnWithUnknownCall() throws Throwable { doTest(); }
  public void testMissingReturnWithIf() throws Throwable { doTest(); }
  public void testMissingReturnWithAssertion() throws Throwable { doTest(); }
  public void testMissingReturnThrowException() throws Throwable { doTest(); }
  public void testMissingReturnTryCatch() throws Throwable { doTest(); }
  public void testMissingReturnLastNull() throws Throwable { doTest(); }
  public void testMissingReturnImplicitReturns() throws Throwable {doTest();}
  public void testMissingReturnOvertReturnType() throws Throwable {doTest();}
  public void testMissingReturnFromClosure() throws Throwable {doTest();}
  public void testReturnsWithoutValue() throws Throwable {doTest();}
  public void testEndlessLoop() throws Throwable {doTest();}
  public void testEndlessLoop2() throws Throwable {doTest();}
  public void testExceptionWithFinally() throws Throwable {doTest();}
  public void testOnlyAssert() throws Throwable {doTest();}
  public void testImplicitReturnNull() throws Throwable {doTest();}
  public void testMissingReturnInClosure() {doTest();}
  public void testFinally() {doTest();}
  public void testClosureWithExplicitExpectedType() {doTest()}

  public void testAssert() {
    doTextText('''\
Integer.with {
  assert valueof('1') == 1
}                         //no error

Integer.with {
  if (foo) {
    return 2
  }
  else {
    print 1
  }
<warning descr="Not all execution paths return a value">}</warning>
''')
  }


  public void testInterruptFlowInElseBranch() {
    doTextText('''\
//correct
public int foo(int bar) {
    if (bar < 0) {
        return -1
    }
    else if (bar > 0) {
        return 12
    }
    else {
        throw new IllegalArgumentException('bar cannot be zero!')
    }
}

//incorrect
public int foo2(int bar) {
    if (bar < 0) {
        return -1
    }
    else if (bar > 0) {
        return 12
    }
<warning descr="Not all execution paths return a value">}</warning>
''')
  }

  void testSwitch() {
    doTextText('''\
//correct
String foo(e) {
    switch(e) {
        case 1: 1; break
        case 2: return 2; break
        default: 3
    }
}

//incorrect
String foo2(e) {
    switch(e) {
        case 1: 1; break
        case 2: break
        default: 3
    }
<warning descr="Not all execution paths return a value">}</warning>
''')
  }

  void testSwitchWithIf() {
    doTextText('''\
//correct
String foo(e) {
    switch(e) {
        case 1:
            if (e=='a') {
                return 'a'
            }
            else {
                'c'
            }
            break
        default: ''
    }
}

//incorrect
String foo2(e) {
    switch(e) {
        case 1:
            if (e=='a') {
                return 'a'
            }
            else {
           //     'c'
            }
            break
        default: ''
    }
<warning descr="Not all execution paths return a value">}</warning>
''')
  }

  void testConditional() {
    doTextText('''\
//correct
List createFilters1() {
    abc ? [1] : []
}

//correct
List createFilters2() {
    abc ?: [1]
}

//incorrect
List createFilters3() {
<warning descr="Not all execution paths return a value">}</warning>
''')
  }

  void testReturnWithoutValue0() {
    doTextText('''\
int foo() {
  if (abc) {
    return
  }

  return 2
<warning>}</warning>
''')
  }

  void testReturnWithoutValue1() {
    doTextText('''\
int foo() {
  return
<warning>}</warning>
''')
  }

  void testReturnWithoutValue2() {
    doTextText('''\
void foo() {
  if (abc) {
    return
  }

  print 2
} //no error
''')
  }

  void testReturnWithoutValue3() {
    doTextText('''\
void foo() {
  return
} //no error
''')
  }

  void testSingleThrow1() {
    doTextText('''
      int foo() {
        throw new RuntimeException()
      } //correct
    ''')
  }

  void testSingleThrow2() {
    doTextText('''
      void foo() {
        throw new RuntimeException()
      } //correct
    ''')
  }

  void testSingleThrow3() {
    doTextText('''
      def foo() {
        throw new RuntimeException()
      } //correct
    ''')
  }

  void testThrowFromIf1() {
    doTextText('''
      int foo() {
        if (1) {
          throw new RuntimeException()
        }
      <warning>}</warning>
    ''')
  }

  void testThrowFromIf2() {
    doTextText('''
      void foo() {
        if (1) {
          throw new RuntimeException()
        }
      } //correct
    ''')
  }

  void testThrowFromIf3() {
    doTextText('''
      def foo() {
        if (1) {
          throw new RuntimeException()
        }
      } //correct
    ''')
  }

  void testThrowFromIf4() {
    doTextText('''
      int foo() {
        if (1) {
          throw new RuntimeException()
        }
        else {
          return 1
        }
      }
    ''')
  }

  void testThrowFromIf5() {
    doTextText('''
      void foo() {
        if (1) {
          throw new RuntimeException()
        }
        else {
          throw new RuntimeException()
        }
      } //correct
    ''')
  }

  void testThrowFromIf6() {
    doTextText('''
      def foo() {
        if (1) {
          throw new RuntimeException()
        }
        else {
          return 1
        }
      } //correct
    ''')
  }

  void doTextText(String text) {
    myFixture.configureByText('___.groovy', text)
    myFixture.enableInspections(MissingReturnInspection)
    myFixture.testHighlighting(true, false, false)
  }

  private void doTest() {
    myFixture.enableInspections(new MissingReturnInspection());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

}
