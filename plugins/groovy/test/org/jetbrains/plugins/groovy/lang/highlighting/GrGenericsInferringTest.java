// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrGenericsInferringTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_3);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()});

  @Test
  public void testMapExplicit() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           Map<?,?> map = [:].collectEntries { k, v -> [:] }
                         
                           map.put(1, 2)
                         }
                         """);
  }

  @Test
  public void testMapImplicit() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           def map = [:].collectEntries { k, v -> [:] }
                           map.put(1, 2)
                         }
                         """);
  }

  @Test
  public void testMapChainCall() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           [:].collectEntries { k, v -> [:] }.put(1, 2)
                         }
                         """);
  }

  @Test
  public void testListWildcardExtends() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? extends Serializable> captured) {
                           captured.add('some string')
                           return captured
                         }
                         """);
  }

  @Test
  public void testListWildcardExtendsError() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? extends Serializable> captured) {
                           captured.add<error>(new Object())</error>
                           return captured
                         }
                         """);
  }

  @Test
  public void testListWildcardExtendsAssign() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method(List<? extends Serializable> captured) {
                           Serializable s = captured.get(0);
                         }
                         """);
  }

  @Test
  public void testListWildcardSuper() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? super Serializable> captured) {
                           captured.add('some string')
                           return captured
                         }
                         """);
  }

  @Test
  public void testListWildcardSuperError() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method(List<? super Serializable> captured) {
                           Serializable <error>s</error> = captured.get(0);
                         }
                         """);
  }

  @Test
  public void testDeclaration6() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String,String> <error>obj</error> = new HashMap<String,Integer>()
                         }
                         """);
  }

  @Test
  public void testAddOnList() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = []
                             list.add<error>(1)</error>
                         }
                         """);
  }

  @Test
  public void testAddOnListUsingLeftShift() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = []
                             list <error><<</error> 1
                         }
                         """);
  }

  @Test
  public void testPutMethodWithWrongValueType() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String, Integer> map = new HashMap<String,Integer>()
                             map.put<error>('hello', new Object())</error>
                         }
                         """);
  }

  @Test
  public void testPutMethodWithPrimitiveValueAndArrayPut() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String, Integer> map = new HashMap<String,Integer>()
                             map['hello'] = 1
                         }
                         """);
  }

  @Test
  public void testAddAllWithCollectionShouldNotBeAllowed() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = ['a','b','c']
                             Collection<Integer> e = (Collection<Integer>) [1,2,3]
                             boolean r = list.addAll<error>(e)</error>
                         }
                         """);
  }

  @Test
  public void testIncompatibleGenericsForTwoArgumentsUsingEmbeddedPlaceholder() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         public <T> void printEqual(T arg1, List<T> arg2) {
                             println arg1 == arg2
                         }
                         @CompileStatic
                         def m() {
                           printEqual<error>(1, ['foo'])</error>
                         }
                         """);
  }

  @Test
  public void testConstructorArgumentsAgainstGenerics() {
    highlighting.get().doTestHighlighting("""
                         class Foo<T>{  Foo(T a, T b){} }
                         
                         @groovy.transform.CompileStatic
                         def bar() {
                             Foo<Map> f = new Foo<Map><error>("a",1)</error>
                         }
                         """);
  }

  @Test
  public void testMethodWithDefaultArgument() {
    highlighting.get().doTestHighlighting("""
                         class A{}
                         class B extends A{}
                         def foo(List<? extends A> arg, String value='default'){1}

                         List<B> b = new ArrayList<>()
                         assert foo(b) == 1
                         List<A> a = new ArrayList<>()
                         assert foo(a) == 1
                         """);

    highlighting.get().doTestHighlighting("""
                         class A{}
                         class B extends A{}
                         def foo(List<? extends A> arg, String value='default'){1}
                         
                         @groovy.transform.CompileStatic
                         def m() {
                           List<Object> l = new ArrayList<>()
                           assert foo<error>(l)</error> == 1
                         }
                         """);
  }

  @Test
  public void testMethodShadowGenerics() {
    highlighting.get().doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         public class GoodCodeRed<T> {
                             Collection<GoodCodeRed<T>> attached = []
                             public <T> void attach(GoodCodeRed<T> toAttach) {
                                 attached.add<error>(toAttach)</error>
                             }
                             static void foo() {
                                 def g1 = new GoodCodeRed<Long>()
                                 def g2 = new GoodCodeRed<Integer>()
                                 g1.attach(g2);
                             }
                         }
                         GoodCodeRed.foo()
                         """);
  }

  @Test
  public void testGenericField() {
    highlighting.get().doTestHighlighting("""
                         class MyClass {
                             static void main(args) {
                                 Holder<Integer> holder = new Holder<Integer>()
                                 holder.value = 5
                                 assert holder.value > 4
                             }
                             private static class Holder<T> {
                                 T value
                             }
                         }
                         """);
  }

  @Test
  public void testReturnTypeChecking() {
    highlighting.get().doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         class Foo {
                             List<String> run() {
                                 <error>[11, 12]</error>
                             }
                         }
                         """);
  }

  @Test
  public void testEnumGenerics() {
    highlighting.get().doTestHighlighting("""
                         enum MyEnum { ONE, TWO }

                         @groovy.transform.CompileStatic
                         void compare() {
                             assert MyEnum.ONE < MyEnum.TWO
                         }
                         """);
  }

  @Test
  public void testInjectGenerics() {
    highlighting.get().doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         void printList() {
                           List<String> list = ['1', '2']
                           assert list.inject('0') { old, i -> old + i } == '012'
                         }
                         """);
  }

  @Test
  public void testBoundedGenericsCompileStatic() {
    highlighting.get().doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         class Foo {
                           static <T extends List<Integer>> void extInteger(T a) {}

                           static <T extends List<? extends CharSequence>> void extCS(T a) {}

                           static <T extends List<Object>> void extObj(T a) {
                             extCS<error descr="'extCS' in 'Foo' cannot be applied to '(T)'">(a)</error>
                           }

                           static void foo() {
                             extObj([new Object()])
                             extInteger<error descr="'extInteger' in 'Foo' cannot be applied to '([java.lang.String])'">([''])</error>
                           }
                         }
                         """);
  }

  @Test
  public void testClosureToSAM() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         interface SAM<In, Out> {
                             Out run(In argument)
                         }

                         @CompileStatic
                         class SomeClass2 {
                             static <T> String join(T item, SAM<T, String> f) {
                                 return ""
                             }

                             static void method() {
                                 join(new SomeClass2(), { it.toString() })
                             }
                         }

                         """);
  }

  @Test
  public void testClosureToSAMWildcard() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         interface SAM<In, Out> {
                             Out run(In argument)
                         }

                         @CompileStatic
                         class SomeClass2 {
                             static <T> String join(T item, SAM<? extends T, String> f) {
                                 return ""
                             }

                             static void method() {
                                 join(new SomeClass2(), { it.toString() })
                             }
                         }
                         """);
  }

  @Test
  public void testClosureToSAMGenericWildcard() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         
                         interface SAM<In, Out> {
                             Out run(In argument)
                         }
                         
                         @CompileStatic
                         class SomeClass2 {
                             static <T> String join(List<? extends T> item, SAM<T, String> f) {
                                 return ""
                             }
                         
                             static void method() {
                                 def list = [new SomeClass2()]
                                 join(list, { it.toString() })
                             }
                         }
                         """);
  }

  @Test
  public void testListToArrayCoercion() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         class SomeClass2 {
                             static SomeClass2[] join() {
                                 <error>return</error> []
                             }
                         }
                         """);
  }

  @Test
  public void testListToArrayCoercionWithHierarchy() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         class SomeClass2 {
                             static Number[] join() {
                                 <error>return</error> [1]
                             }
                         }
                         """);
  }

  @Test
  public void testComponentCoercion() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             static List<Object> join() {
                                 <error>return</error> new ArrayList<SomeClass>()

                             }
                         }
                         """);
  }

  @Test
  public void testArrayListInitializerCoercion() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             def foo() {
                                 String[] m  = ["str"]
                             }
                         }
                         """);
  }

  @Test
  public void testArrayEmptyListInitializerCoercion() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             def foo() {
                                 String[] m  = []
                             }
                         }
                         """);
  }

  @Test
  public void testOverloadWithPlaceholders() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         class User{}

                         public interface Repo<T>{
                             def <S extends T> S save(S entitty)
                             def <S extends T> Iterable<S> save(Iterable<S> entities)
                         }

                         @CompileStatic
                         interface Foo extends Repo<User> {

                         }

                         @CompileStatic
                         static void main(Foo foo) {
                             foo.save(new User())
                         }
                         """);
  }

  @Test
  public void testOverloadWithPlaceholders2() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         class User{}

                         class Bar {
                             static <S extends User> void save(S s){}
                             static <S extends User> void save(List<S> s){}
                         }

                         @CompileStatic
                         static void main() {
                             Bar.save(new User())
                         }
                         """);
  }

  @Test
  public void testCollectManyListReturn() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def nums = [1]
                             def res = nums.collectMany { [""] }
                             res[0].toUpperCase()
                         }
                         """);
  }

  @Test
  public void testCollectManyItReturn() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def nums = [""]
                             def res = nums.collectMany { [it] }
                             res[0].toUpperCase()
                         }
                         """);
  }

  @Test
  public void testSeveralPossibleClosureSignatures() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         @CompileStatic
                         def bar(Map<String, Integer> map) {
                             map.sort({ it.value })
                         }
                         """);
  }
}
