// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

public class GrLatestHighlightingTest extends GrHighlightingTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;
  }

  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new LocalInspectionTool[]{
      new GroovyAssignabilityCheckInspection(),
      new GrUnresolvedAccessInspection(),
      new GroovyAccessibilityInspection(),
      new MissingReturnInspection()
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
  }

  public void testIDEA_184690() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def com() {
                             BigDecimal[] c = [2, 3]
                             c == [2,3] as BigDecimal[]\s
                         }
                         """);
  }

  public void testIDEA_184690_2() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def com() {
                             new Object() == 1
                         }
                         """);
  }

  public void testIDEA184690_3() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def com() {
                             new Thread[1] == new Object[1]
                         }
                         """);
  }

  public void testIDEA_185371() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def com() {
                             Map<String, Integer> correct = [:].withDefault({ 0 })
                         }
                         """);
  }

  public void testIDEA_185371_2() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         static <K,V> Map<K, V> getMap() {
                           return new HashMap<K,V>()
                         }

                         @CompileStatic
                         def com() {
                             Map<String, Integer> correct = getMap().withDefault({ 0 })
                         }
                         """);
  }

  public void _testIDEA_185371_3() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_185371_4() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m() {
                             ''.with {
                                 print toUpperCase()
                             }
                         }
                         """);
  }

  public void testIDEA_185758_2() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_185758() {
    doTestHighlighting("""
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
                         """);
  }

  public void testOverloadedInClosure() {
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTestHighlighting("""
                         def <T> void foo(T t, Closure cl) {}

                         foo(1) { println <weak_warning descr="Cannot infer argument types">it</weak_warning> }
                         """);
  }

  public void testOverloadedInClosureCS() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         def <T> void foo(T t, Closure<T> cl) {}

                         @CompileStatic
                         def m() {
                           foo(1) {\s
                             println it\s
                             1
                           }
                         }
                         """);
  }

  public void testOverloadedInClosureCS2() {
    myFixture.enableInspections(new MissingReturnInspection());

    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         def <T> void foo(T t, Closure<T> cl) {}

                         @CompileStatic
                         def m() {
                          foo(1) { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() }
                         }
                         """);
  }

  public void testOverloadedInClosureCS3() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         import groovy.transform.stc.ClosureParams
                         import groovy.transform.stc.FirstParam

                         def <T> void foo(T t, @ClosureParams(value = FirstParam) Closure<T> cl) {}

                         @CompileStatic
                         def m() {
                           foo('') { it.toUpperCase() }
                         }
                         """);
  }

  public void testIDEA_171738() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         import java.util.stream.Collectors

                         @CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                           Map<String, Thread> works = existingPairs.stream().collect(Collectors.toMap({ kv -> kv.toString().trim() }, { kv -> kv }))
                         }
                         """);
  }

  public void testIDEA_171738_2() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         import java.util.stream.Collectors

                         @CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                             Integer key = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv })).keySet().getAt(1)
                         }
                         """);
  }

  public void testIDEA_171738_2_5() {
    doTestHighlighting("""
                         import java.util.stream.Collectors

                         @groovy.transform.CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                             Map<Integer, Thread> value = existingPairs.stream().collect(Collectors.toMap({ kv -> 1 }, { kv -> kv }))
                         }
                         """);
  }

  public void _testIDEA_171738_3() {
    doTestHighlighting("""
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
                         """);
  }

  public void _testIDEA_171738_4() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_189792() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         import java.nio.file.Files
                         import java.nio.file.Path
                         import java.util.stream.Stream

                         @CompileStatic
                         static Stream<String> topicStream(Path path) {
                             Files.list(path).map { it.toFile().name }
                         }
                         """);
  }

  public void testIDEA_189274() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_188105() {
    doTestHighlighting("""
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
                                 println toUpperCase()\s
                             }
                         }
                         """);
  }

  public void testIDEA_191019() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         import java.util.stream.Collectors

                         @CompileStatic
                         class GoodCodeRed {
                             static Set<Integer> intset = new HashSet<>(Arrays.asList(1, 2, 3, 4))

                             static Integer testCode() {
                                 return intset.stream().collect(Collectors.toList()).get(0)
                             }
                         }
                         """);
  }

  public void test_recursive_generics() {
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTestHighlighting("""
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
                         """);
  }

  public void test_constructor_with_generic_parameter() {
    doTestHighlighting("""
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
                         """);
  }

  public void test_constructor__parameter() {
    doTestHighlighting("""
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
                         """);
  }

  public void test_call_without_reference() {
    doTestHighlighting("""
                         class E {
                             E call() {
                                 null
                             }
                             E bar() {null}

                         }

                         new E().bar()()

                         """);
  }

  public void test_with_closeable_IDEA_197035() {
    doTestHighlighting("""
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
                         """);
  }

  public void test_SOE_on_map_literal() {
    doTestHighlighting("""
                         static method(a) {}
                         static method(a, b) {}
                         def q\s

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
                         })\s
                         """);
  }

  public void testIDEA_198057_1() {
    doTestHighlighting("""
                         Optional<BigDecimal> foo(Optional<String> string) {
                             string.flatMap {
                                 try {
                                     return Optional.of(new BigDecimal(it))
                                 } catch (Exception ignored) {
                                     return Optional.<BigDecimal> empty()
                                 }
                             }
                         }
                         """);
  }

  public void testIDEA_198057_2() {
    doTestHighlighting("""
                         Optional<BigDecimal> foo(Optional<String> string) {
                           string.flatMap {
                              return Optional.<BigDecimal> empty()   \s
                           }
                         }
                         """);
  }

  public void testIDEA_198057_3() {
    doTestHighlighting("""
                         void foo() {
                             def o = Optional.<BigDecimal> empty()
                             Optional<BigDecimal>  d = o \s
                         }
                         """);
  }

  public void test_call_without_reference_with_generics() {
    doTestHighlighting("""
                         class E {
                             def <K,V> Map<K, V> call(Map<K, V> m) { m }
                         }

                         static <K,V> Map<K, V> getMap() { null }

                         @groovy.transform.CompileStatic
                         def usage() {
                             Map<String, Integer> correct = new E()(getMap().withDefault({ 0 }))
                         }
                         """);
  }

  public void testTypeSubstitutionWithClosureArg() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         def <T> T foo(T t) {
                             return t
                         }

                         @CompileStatic
                         def m() {
                             foo( {print 'aa'}).call()
                         }
                         """);
  }

  public void test_assign_empty_list_literal_to_Set() {
    doTestHighlighting("Set<String> x = []");
  }

  public void test_assign_empty_list_literal_to_Set__CS() {
    doTestHighlighting("@groovy.transform.CompileStatic def bar() { Set<String> x = [] }");
  }

  public void test_nested_closures_expected_type() {
    doTestHighlighting("""
                         @groovy.transform.TypeChecked
                         int mmm(Closeable cc) {
                             1.with {
                                 cc.withCloseable {}
                             }
                             return 42
                         }
                         """);
  }

  public void testIDEA_216095() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_216095_2() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_216095_3() {
    doTestHighlighting("""
                         void foo(Integer i, Object... objects){
                         }

                         void foo(Object i, Integer... objects){
                         }

                         foo<warning descr="Method call is ambiguous">(1, 1)</warning>
                         """);
  }

  public void testIDEA_219842() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m() {
                             ["a"]*.trim().findAll {
                                 String line -> !line.isEmpty()
                             }
                         }
                         """);
  }

  public void testIDEA_221874() {
    doTestHighlighting("""
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
                         """);
  }

  public void testIDEA_221874_2() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m2() {
                             def all = Thread.declaredFields
                                     .findAll { !it.synthetic }

                             print all.<error>get</error>(0)
                         }

                         """, true, false, false);
  }

  public void testResolveCallsInsideClosureWithCompileStatic() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def test() {
                             def x = 1
                             1.with { x.byteValue() }
                         }""", true, false, false);
  }

  public void testOperatorAssignment() {
    doTestHighlighting("""
                         properties[""] += sourceSets
                         """, false);
  }

  public void testDispatchToConsumer() {
    doTestHighlighting("""
                         interface MyRunnable { void run() }
                         interface MyConsumer { void consume(int x) }

                         void foo(MyConsumer consumer) {}
                         void foo(MyRunnable runnable) {}

                         foo({ name -> println 2 })
                         """, GroovyAssignabilityCheckInspection.class);
  }

  public void testDispatchToConsumer2() {
    doTestHighlighting("""
                         interface MyRunnable { void run() }
                         interface MyConsumer { void consume(int x) }

                         void foo(MyConsumer consumer) {}
                         void foo(MyRunnable runnable) {}

                         foo<warning>({ println 2 })</warning>
                         """, GroovyAssignabilityCheckInspection.class);
  }

  public void testNoConstantValueWarning() {
    myFixture.addFileToProject("A.groovy", "class A { public static final String CONST = \"1\" }");
    myFixture.addClass("@Deprecated(since = A.CONST) public class Main {}");
    myFixture.configureByText("Main.java", "@Deprecated(since = A.CONST) public class Main {}");
    myFixture.checkHighlighting();
  }
}
