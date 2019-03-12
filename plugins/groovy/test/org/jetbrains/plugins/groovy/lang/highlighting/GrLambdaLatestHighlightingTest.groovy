// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.RecursionManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

class GrLambdaLatestHighlightingTest extends GrHighlightingTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0_REAL_JDK
  }

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection(), new GroovyAccessibilityInspection(),
     new MissingReturnInspection()]
  }

  @Override
  void setUp() throws Exception {
    super.setUp()
    RecursionManager.assertOnRecursionPrevention(myFixture.testRootDisposable)
  }


  void 'test IDEA-185371'() {
    testHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    Map<String, Integer> correct = [:].withDefault((it)->{ 0 })
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
    Map<String, Integer> correct = getMap().withDefault((it)->{ 0 })
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
  withDefault(getMap(), (it)->{ 'str '}).get(1).with {
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
    ''.with (a) -> {
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
   box.register<error descr="'register' in 'Container<A>' cannot be applied to '(java.lang.Class<C>, groovy.lang.Closure<C>)'">(C, param -> { new C() })</error>
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
    box.register(B, param ->  { new B() })
}
'''
  }

  void testOverloadedInClosure() {
    testHighlighting '''
def <T> void foo(T t, Closure cl) {}

foo(1, (it) -> { println <weak_warning descr="Cannot infer argument types">it</weak_warning> })
'''
  }

  void testOverloadedInClosureCS() {
    testHighlighting '''
import groovy.transform.CompileStatic

def <T> void foo(T t, Closure<T> cl) {}

@CompileStatic
def m() {
  foo(1, (it)-> { 
    println it 
    1
  })
}
'''
  }

  void testOverloadedInClosureCS2() {
    myFixture.enableInspections(new MissingReturnInspection())

    testHighlighting '''
import groovy.transform.CompileStatic

def <T> void foo(T t, Closure<T> cl) {}

@CompileStatic
def m() {
 foo(1, it -> { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() })
}

'''
  }


  void testOverloadedInClosureCS3() {
    testHighlighting '''
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <T> void foo(T t, @ClosureParams(value = FirstParam) Closure<T> cl) {}

@CompileStatic
def m() {
  foo('',  it -> { it.toUpperCase() })
}
'''
  }

  void 'test IDEA-171738'() {
    testHighlighting '''
import groovy.transform.CompileStatic
import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
  Map<String, Thread> works = existingPairs.stream().collect(Collectors.toMap(kv -> { kv.toString().trim() }, kv -> {  kv }))
}

    '''
  }

  void 'test IDEA-171738-2'() {
    testHighlighting '''
import groovy.transform.CompileStatic

import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Integer key = existingPairs.stream().collect(Collectors.toMap(kv -> { 1 }, kv -> {kv })).keySet().getAt(1)
}
    '''
  }

  void 'test IDEA-171738-2_5'() {
    testHighlighting '''
import java.util.stream.Collectors

@groovy.transform.CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Map<Integer, Thread> value = existingPairs.stream().collect(Collectors.toMap(kv -> { 1 }, kv -> { kv }))
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
    Files.list(path).map(it-> { it.toFile().name })
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
        events.reverse().findResult(LocalDateTime.MAX, it -> { it.timeToEscalate })
    }
}
'''
  }

  void '_test IDEA-188105'() {
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
    apply("hello world", {
        println toUpperCase() 
    })
}
'''
  }


  void 'test with closeable IDEA-197035'() {
    testHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    def stream = new FileInputStream("df")
    def c = stream.with(file -> { 
        new BufferedInputStream(file)
    }).withCloseable((it) -> {
        int a = 0
        new BufferedInputStream(it)
    })
}
'''
  }

  void 'test IDEA-198057-1'() {
    testHighlighting '''
Optional<BigDecimal> foo(Optional<String> string) {
    string.flatMap (it) -> {
        try {
            return Optional.of(new BigDecimal(it))
        } catch (Exception ignored) {
            return Optional.<BigDecimal> empty()
        }
    }
}
'''
  }

  void 'test IDEA-198057-2'() {
    testHighlighting '''
Optional<BigDecimal> foo(Optional<String> string) {
  string.flatMap (it) -> {
     return Optional.<BigDecimal> empty()    
  }
}
'''
  }

  void 'test call without reference with generics'() {
    testHighlighting '''\
class E {
    def <K,V> Map<K, V> call(Map<K, V> m) { m }
}

static <K,V> Map<K, V> getMap() { null }

@groovy.transform.CompileStatic
def usage() {
    Map<String, Integer> correct = new E()(getMap().withDefault(it->{ 0 }))
}
'''
  }
}
