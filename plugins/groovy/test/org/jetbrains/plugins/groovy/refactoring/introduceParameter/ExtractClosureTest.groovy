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
package org.jetbrains.plugins.groovy.refactoring.introduceParameter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.refactoring.IntroduceParameterRefactoring
import gnu.trove.TIntArrayList
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromClosureProcessor
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromMethodProcessor
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterHandler
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.IntroduceParameterInfo
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}groovy/refactoring/extractMethod/";
  }

  protected void doTest(String before, String after, List<Integer> toRemove = [], List<Integer> notToUseAsParams = [], boolean forceReturn = true) {
    myFixture.configureByText '______________a____________________.groovy', before

    def handler = new GrIntroduceParameterHandler() {
      @Override
      protected void showDialog(IntroduceParameterInfo info) {

        GrIntroduceParameterSettings helper = new ExtractClosureHelperImpl(info, "closure", false,
                                                                           new TIntArrayList(toRemove as int[]), false,
                                                                           IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                                                                           forceReturn, false, false)
        for (p in notToUseAsParams) {
          helper.parameterInfos[p].passAsParameter = false
        }
        if (helper.toReplaceIn instanceof GrMethod) {
          new ExtractClosureFromMethodProcessor(helper).run()
        }
        else {
          new ExtractClosureFromClosureProcessor(helper).run()
        }
      }
    }

    WriteCommandAction.runWriteCommandAction project, {
      handler.invoke myFixture.project, myFixture.editor, myFixture.file, null
      doPostponedFormatting(myFixture.project)
    }
    myFixture.checkResult after
  }

  static class MethodTest extends ExtractClosureTest {
    void testSimple() {
      doTest('''
def foo(String s) {
    s+=2
    <selection>print s</selection>
}

foo('a')
''', '''
def foo(String s, Closure closure) {
    s+=2
    closure(s)
}

foo('a') { String s -> print s }
''', [], [], false)
    }

    void testRemoveUnused() {
      doTest('''
class X {
    def foo(String s) {
        <selection>print s</selection>
    }
}

new X().foo('a')
''', '''
class X {
    def foo(Closure closure) {
        closure()
    }
}

new X().foo { print 'a' }
''', [0], [0], false)

    }

    void testRemoveUnusedAndGenerateLocal() {
      doTest('''
def foo(String s) {
    <selection>s+=2
    print s</selection>
}

foo('a')
''', '''
def foo(Closure closure) {
    closure()
}

foo {
    String s = 'a'
    s += 2
    print s
}
''', [0], [0], true)
    }

    void testInsertQualifier() {
      doTest('''
class X {
    def foo(String s) {
        <selection>bar()</selection>
    }
    def bar(){}
}

new X().foo('a')
''', '''
class X {
    def foo(Closure closure) {
        closure()
    }
    def bar(){}
}

final X x = new X()
x.foo { x.bar() }
''', [0], [], false)

    }

    void testLocalVarAsParam() {
      doTest('''
def foo(int x, int y) {
    int a = 5
    <selection>print 45+y+a</selection>
}

foo(2, 3)
''', '''
def foo(Closure closure) {
    int a = 5
    closure(a)
}

foo { int a -> print 45 + 3 + a }
''', [0, 1], [0], false)
    }

    void testRPG() {
      doTest '''
adventure()

def adventure() {

    try {
        <selection><caret>killMonsters()
        collectLoot()</selection>
    } catch (ArrowToKneeException) {
        becomeTownGuard()
    }
}
''', '''
adventure {
    killMonsters()
    collectLoot()
}

def adventure(Closure closure) {

    try {
        closure()<caret>
    } catch (ArrowToKneeException) {
        becomeTownGuard()
    }
}
'''
    }

    void testExpression() {
      doTest('''
adventure()

def adventure() {
    try {
        def skill = <selection>killMonsters()+collectLoot()</selection>
    } catch (ArrowToKneeException e) {
        becomeTownGuard()
    }
}
class ArrowToKneeException extends Exception{}
def killMonsters(){2}
def collectLoot(){3}
def becomeTownGuard(){}
''', '''
adventure { return killMonsters() + collectLoot() }

def adventure(Closure<Integer> closure) {
    try {
        def skill = closure()
    } catch (ArrowToKneeException e) {
        becomeTownGuard()
    }
}
class ArrowToKneeException extends Exception{}
def killMonsters(){2}
def collectLoot(){3}
def becomeTownGuard(){}
''')
    }

    void testDontQualify() {
      doTest('''
class Some {
     private static int smth = 1
     private static void doSmth() {}

     void m1() {
         <selection><caret>println smth
         doSmth()</selection>
     }
     void m2() {
         m1()
     }
}
''', '''
class Some {
     private static int smth = 1
     private static void doSmth() {}

     void m1(Closure closure) {
         closure()<caret>
     }
     void m2() {
         m1 {
             println smth
             doSmth()
         }
     }
}
''')
    }

    void testAppStatement() {
      doTest('''
void foo() {
    def s = <selection><caret>"zxcvbn".substring 2 charAt(1)</selection>
}
foo()
''', '''
void foo(Closure<Character> closure) {
    def s = closure()<caret>
}
foo { return "zxcvbn".substring(2).charAt(1) }
''')
    }

    void testStringPart0() {
      doTest('''\
def cl() {
    print 'a<selection>b</selection>c'
}

cl()
''', '''\
def cl(Closure<String> closure) {
    print 'a' + closure()<caret> + 'c'
}

cl { return 'b' }
''')
    }

    void testNull() {
      doTest('''\
def foo() {
    print <selection>null</selection>
}

foo()
''', '''\
def foo(Closure closure) {
    print closure()
}

foo { return null }
''')
    }

  }

  static class ClosureTest extends ExtractClosureTest {
    void testSimple() {
      doTest('''
def foo = {String s ->
    s+=2
    <selection>print s</selection>
}

foo('a')
''', '''
def foo = { String s, Closure closure ->
    s+=2
    <selection>closure(s)</selection>
}

foo('a') { String s -> print s }
''', [], [], false)
    }

    void testRemoveUnused() {
      doTest('''
class X {
    def foo = {String s ->
        <selection>print s</selection>
    }
}

new X().foo('a')
''', '''
class X {
    def foo = { Closure closure ->
        <selection>closure()</selection>
    }
}

final X x = new X()
x.foo { x.print 'a' }
''', [0], [0], false)

    }

    void testRemoveUnusedAndGenerateLocal() {
      doTest('''
def foo = {String s ->
    <selection>s+=2
    print s</selection>
}

foo('a')
''', '''
def foo = { Closure closure ->
    closure()
}

foo {
    String s = 'a'
    s += 2
    print s
}
''', [0], [0], true)
    }

    void testInsertQualifier() {
      doTest('''
class X {
    def foo = {String s ->
        <selection>bar()</selection>
    }
    def bar(){}
}

new X().foo('a')
''', '''
class X {
    def foo = { Closure closure ->
        <selection>closure()</selection>
    }
    def bar(){}
}

final X x = new X()
x.foo { x.bar() }
''', [0], [], false)

    }

    void testLocalVarAsParam() {
      doTest('''
def foo = {int x, int y ->
    int a = 5
    <selection>print 45+y+a</selection>
}

foo(2, 3)
''', '''
def foo = { Closure closure ->
    int a = 5
    <selection>closure(a)</selection>
}

foo { int a -> print 45 + 3 + a }
''', [0, 1], [0], false)
    }

    void testRPG() {
      doTest '''
def adventure = {

    try {
        <selection><caret>killMonsters()
        collectLoot()</selection>
    } catch (ArrowToKneeException) {
        becomeTownGuard()
    }
}

adventure()
''', '''
def adventure = { Closure closure ->

    try {
        <caret>closure()
    } catch (ArrowToKneeException) {
        becomeTownGuard()
    }
}

adventure {
    killMonsters()
    collectLoot()
}
'''
    }

    void testExpression() {
      doTest('''
def adventure = {
    try {
        def skill = <selection>killMonsters()+collectLoot()</selection>
    } catch (ArrowToKneeException e) {
        becomeTownGuard()
    }
}

adventure()

class ArrowToKneeException extends Exception{}
def killMonsters(){2}
def collectLoot(){3}
def becomeTownGuard(){}
''', '''
def adventure = { Closure<Integer> closure ->
    try {
        def skill = <selection>closure()</selection>
    } catch (ArrowToKneeException e) {
        becomeTownGuard()
    }
}

adventure { return killMonsters() + collectLoot() }

class ArrowToKneeException extends Exception{}
def killMonsters(){2}
def collectLoot(){3}
def becomeTownGuard(){}
''')
    }

    void testDontQualify() {
      doTest('''
class Some {
     private static int smth = 1
     private static void doSmth() {}

     void m1 = {
         <selection><caret>println smth
         doSmth()</selection>
     }
     void m2() {
         m1()
     }
}
''', '''
class Some {
     private static int smth = 1
     private static void doSmth() {}

     void m1 = {<caret> Closure closure ->

         closure()
     }
     void m2() {
         m1 {
             println smth
             doSmth()
         }
     }
}
''')
    }

    void testAppStatement() {
      doTest('''
void foo = {
    def s = <selection><caret>"zxcvbn".substring 2 charAt(1)</selection>
}
foo()
''', '''
void foo = { Closure<Character> closure ->
    def s = <selection><caret>closure()</selection>
}
foo { return "zxcvbn".substring(2).charAt(1) }
''')
    }
  }

}
