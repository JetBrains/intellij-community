// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class MapConstructorTest implements HighlightingTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String text) {
    String newText = text;
    if (text.contains("MapConstructor")) {
      newText = "import groovy.transform.MapConstructor\n" + newText;
    }

    if (text.contains("CompileStatic")) {
      newText = "import groovy.transform.CompileStatic\n" + newText;
    }

    String finalText = newText;
    EdtTestUtil.runInEdtAndWait(() -> highlightingTest(finalText, GroovyConstructorNamedArgumentsInspection.class));
  }

  @Test
  public void explicitMapConstructor() {
    doTest("""
             @MapConstructor
             class Rr {
                 String actionType = ""
                 long referrerCode
                 boolean referrerUrl
                 Rr(String s) { int x = 1; }
             }
             
             @CompileStatic
             static void main(String[] args) {
                 new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
             }
             """);
  }

  @Test
  public void noExplicitMapConstructor() {
    doTest("""
             class Rr {
                 String actionType = ""
                 long referrerCode
                 boolean referrerUrl
                 Rr(String s) { int x = 1; }
             }
             
             @CompileStatic
             static void main(String[] args) {
                 new Rr<error>(actionType: "a", referrerCode: 10, referrerUrl: true)</error>
             }
             """);
  }

  @Test
  public void preAndPostResolving() {
    doTest("""
             
             class NN {}
             
             @CompileStatic
             @MapConstructor(pre = { super(); }, post = { assert referrerUrl = true })
             class Rr extends NN {
                 String actionType = ""
                 long referrerCode
                 boolean referrerUrl
                 Rr(String s) { int x = 1; }
             }
             
             @CompileStatic
             static void main(String[] args) {
                 new Rr(actionType: "a", referrerCode: 10, referrerUrl: true)
             }
             """);
  }

  @Test
  public void constructorParameterInPreAndPost() {
    doTest("""
             @CompileStatic
             @MapConstructor(pre = {args}, post = {args})
             class MyClass {
             }
             """);
  }

  @Test
  public void unknownLabel() {
    doTest("""
             @MapConstructor(excludes = "actionType")
             class Rr {
                 String actionType
                 long referrerCode;
                 boolean referrerUrl;
             }
             
             @CompileStatic
             static void main(String[] args) {
                 def x = new Rr(<warning>actionType</warning>: "abc")
             }""");
  }

  @Test
  public void staticProperty() {
    doTest("""
             @MapConstructor(excludes = "actionType")
             class Rr {
                 String actionType
                 long referrerCode;
                 static boolean referrerUrl
             }
             
             @CompileStatic
             static void main(String[] args) {
                 def x = new Rr(referrerCode: 10, <warning>referrerUrl</warning>: true)
             }""");
  }

  @Test
  public void beanProperty() {
    doTest("""
             @MapConstructor(allProperties = true)
             class Rr {
               void setFoo(String s) {
               }
             }
             
             @CompileStatic
             static void main(String[] args) {
               def x = new Rr(foo : "abc")
             }""");
  }

  @Test
  public void resolveSuperProperty() {
    doTest("""
             class Nn {
               String fff
             }
             
             @MapConstructor(includeSuperProperties = true)
             class Rr extends Nn {}
             
             @CompileStatic
             static void main(String[] args) {
               def x = new Rr(fff : "abc")
             }""");
  }

  @Test
  public void reducedVisibility() {
    getFixture().addFileToProject("other.groovy", """
      @groovy.transform.CompileStatic
      @groovy.transform.MapConstructor
      @groovy.transform.VisibilityOptions(constructor = Visibility.PRIVATE)
      class Cde {
          String actionType
          long referrerCode
          boolean referrerUrl
      }""");
    doTest("""
             class X {
                 @CompileStatic
                 static void main(String[] args) {
                     def x = new <error>Cde</error>(referrerCode : 10)
                 }
             }""");
  }

  @Test
  public void fieldWithInitializer() {
    doTest("""
             @MapConstructor(includeFields = true)
             class MapConstructorTestClass {
                 private final Integer number = 123
             }
             
             void mapConstructorTest() {
                 final o0 = new MapConstructorTestClass(number:333)
             }""");
  }

  @Test
  public void innerClass() {
    doTest("""
             class A {
               @MapConstructor
               class B {
                 String rr
               }
             
               def foo() {
                 new B(rr : "")
               }
             }
             """);
  }

  @Test
  public void immutable() {
    doTest("""
             import groovy.transform.Immutable
             
             @Immutable
             class P {
                 String a
                 def foo() {
                     return new P(a: '')
                 }
             }
             """);
  }
}
