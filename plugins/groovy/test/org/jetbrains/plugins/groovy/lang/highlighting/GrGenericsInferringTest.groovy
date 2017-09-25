/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

class GrGenericsInferringTest extends GrHighlightingTestBase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_3
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }


  void testMapExplicit() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static void method() {
        Map<?,?> map = [:].collectEntries { k, v -> [:] }
  
        map.put(1, 2)
      }
    '''
  }

  void testMapImplicit() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static void method() {
        def map = [:].collectEntries { k, v -> [:] }
        map.put(1, 2)
      }
    '''
  }

  void testMapChainCall() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static void method() {
        [:].collectEntries { k, v -> [:] }.put(1, 2)
      }
    '''
  }

  void testListWildcardExtends() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static def method(List<? extends Serializable> captured) {
        captured.add('some string')
        return captured
      }
    '''
  }

  void testListWildcardExtendsError() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static def method(List<? extends Serializable> captured) {
        captured.add<error>(new Object())</error>
        return captured
      }
    '''
  }

  void testListWildcardExtendsAssign() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static void method(List<? extends Serializable> captured) {
        Serializable s = captured.get(0);
      }
    '''
  }

  void testListWildcardSuper() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static def method(List<? super Serializable> captured) {
        captured.add('some string\')
        return captured
      }
    '''
  }

  void testListWildcardSuperError() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      static void method(List<? super Serializable> captured) {
        Serializable <error>s</error> = captured.get(0);
      }
    '''
  }

  void testDeclaration6() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      def m(){
          Map<String,String> <error>obj</error> = new HashMap<String,Integer>()
      }
      '''
  }

  void testAddOnList() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      def m(){
          List<String> list = []
          list.add<error>(1)</error>
      }
      '''
  }

  void testAddOnListUsingLeftShift() {
    testHighlighting '''
      import groovy.transform.CompileStatic

     @CompileStatic
      def m(){
          List<String> list = []
          list <error><<</error> 1
      }
      '''
  }

  void testPutMethodWithWrongValueType() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      def m(){
          Map<String, Integer> map = new HashMap<String,Integer>()
          map.put<error>('hello', new Object())</error>
      }
      '''
  }

  void testPutMethodWithPrimitiveValueAndArrayPut() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      def m(){
          Map<String, Integer> map = new HashMap<String,Integer>()
          map['hello'] = 1
      }
      '''
  }


  void testAddAllWithCollectionShouldNotBeAllowed() {
    testHighlighting '''
      import groovy.transform.CompileStatic

      @CompileStatic
      def m(){
          List<String> list = ['a','b','c']
          Collection<Integer> e = (Collection<Integer>) [1,2,3]
          boolean r = list.addAll<error>(e)</error>
      }        
      '''
  }

  void testIncompatibleGenericsForTwoArgumentsUsingEmbeddedPlaceholder() {
    testHighlighting '''
          import groovy.transform.CompileStatic 
          
          @CompileStatic
          public <T> void printEqual(T arg1, List<T> arg2) {
              println arg1 == arg2
          }
          @CompileStatic
          def m() {
            printEqual<error>(1, ['foo'])</error>
          }
      '''
  }

  void testConstructorArgumentsAgainstGenerics() {
    testHighlighting '''
        class Foo<T>{  Foo(T a, T b){} }
        
        @groovy.transform.CompileStatic
        def bar() {
            Foo<Map> f = new Foo<Map><error>("a",1)</error>
        }
          
      '''
  }

  void testMethodWithDefaultArgument() {
    testHighlighting '''
          class A{}
          class B extends A{}
          def foo(List<? extends A> arg, String value='default'){1}

          List<B> b = new ArrayList<>()
          assert foo(b) == 1
          List<A> a = new ArrayList<>()
          assert foo(a) == 1
      '''

    testHighlighting '''
          class A{}
          class B extends A{}
          def foo(List<? extends A> arg, String value='default'){1}
          
          @groovy.transform.CompileStatic
          def m() {
            List<Object> l = new ArrayList<>()
            assert foo<error>(l)</error> == 1
          }
      '''
  }

  void testMethodShadowGenerics() {
    testHighlighting '''
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
      '''
  }

  void testGenericField() {
    testHighlighting '''
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
    '''
  }

  void testReturnTypeChecking() {
    testHighlighting '''
      @groovy.transform.CompileStatic
      class Foo {
          List<String> run() {
              <error>[11, 12]</error>
          }
      }
  '''
  }

  void testEnumGenerics() {
    testHighlighting '''\
enum MyEnum { ONE, TWO }

@groovy.transform.CompileStatic
void compare() {
    assert MyEnum.ONE < MyEnum.TWO
}
'''
  }

  void testInjectGenerics() {
    testHighlighting '''\
@groovy.transform.CompileStatic
void printList() {
  List<String> list = ['1', '2']
  assert list.inject('0') { old, i -> old + i } == '012'
}
'''
  }

  void testBoundedGenericsCompileStatic() {
    testHighlighting '''\
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
'''
  }

  void testClosureToSAM() {
    testHighlighting '''\
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

'''
  }

  void testClosureToSAMWildcard() {
    testHighlighting '''\
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
'''
  }

  void testClosureToSAMGenericWildcard() {
    testHighlighting '''\
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
  '''
  }

  void testListToArrayCoercion() {
    testHighlighting '''\
  import groovy.transform.CompileStatic
  
  @CompileStatic
  class SomeClass2 {
      static SomeClass2[] join() {
          <error>return</error> []
      }
  }
  '''
  }

  void testListToArrayCoercionWithHierarchy() {
    testHighlighting '''\
  import groovy.transform.CompileStatic
  
  @CompileStatic
  class SomeClass2 {
      static Number[] join() {
          <error>return</error> [1]
      }
  }
  '''
  }

  void testComponentCoercion() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class SomeClass {
    static List<Object> join() {
        <error>return</error> new ArrayList<SomeClass>()

    }
}
  '''
  }

  void testArrayListInitializerCoercion() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class SomeClass {
    def foo() {
        String[] m  = ["str"]
    }
}
  '''
  }

  void testArrayEmptyListInitializerCoercion() {
    testHighlighting ''' 
import groovy.transform.CompileStatic

@CompileStatic
class SomeClass {
    def foo() {
        String[] m  = []
    }
}
  '''
  }

  void testCollectMany() {
    testHighlighting ''' 
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def nums = [1]
    def res = nums.collectMany { [it] }
}
  '''
  }
}