// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

public class GrGenericsInferringTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()};
  }

  public void testMapExplicit() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           Map<?,?> map = [:].collectEntries { k, v -> [:] }
                         
                           map.put(1, 2)
                         }
                         """);
  }

  public void testMapImplicit() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           def map = [:].collectEntries { k, v -> [:] }
                           map.put(1, 2)
                         }
                         """);
  }

  public void testMapChainCall() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method() {
                           [:].collectEntries { k, v -> [:] }.put(1, 2)
                         }
                         """);
  }

  public void testListWildcardExtends() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? extends Serializable> captured) {
                           captured.add('some string')
                           return captured
                         }
                         """);
  }

  public void testListWildcardExtendsError() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? extends Serializable> captured) {
                           captured.add<error>(new Object())</error>
                           return captured
                         }
                         """);
  }

  public void testListWildcardExtendsAssign() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method(List<? extends Serializable> captured) {
                           Serializable s = captured.get(0);
                         }
                         """);
  }

  public void testListWildcardSuper() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static def method(List<? super Serializable> captured) {
                           captured.add('some string')
                           return captured
                         }
                         """);
  }

  public void testListWildcardSuperError() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         static void method(List<? super Serializable> captured) {
                           Serializable <error>s</error> = captured.get(0);
                         }
                         """);
  }

  public void testDeclaration6() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String,String> <error>obj</error> = new HashMap<String,Integer>()
                         }
                         """);
  }

  public void testAddOnList() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = []
                             list.add<error>(1)</error>
                         }
                         """);
  }

  public void testAddOnListUsingLeftShift() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = []
                             list <error><<</error> 1
                         }
                         """);
  }

  public void testPutMethodWithWrongValueType() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String, Integer> map = new HashMap<String,Integer>()
                             map.put<error>('hello', new Object())</error>
                         }
                         """);
  }

  public void testPutMethodWithPrimitiveValueAndArrayPut() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             Map<String, Integer> map = new HashMap<String,Integer>()
                             map['hello'] = 1
                         }
                         """);
  }

  public void testAddAllWithCollectionShouldNotBeAllowed() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m(){
                             List<String> list = ['a','b','c']
                             Collection<Integer> e = (Collection<Integer>) [1,2,3]
                             boolean r = list.addAll<error>(e)</error>
                         }
                         """);
  }

  public void testIncompatibleGenericsForTwoArgumentsUsingEmbeddedPlaceholder() {
    doTestHighlighting("""

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

  public void testConstructorArgumentsAgainstGenerics() {
    doTestHighlighting("""
                         class Foo<T>{  Foo(T a, T b){} }
                         
                         @groovy.transform.CompileStatic
                         def bar() {
                             Foo<Map> f = new Foo<Map><error>("a",1)</error>
                         }
                         """);
  }

  public void testMethodWithDefaultArgument() {
    doTestHighlighting("""
                         class A{}
                         class B extends A{}
                         def foo(List<? extends A> arg, String value='default'){1}

                         List<B> b = new ArrayList<>()
                         assert foo(b) == 1
                         List<A> a = new ArrayList<>()
                         assert foo(a) == 1
                         """);

    doTestHighlighting("""
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

  public void testMethodShadowGenerics() {
    doTestHighlighting("""
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

  public void testGenericField() {
    doTestHighlighting("""
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

  public void testReturnTypeChecking() {
    doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         class Foo {
                             List<String> run() {
                                 <error>[11, 12]</error>
                             }
                         }
                         """);
  }

  public void testEnumGenerics() {
    doTestHighlighting("""
                         enum MyEnum { ONE, TWO }

                         @groovy.transform.CompileStatic
                         void compare() {
                             assert MyEnum.ONE < MyEnum.TWO
                         }
                         """);
  }

  public void testInjectGenerics() {
    doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         void printList() {
                           List<String> list = ['1', '2']
                           assert list.inject('0') { old, i -> old + i } == '012'
                         }
                         """);
  }

  public void testBoundedGenericsCompileStatic() {
    doTestHighlighting("""
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

  public void testClosureToSAM() {
    doTestHighlighting("""
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

  public void testClosureToSAMWildcard() {
    doTestHighlighting("""
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

  public void testClosureToSAMGenericWildcard() {
    doTestHighlighting("""
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

  public void testListToArrayCoercion() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         class SomeClass2 {
                             static SomeClass2[] join() {
                                 <error>return</error> []
                             }
                         }
                         """);
  }

  public void testListToArrayCoercionWithHierarchy() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         
                         @CompileStatic
                         class SomeClass2 {
                             static Number[] join() {
                                 <error>return</error> [1]
                             }
                         }
                         """);
  }

  public void testComponentCoercion() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             static List<Object> join() {
                                 <error>return</error> new ArrayList<SomeClass>()

                             }
                         }
                         """);
  }

  public void testArrayListInitializerCoercion() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             def foo() {
                                 String[] m  = ["str"]
                             }
                         }
                         """);
  }

  public void testArrayEmptyListInitializerCoercion() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class SomeClass {
                             def foo() {
                                 String[] m  = []
                             }
                         }
                         """);
  }

  public void testOverloadWithPlaceholders() {
    doTestHighlighting("""
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

  public void testOverloadWithPlaceholders2() {
    doTestHighlighting("""
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

  public void testCollectManyListReturn() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def nums = [1]
                             def res = nums.collectMany { [""] }
                             res[0].toUpperCase()
                         }
                         """);
  }

  public void testCollectManyItReturn() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def nums = [""]
                             def res = nums.collectMany { [it] }
                             res[0].toUpperCase()
                         }
                         """);
  }

  public void testSeveralPossibleClosureSignatures() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         @CompileStatic
                         def bar(Map<String, Integer> map) {
                             map.sort({ it.value })
                         }
                         """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_3;
  }
}
