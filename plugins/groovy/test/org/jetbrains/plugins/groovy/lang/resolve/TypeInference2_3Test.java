// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

/**
 * Created by Max Medvedev on 10/02/14
 */
@TestApplication
public class TypeInference2_3Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_3);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

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
                     wrap {
                         1
                     } contravariantTake {
                         dub(i<caret>t) // fails static compile, 'it' is not known to be Integer
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
                 customType { [] }.ad<caret>d(1) // return type is not inferred - fails compile
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
                 first { [[]] }.ad<caret>d(1) // return type is not inferred - fails compile
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
                     customType { [(1):3] }.pu<caret>t(1, 5) // return type is not inferred - fails compile
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
                 exec('foo') {print i<caret>t.toUpperCase() ;print 2 }
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
                 exec('foo') {i<caret>t.toUpperCase() }
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
                 exec('foo') {print i<caret>t.toUpperCase() }
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
                     customType { i<caret>t }
                 }
             }""", null);
  }

  @Test
  public void testClosureParamsUsingGenerics() {
    inference.get().doTest("""
                 import groovy.transform.CompileStatic
             
                 @CompileStatic
                 class Idea {
                   public static void main(String[] args) {
                     ["bc", "a", ].sort { i<caret>t.size() }
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartTypeOnDef() {
    inference.get().doTest("""
                 class Idea {
                   public static void main(String[] args) {
                    def aa = new Object()
                    aa = "as"
                    a<caret>a
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartType() {
    inference.get().doTest("""
                 class Idea {
                   public static void main(String[] args) {
                    Object aa = new Object()
                    aa = "as"
                    a<caret>a
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartTypeIf() {
    inference.get().doTest("""
                 import groovy.transform.CompileStatic
             
                 @CompileStatic
                 class Idea {
                   public static void main(String[] args) {
                    Object aa = new Object()
                    if (aa instanceof String) {
                     a<caret>a
                    }
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartTypeIfOnDef() {
    inference.get().doTest("""
                 class Idea {
                   public static void main(String[] args) {
                    def aa = new Object()
                    if (aa instanceof String) {
                     a<caret>a
                    }
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartTypeAssert() {
    inference.get().doTest("""
                 class Idea {
                   public static void main(String[] args) {
                    Object aa = new Object()
                    assert aa instanceof String
                    a<caret>a.hashCode()
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testSmartTypeAssertOnDef() {
    inference.get().doTest("""
                 class Idea {
                   public static void main(String[] args) {
                    def aa = new Object()
                    assert aa instanceof String
                    a<caret>a.hashCode()
                   }
                 }
             """, "java.lang.String");
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgument() {
    inference.get().doTest("""
             
             interface SAM<O> {
                 void accept(O out)
             }
             
             def <R> R samMethod(SAM<R> mapper) {
             }
             
             s<caret>amMethod({Integer i->})
             
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentNestedGeneric() {
    inference.get().doTest("""
             
             interface SAM<O> {
                 void accept(Collection<List<O>> out)
             }
             
             def <R> R samMethod(SAM<R> mapper) {
             }
             
             s<caret>amMethod({Collection<List<Integer>> i->})
             
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentSeveralTypeParams() {
    inference.get().doTest("""
             
             public interface SAM<T, O> {
                 void flatMap(T value, Collection<O> out) throws Exception;
             }
             
             class C<T> {
                 public <R> R flatMap(SAM<T, R> f) {
                     return null
                 }
             }
             
             new C<String>().flat<caret>Map {
                 String s, Collection<Integer> c ->
             }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentWithReturnType() {
    inference.get().doTest("""
             
             public interface SAM<T, O> {
                 void flatMap(T value, Collection<O> out) throws Exception;
             }
             
             class C<T> {
                 public <R> R flatMap(SAM<T, R> f) {
                     return null
                 }
             }
             
             new C<String>().flat<caret>Map {
                 String s, Collection<Integer> c -> new String[10]
             }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentWithLambda() {
    inference.get().doTest("""
             
             public interface SAM<T, O> {
                 void flatMap(T value, Collection<O> out) throws Exception;
             }
             
             class C<T> {
                 public <R> R flatMap(SAM<T, R> f) {
                     return null
                 }
             }
             
             new C<String>().flat<caret>Map((String s, Collection<Integer> c) -> new String[10])
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentWithDefaultValues() {
    inference.get().doTest("""
             
             public interface SAM<T, O> {
                 void flatMap(T value, Collection<O> out) throws Exception;
             }
             
             class C<T> {
                 public <R> R flatMap(SAM<T, R> f) {
                     return null
                 }
             }
             
             new C<String>().flat<caret>Map {
                 String s, Collection<Integer> c, Double d = 1.0 -> new String[10]
             }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testInferenceFromExplicitTypedSamArgumentWithSamInheritance() {
    inference.get().doTest("""
             
             public interface SAM<T, O> {
                 void flatMap(T value, Collection<O> out) throws Exception;
             }
             
             public interface Inheritor<U> extends SAM<String, U> {
             }
             
             class C {
                 public <R> R flatMap(Inheritor<R> f) {
                     return null
                 }
             }
             
             new C().flat<caret>Map {
                 String s, Collection<Integer> c -> new String[10]
             }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testTypeOfMethodReturningNullInCompileStatic() {
    inference.get().doTest("""
             @groovy.transform.CompileStatic
             class B {
                 void m() { <caret>method() }
                 private List method() { return null }
             }
             """, GrMethodCall.class, "java.util.List");
  }
}
