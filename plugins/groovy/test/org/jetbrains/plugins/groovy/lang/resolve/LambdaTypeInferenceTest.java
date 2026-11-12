// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

@TestApplication
public class LambdaTypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @Test
  public void testPlusEquals() {
    inference.get().doTest("""
             class Test {
                 def plus = (a) -> "a"
             }
             def test = new Test()
             test += 2
             te<caret>st
             """, "java.lang.String");
  }

  @Test
  public void testGetAtLambda() {
    inference.get().doTest("""
             class Test {
                 def getAt = a -> "a"
             }
             def test = new Test()
             def test2 = test[2]
             
             print t<caret>est2
             """, "java.lang.String");
  }

  @Test
  public void test_binding_from_inside() {
    inference.get().doTest("list = ['a', 'b']; list.each (it) -> <caret>it", "java.lang.String");
  }

  @Test
  public void testMethodCallInvokedOnArrayAccess() {
    inference.get().doTest("""
             def foo(String s) {
               return this
             }
             
             def bar(String s) {
               return 2
             }
             
             def foo = [(a)->{this}]
             
             def var = foo[0] "a" bar "a"
             print  va<caret>r
             """, "java.lang.Integer");
  }

  @Test
  public void testInferWithClosureType() {
    inference.get().doTest("""
             class C {
               Date field
             }
             
             enum E {
               val((C c) -> {
                 def data = c.with( (it) -> it.field )
                 print d<caret>ata
               })
             }
             """, "java.util.Date");
  }

  public void _test_return_type_1() {
    inference.get().doTest("""
             class A {
               static def fact = (int i) -> {
                 if (i > 1) {
                   return call(i - 1)
                 } else {
                   return 1
                 }
               }
             
               public static void main(String[] args) {
                 def var = A.fact(5)
                 println(<caret>var)
               }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void test_return_type_2() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    inference.get().doTest("""
             class A {
               static def fact = (int i) -> {
                 if (i > 1) {
                   return A.fact(i - 1)
                 } else {
                   return 1
                 }
               }
             
               public static void main(String[] args) {
                 def var = A.fact(5)
                 println(<caret>var)
               }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void test_return_type_3() {
    inference.get().doTest("""
             class A {
               static def fact = int i -> {
                 if (i > 1) {
                   return A.fact(i - 1)
                 } else {
                   return 1
                 }
               }
             
               public static void main(String[] args) {
                     Closure cl = A.fact
                     int var = cl(5)
                     println(<caret>var.intValue())
                 }
             }
             """, "int");
  }

  @Test
  public void testWildcardClosureParam() {
    inference.get().doTest("""
             class Tx {
                 def methodOfT() {}
             }
             
             def method(List<? extends Tx> t) {
                 t.collect (it) -> { print i<caret>t }
             }
             """, "? extends Tx");
  }

  @Test
  public void testTypeOfGroupBy() {
    inference.get().doTest("""
             [1, 2, 3].with {
               def by = groupBy((i)->{2})
               print b<caret>y
             }
             """, "java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>");
  }

  @Test
  public void testTypeOfCall() {
    inference.get().doTest("""
               def by = () -> {2}
               def res = by()
               re<caret>s
             """, "java.lang.Integer");
  }

  @Test
  public void testContravariantType() {
    inference.get().doTest("""
             import groovy.transform.CompileStatic
             import java.util.concurrent.Callable
             
             @CompileStatic
             class TestCase {
             
                 interface Action<T> {
                     void execute(T thing)
                 }
             
                 static class Wrapper<T> {
             
                     private final T thing
             
                     Wrapper(T thing) {
                         this.thing = thing
                     }
             
                     void contravariantTake(Action<? super T> action) {
                         action.execute(thing)
                     }
             
                 }
             
                 static <T> Wrapper<T> wrap(Callable<T> callable) {
                     new Wrapper(callable.call())
                 }
             
                 static Integer dub(Integer integer) {
                     integer * 2
                 }
             
                 static void main(String[] args) {
                     wrap(()-> 1).contravariantTake((it) -> dub(i<caret>t))
                     }
                 }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void testSAMInference() {
    inference.get().doTest("""
             import groovy.transform.CompileStatic
             
             interface CustomCallable<T> {
               T call()
             }
             
             class Thing {
               static <T> T customType(CustomCallable<T> callable) {
                 callable.call()
               }
             
               @CompileStatic
               static void run() {
                 customType( () -> [] ).ad<caret>d(1)
               }
             }
             """, "boolean");
  }

  @Test
  public void testSAMInference2() {
    inference.get().doTest("""
             import groovy.transform.CompileStatic
             
             interface CustomCallable<T> {
               List<T> call()
             }
             
             class Thing {
               static <T> T first(CustomCallable<T> callable) {
                 callable.call().iterator().next()
               }
             
               @CompileStatic
               static void run() {
                 first(() -> [[]] ).ad<caret>d(1)
               }
             }
             """, "boolean");
  }

  @Test
  public void testSAMInference3() {
    inference.get().doTest("""
             import groovy.transform.CompileStatic
             
             interface CustomCallable<K, V> {
                 Map<K, V> call()
             }
             
             class Thing {
                 static <K, V> Map<K, V> customType(CustomCallable<K, V> callable) {
                     callable.call()
                 }
             
                 @CompileStatic
                 static void run() {
                     customType(() -> [(1):3] ).pu<caret>t(1, 5)
                 }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void testSamInference4() {
    inference.get().doTest("""
             interface Action<T> {
                 void execute(T t)
             }
             
             public <T> void exec(T t, Action<T> f) {
             }
             
             
             def foo() {
                 exec('foo', it -> {print i<caret>t.toUpperCase() ;print 2 })
             }
             """, "java.lang.String");
  }

  @Test
  public void testSamInference5() {
    inference.get().doTest("""
             interface Action<T> {
                 void execute(T t)
             }
             
             public <T> void exec(T t, Action<T> f) {
             }
             
             
             def foo() {
                 exec('foo', it -> i<caret>t.toUpperCase())
             }
             """, "java.lang.String");
  }

  @Test
  public void testSamInference6() {
    inference.get().doTest("""
             interface Action<T> {
                 void execute(T t)
             }
             
             public <T> void exec(T t, Action<T> f) {
             }
             
             
             def foo() {
                 exec('foo', it -> {print i<caret>t.toUpperCase() })
             }
             """, "java.lang.String");
  }

  @Test
  public void testSamInference7() {
    inference.get().doTest("""
             interface CustomCallable<T> {
                 T call()
             }
             
             class Thing {
                 static <T> T customType(CustomCallable<T> callable) {
                 }
             
                 static void run() {
                     customType (it) -> i<caret>t
                 }
             }
             """, null);
  }

  @Test
  public void testClosureParamsUsingGenerics() {
    inference.get().doTest("""
             import groovy.transform.CompileStatic
             
             @CompileStatic
             class Idea {
               public static void main(String[] args) {
                 ["bc", "a", ].sort (it) -> i<caret>t.size()
               }
             }
             """, "java.lang.String");
  }

  public void _test_owner_type() {
    inference.get().doTest("""
             class W {
               def c = ()->{
                 <caret>owner
               }
             }
             """, "W");
  }

  @Test
  public void test_use_parent_DFA() {
    inference.get().doTest("""
             def foo(a) {
               a = 1
               1.with(x -> {
                 <caret>a
               })
             }
             """, "java.lang.Integer");
  }

  @Test
  public void test_use_outer_types_inside_unknown_lambdas() {
    inference.get().doTest("""
             def foo() {
               def a = 1
               lambda = x -> {
                 <caret>a
               }
               a = ""
             }
             """, "java.lang.Integer");
  }
}
