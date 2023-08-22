// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.RecursionManager
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK

class GrLatestHighlightingTest extends GrHighlightingTestBase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_LATEST_REAL_JDK
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

  void 'test IDEA-184690'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    BigDecimal[] c = [2, 3]
    c == [2,3] as BigDecimal[] 
}
'''
  }

  void 'test IDEA-184690-2'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Object() == 1
}
'''
  }

  void 'test IDEA-184690-3'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    new Thread[1] == new Object[1]
}
'''
  }

  void 'test IDEA-185371'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def com() {
    Map<String, Integer> correct = [:].withDefault({ 0 })
}
'''
  }

  void 'test IDEA-185371-2'() {
    doTestHighlighting '''\
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
    doTestHighlighting '''\
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
    doTestHighlighting '''\
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
    doTestHighlighting '''\
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
    doTestHighlighting '''\
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

  void testOverloadedInClosure() {
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.testRootDisposable)
    doTestHighlighting '''
def <T> void foo(T t, Closure cl) {}

foo(1) { println <weak_warning descr="Cannot infer argument types">it</weak_warning> }
'''
  }

  void testOverloadedInClosureCS() {
    doTestHighlighting '''
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

  void testOverloadedInClosureCS2() {
    myFixture.enableInspections(new MissingReturnInspection())

    doTestHighlighting '''
import groovy.transform.CompileStatic

def <T> void foo(T t, Closure<T> cl) {}

@CompileStatic
def m() {
 foo(1) { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() }
}

'''
  }


  void testOverloadedInClosureCS3() {
    doTestHighlighting '''
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
    doTestHighlighting '''
import groovy.transform.CompileStatic
import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
  Map<String, Thread> works = existingPairs.stream().collect(Collectors.toMap({ kv -> kv.toString().trim() }, { kv -> kv }))
}

    '''
  }

  void 'test IDEA-171738-2'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

import java.util.stream.Collectors

@CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Integer key = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv })).keySet().getAt(1)
}
    '''
  }

  void 'test IDEA-171738-2_5'() {
    doTestHighlighting '''
import java.util.stream.Collectors

@groovy.transform.CompileStatic
void testAsItIs(Collection<Thread> existingPairs) {
    Map<Integer, Thread> value = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv }))
}
'''
  }

  void '_test IDEA-171738-3'() {
    doTestHighlighting '''
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
    doTestHighlighting '''
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
    doTestHighlighting '''
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
    doTestHighlighting '''
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
    doTestHighlighting '''
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
    doTestHighlighting '''
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
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.testRootDisposable)
    doTestHighlighting '''
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
    doTestHighlighting '''
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

  void 'test constructor  parameter'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
class Cl {

    Cl(Map<String, Integer> a, Condition<Cl> con, String s) {
    }

    interface Condition<T> {}

    static <T> Condition<T> alwaysFalse() {
        return (Condition<T>)null
    }


    static m() {
        new Cl(alwaysFalse(), name: 1, m: 2, new Object().toString(), sad: 12)
    }
}
'''
  }



  void 'test call without reference'() {
    doTestHighlighting '''
class E {
    E call() {
        null
    }
    E bar() {null}

}

new E().bar()()

