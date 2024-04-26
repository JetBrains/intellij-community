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

public class GrLambdaLatestHighlightingTest extends GrHighlightingTestBase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0_REAL_JDK;
  }

  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new LocalInspectionTool[]{new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection(),
      new GroovyAccessibilityInspection(), new MissingReturnInspection()};
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
  }

  public void testIDEA_185371() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def com() {
                             Map<String, Integer> correct = [:].withDefault((it)->{ 0 })
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
                             Map<String, Integer> correct = getMap().withDefault((it)->{ 0 })
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
                           withDefault(getMap(), (it)->{ 'str '}).get(1).with {
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
                             ''.with (a) -> {
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
                            box.register<error descr="'register' in 'Container<A>' cannot be applied to '(java.lang.Class<C>, groovy.lang.Closure<C>)'">(C, param -> { new C() })</error>
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
                             box.register(B, param ->  { new B() })
                         }
                         """);
  }

  public void testOverloadedInClosure() {
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.getTestRootDisposable());
    doTestHighlighting("""
                         def <T> void foo(T t, Closure cl) {}

                         foo(1, (it) -> { println <weak_warning descr="Cannot infer argument types">it</weak_warning> })
                         """);
  }

  public void testOverloadedInClosureCS() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         def <T> void foo(T t, Closure<T> cl) {}

                         @CompileStatic
                         def m() {
                           foo(1, (it)-> {\s
                             println it\s
                             1
                           })
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
                          foo(1, it -> { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() })
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
                           foo('',  it -> { it.toUpperCase() })
                         }
                         """);
  }

  public void testIDEA_171738() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic
                         import java.util.stream.Collectors

                         @CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                           Map<String, Thread> works = existingPairs.stream().collect(Collectors.toMap(kv -> { kv.toString().trim() }, kv -> {  kv }))
                         }""");
  }

  public void testIDEA_171738_2() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         import java.util.stream.Collectors

                         @CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                             Integer key = existingPairs.stream().collect(Collectors.toMap(kv -> { 1 }, kv -> {kv })).keySet().getAt(1)
                         }
                         """);
  }

  public void testIDEA_171738_2_5() {
    doTestHighlighting("""
                         import java.util.stream.Collectors

                         @groovy.transform.CompileStatic
                         void testAsItIs(Collection<Thread> existingPairs) {
                             Map<Integer, Thread> value = existingPairs.stream().collect(Collectors.toMap(kv -> { 1 }, kv -> { kv }))
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
                             Files.list(path).map(it-> { it.toFile().name })
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
                                 events.reverse().findResult(LocalDateTime.MAX, it -> { it.timeToEscalate })
                             }
                         }
                         """);
  }

  public void _testIDEA_188105() {
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
                             apply("hello world", {
                                 println toUpperCase()\s
                             })
                         }
                         """);
  }

  public void testWithCloseableIDEA_197035() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def m() {
                             def stream = new FileInputStream("df")
                             def c = stream.with(file -> {\s
                                 new BufferedInputStream(file)
                             }).withCloseable((it) -> {
                                 int a = 0
                                 new BufferedInputStream(it)
                             })
                         }
                         """);
  }

  public void testIDEA_198057_1() {
    doTestHighlighting("""
                         Optional<BigDecimal> foo(Optional<String> string) {
                             string.flatMap (it) -> {
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
                           string.flatMap (it) -> {
                              return Optional.<BigDecimal> empty()   \s
                           }
                         }
                         """);
  }

  public void testCallWithoutReferenceWithGenerics() {
    doTestHighlighting("""
                         class E {
                             def <K,V> Map<K, V> call(Map<K, V> m) { m }
                         }

                         static <K,V> Map<K, V> getMap() { null }

                         @groovy.transform.CompileStatic
                         def usage() {
                             Map<String, Integer> correct = new E()(getMap().withDefault(it->{ 0 }))
                         }
                         """);
  }
}
