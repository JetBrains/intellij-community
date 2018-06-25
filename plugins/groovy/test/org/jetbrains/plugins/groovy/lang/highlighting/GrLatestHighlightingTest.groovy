// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

class GrLatestHighlightingTest extends GrHighlightingTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_LATEST_REAL_JDK
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection(), new GroovyAccessibilityInspection(),
     new MissingReturnInspection()]
  }

  void 'test IDEA-184690'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    BigDecimal[] c = [2, 3]
    c == [2,3] as BigDecimal[] 
}
'''
  }

  void 'test IDEA-184690-2'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Object() == 1
}
'''
  }

  void 'test IDEA-184690-3'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Thread[1] == new Object[1]
}
'''
  }

  void 'test IDEA-185371'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    Map<String, Integer> correct = [:].withDefault({ 0 })
}
'''
  }

  void 'test IDEA-185371-2'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

static <K,V> Map<K, V> getMap() {
  return new HashMap<K,V>()
}

@CompileStatic
def com() {
    Map<String, Integer> correct = getMap().withDefault({ 0 })
}
'''
  }

  void '_test IDEA-185371-3'() {
    testHighlighting '''\
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <K,V> Map<K, V> getMap() {
  new HashMap<K, V>()
}

static <K, V> Map<K, V> withDefault(Map<K, V> self, @ClosureParams(FirstParam.FirstGenericType.class) Closure<V> init) {
  return null
}

@CompileStatic
def m() {
  withDefault(getMap()) { 'str '}.get(1).with {
    print toUpperCase()
  }
}
'''
  }

  void 'test IDEA-185371-4'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    ''.with {
        print toUpperCase()
    }
}
'''
  }

  void 'test IDEA-185758-2'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

interface A {}

class C implements A {}

class Container<T> {
    public <U extends T> void register(Class<U> clazz, Closure<Integer> closure) {}
}