'''
  }

  void 'test with closeable IDEA-197035'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    def stream = new FileInputStream("df")
    def c = stream.with { file ->
        new BufferedInputStream(file)
    }.withCloseable {
        int a = 0
        new BufferedInputStream(it)
    }
}
'''
  }

  void 'test SOE on map literal'() {
    doTestHighlighting '''
static method(a) {}
static method(a, b) {}
def q 

method(
        foo: {
            q.v = []
        },
        bar: 42
)

interface Foo {
     getProp()
}

class A {
    Foo foo
}

new A(foo: {
    <warning descr="Cannot resolve symbol 'prop'">prop</warning>
}) 
'''
  }

  void 'test IDEA-198057-1'() {
    doTestHighlighting '''
Optional<BigDecimal> foo(Optional<String> string) {
    string.flatMap {
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
    doTestHighlighting '''
Optional<BigDecimal> foo(Optional<String> string) {
  string.flatMap {
     return Optional.<BigDecimal> empty()    
  }
}
'''
  }

  void 'test IDEA-198057-3'() {
    doTestHighlighting '''
void foo() {
    def o = Optional.<BigDecimal> empty()
    Optional<BigDecimal>  d = o  
}
'''
  }

  void 'test call without reference with generics'() {
    doTestHighlighting '''\
class E {
    def <K,V> Map<K, V> call(Map<K, V> m) { m }
}

static <K,V> Map<K, V> getMap() { null }

@groovy.transform.CompileStatic
def usage() {
    Map<String, Integer> correct = new E()(getMap().withDefault({ 0 }))
}
'''
  }

  void testTypeSubstitutionWithClosureArg() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

def <T> T foo(T t) {
    return t
}

@CompileStatic
def m() {
    foo( {print 'aa'}).call()
}
'''
  }

  void 'test assign empty list literal to Set'() {
    doTestHighlighting 'Set<String> x = []'
  }

  void 'test assign empty list literal to Set @CS'() {
    doTestHighlighting '@groovy.transform.CompileStatic def bar() { Set<String> x = [] }'
  }

  void 'test nested closures expected type'() {
    doTestHighlighting '''\
@groovy.transform.TypeChecked
int mmm(Closeable cc) {
    1.with {
        cc.withCloseable {}
    }
    return 42
}
'''
  }

  void 'test IDEA-216095'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic
@CompileStatic
class AmbigousProblem {

    class A{
    }

    void methodA(Object[] objects){
    }

    void methodA(Class... classes){}

    void ambigous(){
        methodA(A)
    }
}
'''
  }

  void 'test IDEA-216095-2'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic
@CompileStatic
class AmbigousProblem {
    class A{
    }

    void methodA(Object o, Object... objects){
    }

    void methodA(String s, Object... classes){}

    void ambigous(){
        methodA("", A)
    }
}
'''
  }

  void 'test IDEA-216095-3'() {
    doTestHighlighting '''\
void foo(Integer i, Object... objects){
}

void foo(Object i, Integer... objects){
}

foo<warning descr="Method call is ambiguous">(1, 1)</warning>
'''
  }

  void 'test IDEA-219842'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    ["a"]*.trim().findAll {
        String line -> !line.isEmpty()
    }
}
'''
  }

  void 'test IDEA-221874'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def m() {
    def clazz = Thread
    clazz.declaredFields
        .findAll { !it.synthetic }
        .each {
            it.name
        }
}
'''
  }

  void 'test IDEA-221874-2'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def m2() {
    def all = Thread.declaredFields
            .findAll { !it.synthetic }

    print all.<error>get</error>(0)
}

''', true, false, false
  }

  void 'test resolve calls inside closure with CompileStatic'() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def test() {
    def x = 1
    1.with { x.byteValue() }
}''', true, false, false
  }

  void 'test +='() {
    doTestHighlighting '''
properties[""] += sourceSets
''', false
  }

  void 'test dispatch to consumer'() {
    doTestHighlighting """
interface MyRunnable { void run() }
interface MyConsumer { void consume(int x) }

void foo(MyConsumer consumer) {}
void foo(MyRunnable runnable) {}

foo({ name -> println 2 })
""", GroovyAssignabilityCheckInspection
  }

  void 'test dispatch to consumer 2'() {
    doTestHighlighting """
interface MyRunnable { void run() }
interface MyConsumer { void consume(int x) }

void foo(MyConsumer consumer) {}
void foo(MyRunnable runnable) {}

foo<warning>({ println 2 })</warning>
""", GroovyAssignabilityCheckInspection
  }

  void 'test no constant value warning'() {
    myFixture.addFileToProject("A.groovy", "class A { public static final String CONST = \"1\" }")
    myFixture.addClass("@Deprecated(since = A.CONST) public class Main {}")
    myFixture.configureByText("Main.java", "@Deprecated(since = A.CONST) public class Main {}")
    myFixture.checkHighlighting()
  }
}
