// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceParameter;

import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromClosureProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureFromMethodProcessor;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.IntroduceParameterInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/extractMethod/";
  }

  protected void doTest(String before, String after, IntList toRemove, IntList notToUseAsParams, boolean forceReturn) {
    myFixture.configureByText("______________a____________________.groovy", before);

    GrIntroduceParameterHandler handler = new GrIntroduceParameterHandler() {
      @Override
      protected void showDialog(IntroduceParameterInfo info) {

        GrIntroduceParameterSettings helper =
          new ExtractClosureHelperImpl(info, "closure", false, toRemove, false, 
                                       IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, forceReturn, false, false);
        for (int p : notToUseAsParams) {
          helper.getParameterInfos()[p].passAsParameter = false;
        }

        if (helper.getToReplaceIn() instanceof GrMethod) {
          new ExtractClosureFromMethodProcessor(helper).run();
        }
        else {
          new ExtractClosureFromClosureProcessor(helper).run();
        }
      }
    };
    handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
    doPostponedFormatting(myFixture.getProject());

    myFixture.checkResult(after);
  }

  protected void doTest(String before, String after, IntList toRemove, IntList notToUseAsParams) {
    doTest(before, after, toRemove, notToUseAsParams, true);
  }

  protected void doTest(String before, String after, IntList toRemove) {
    doTest(before, after, toRemove, IntList.of(), true);
  }

  protected void doTest(String before, String after) {
    doTest(before, after, IntList.of(), IntList.of(), true);
  }

  public static class MethodTest extends ExtractClosureTest {
    public void testSimple() {
      doTest("""
               def foo(String s) {
                   s+=2
                   <selection>print s</selection>
               }
               
               foo('a')
               """, """
               def foo(String s, Closure closure) {
                   s+=2
                   closure(s)
               }
               
               foo('a') { String s -> print s }
               """, IntList.of(), IntList.of(), false);
    }

    public void testRemoveUnused() {
      doTest("""
               class X {
                   def foo(String s) {
                       <selection>print s</selection>
                   }
               }
               
               new X().foo('a')
               """, """
               class X {
                   def foo(Closure closure) {
                       closure()
                   }
               }
               
               new X().foo { print 'a' }
               """, IntList.of(0), IntList.of(0), false);
    }

    public void testRemoveUnusedAndGenerateLocal() {
      doTest("""
               def foo(String s) {
                   <selection>s+=2
                   print s</selection>
               }
               
               foo('a')
               """, """
               def foo(Closure closure) {
                   closure()
               }
               
               foo {
                   String s = 'a'
                   s += 2
                   print s
               }
               """, IntList.of(0), IntList.of(0), true);
    }

    public void testInsertQualifier() {
      doTest("""
               class X {
                   def foo(String s) {
                       <selection>bar()</selection>
                   }
                   def bar(){}
               }
               
               new X().foo('a')
               """, """
               class X {
                   def foo(Closure closure) {
                       closure()
                   }
                   def bar(){}
               }
               
               final X x = new X()
               x.foo { x.bar() }
               """, IntList.of(0), IntList.of(), false);
    }

    public void testLocalVarAsParam() {
      doTest("""
               def foo(int x, int y) {
                   int a = 5
                   <selection>print 45+y+a</selection>
               }
               
               foo(2, 3)
               """, """
               def foo(Closure closure) {
                   int a = 5
                   closure(a)
               }
               
               foo { int a -> print 45 + 3 + a }
               """, IntList.of(0, 1), IntList.of(0), false);
    }

    public void testRPG() {
      doTest("""
               adventure()
               
               def adventure() {
               
                   try {
                       <selection><caret>killMonsters()
                       collectLoot()</selection>
                   } catch (ArrowToKneeException) {
                       becomeTownGuard()
                   }
               }
               """, """
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
               """);
    }

    public void testExpression() {
      doTest("""
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
               """, """
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
               """);
    }

    public void testDontQualify() {
      doTest("""
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
               """, """
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
               """);
    }

    public void testAppStatement() {
      doTest("""
               void foo() {
                   def s = <selection><caret>"zxcvbn".substring 2 charAt(1)</selection>
               }
               foo()
               """, """
               void foo(Closure<Character> closure) {
                   def s = closure()<caret>
               }
               foo { return "zxcvbn".substring(2).charAt(1) }
               """);
    }

    public void testStringPart0() {
      doTest("""
               def cl() {
                   print 'a<selection>b</selection>c'
               }
               
               cl()
               """, """
               def cl(Closure<String> closure) {
                   print 'a' + closure()<caret> + 'c'
               }
               
               cl { return 'b' }
               """);
    }

    public void testNull() {
      doTest("""
               def foo() {
                   print <selection>null</selection>
               }
               
               foo()
               """, """
               def foo(Closure closure) {
                   print closure()
               }
               
               foo { return null }
               """);
    }
  }

  public static class ClosureTest extends ExtractClosureTest {
    public void testSimple() {
      doTest("""
               def foo = {String s ->
                   s+=2
                   <selection>print s</selection>
               }
               
               foo('a')
               """, """
               def foo = { String s, Closure closure ->
                   s+=2
                   <selection>closure(s)</selection>
               }
               
               foo('a') { String s -> print s }
               """, IntList.of(), IntList.of(), false);
    }

    public void testRemoveUnused() {
      doTest("""
               class X {
                   def foo = {String s ->
                       <selection>print s</selection>
                   }
               }
               
               new X().foo('a')
               """, """
               class X {
                   def foo = { Closure closure ->
                       <selection>closure()</selection>
                   }
               }
               
               final X x = new X()
               x.foo { x.print 'a' }
               """, IntList.of(0), IntList.of(0), false);
    }

    public void testRemoveUnusedAndGenerateLocal() {
      doTest("""
               def foo = {String s ->
                   <selection>s+=2
                   print s</selection>
               }
               
               foo('a')
               """, """
               def foo = { Closure closure ->
                   closure()
               }
               
               foo {
                   String s = 'a'
                   s += 2
                   print s
               }
               """, IntList.of(0), IntList.of(0), true);
    }

    public void testInsertQualifier() {
      doTest("""
               class X {
                   def foo = {String s ->
                       <selection>bar()</selection>
                   }
                   def bar(){}
               }
               
               new X().foo('a')
               """, """
               class X {
                   def foo = { Closure closure ->
                       <selection>closure()</selection>
                   }
                   def bar(){}
               }
               
               final X x = new X()
               x.foo { x.bar() }
               """, IntList.of(0), IntList.of(), false);
    }

    public void testLocalVarAsParam() {
      doTest("""
               def foo = {int x, int y ->
                   int a = 5
                   <selection>print 45+y+a</selection>
               }
               
               foo(2, 3)
               """, """
               def foo = { Closure closure ->
                   int a = 5
                   <selection>closure(a)</selection>
               }
               
               foo { int a -> print 45 + 3 + a }
               """, IntList.of(0, 1), IntList.of(0), false);
    }

    public void testRPG() {
      doTest("""
               def adventure = {
               
                   try {
                       <selection><caret>killMonsters()
                       collectLoot()</selection>
                   } catch (ArrowToKneeException) {
                       becomeTownGuard()
                   }
               }
               
               adventure()
               """, """
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
               """);
    }

    public void testExpression() {
      doTest("""
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
               """, """
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
               """);
    }

    public void testDontQualify() {
      doTest("""
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
               """, """
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
               """);
    }

    public void testAppStatement() {
      doTest("""
               void foo = {
                   def s = <selection><caret>"zxcvbn".substring 2 charAt(1)</selection>
               }
               foo()
               """, """
               void foo = { Closure<Character> closure ->
                   def s = <selection><caret>closure()</selection>
               }
               foo { return "zxcvbn".substring(2).charAt(1) }
               """);
    }
  }
}