@CompileStatic
def method(Container<A> box) {
   box.register<error descr="'register' in 'Container<A>' cannot be applied to '(java.lang.Class<C>, groovy.lang.Closure<C>)'">(C)</error> { param -> new C() }
}
'''
  }

  void 'test IDEA-185758'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

interface A {}

class B implements A {}

class Box<T> {
   public  <U extends T> void register(Class<U> clazz, Closure<? extends U> closure) {}
}

@CompileStatic
def method(Box<A> box) {
    box.register(B) { param -> new B() }
}
'''
  }

  void testPerformanceLike() {
    testHighlighting '''
def <T> void foo(T t, Closure cl) {}

foo(1) { println it }
'''
  }

  void testPerformanceLikeCS() {
    testHighlighting '''
import groovy.transform.CompileStatic

def <T> void foo(T t, Closure<T> cl) {}

@CompileStatic
def m() {
  foo(1) { 
    println it 
    1
  }
}
'''
  }

  void testPerformanceLikeCS2() {
    myFixture.enableInspections(new MissingReturnInspection())

    testHighlighting '''
import groovy.transform.CompileStatic

def <T> void foo(T t, Closure<T> cl) {}

@CompileStatic
def m() {
 foo(1) { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() }
}

'''
  }


  void testPerformanceLikeCS3() {

    testHighlighting '''
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <T> void foo(T t, @ClosureParams(value = FirstParam) Closure<T> cl) {}

@CompileStatic
def m() {
  foo('') { it.toUpperCase() }
}
'''
  }

  void 'test IDEA-171738'() {
    testHighlighting '''
import groovy.transform.CompileStatic
import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
  Map<String, Thread> works = existingPairs.stream().collect(Collectors.toMap({ kv -> kv.toString().trim() }, { kv -> kv }))
}

    '''
  }

  void 'test IDEA-171738-2'() {
    testHighlighting '''
import groovy.transform.CompileStatic

import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Integer key = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv })).keySet().getAt(1)
}
    '''
  }

  void 'test IDEA-171738-2_5'() {
    testHighlighting '''
import java.util.stream.Collectors

@groovy.transform.CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Map<Integer, Thread> value = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv }))
}
'''
  }

  void '_test IDEA-171738-3'() {
    testHighlighting '''
@groovy.transform.CompileStatic
public class G<T> {

    T t;
    G<T> add(T t) {
        this.t = t
        return this
    }

    T get(){
        return t
    }

    static <R> G<R> add(G<R> b, R t) {
        b.add(t)
        return b;
    }



    private static <K> G<K> getG() {
        return new G<K>()
    }

    static void m() {
        println getG().add("Str").get().toUpperCase()
    }
}
    '''
  }

  void '_test IDEA-171738-4'() {
    testHighlighting '''
@groovy.transform.CompileStatic
public class G<T> {
    T t;
    G<T> add(T t) {
        this.t = t
        return this
    }

    private static <K> G<K> getG() {
        return new G<K>()
    }

    static void m() {
       G<String> g =  getG().add("Str")
    }
}


    '''
  }

  void 'test IDEA-189792'() {
    testHighlighting '''
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

@CompileStatic
static Stream<String> topicStream(Path path) {
    Files.list(path).map { it.toFile().name }
}
'''
  }

  void 'test IDEA-189274'() {
    testHighlighting '''
import groovy.transform.CompileStatic

import java.time.LocalDateTime

@CompileStatic
class JustAModel {
    LocalDateTime timeToEscalate
}

@CompileStatic
class JustAClass {

    List<JustAModel> events = []

    LocalDateTime getTimeToEscalate() {
        events.reverse().findResult(LocalDateTime.MAX) { it.timeToEscalate }
    }
}
'''
  }

  void 'test IDEA-188105'() {
    testHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
static <T> T apply(T self, @DelegatesTo(type = "T") Closure<Void> block) {
    block.delegate = self
    block()
    self
}

@CompileStatic
void usage() {
    apply("hello world") {
        println toUpperCase() 
    }
}
'''
  }

  void 'test IDEA-191019'() {
    testHighlighting '''
import groovy.transform.CompileStatic

import java.util.stream.Collectors

@CompileStatic
class GoodCodeRed {
    static Set<Integer> intset = new HashSet<>(Arrays.asList(1, 2, 3, 4))

    static Integer testCode() {
        return intset.stream().collect(Collectors.toList()).get(0)
    }
}
'''
  }

  void 'test recursive generics'() {
    testHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
class C {
  public abstract class B<ParentType, T extends ParentType, Self extends B<ParentType, T, Self>>{
    public Self withParent(final Class<? extends ParentType> type) {
      return null;
    }
  }

  interface I<IP>{}
  public class A<T extends Number> extends  B<Number,T,A<T>> implements I<T>  {

  }

  public static <E> E or3(final I<? extends E>... patterns) { return null}

  def m3() {
    or3(new A<Double>().withParent(Integer)).byteValue()
  }

}
'''
  }

  void 'test constructor with generic parameter'() {
    testHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
class Cl {
    
    Cl(Condition<Cl> con) {

    }
    
    interface Condition<T> {}

    static <T> Condition<T> alwaysFalse() {
      return (Condition<T>)null
    }

    
    static m() {
      new Cl(alwaysFalse())
    }

  }
'''
  }

  void 'test call without reference'() {
    testHighlighting '''
class E {
    E call() {
        null
    }
    E bar() {null}

}

new E().bar()()

'''
  }

//TODO: IDEA-194192
  void '_test call without reference with generics'() {
    testHighlighting '''
import groovy.transform.CompileStatic

class E {
    def <K,V> Map<K, V> call(Map<K, V> m) {
        m
    }
    E bar() {null}
}

static <K,V> Map<K, V> getMap() {
  return new HashMap<K,V>()
}

@CompileStatic
def com() {
    Map<String, Integer> correct = new E()(getMap().withDefault({ 0 }))
}
'''
  }
}
