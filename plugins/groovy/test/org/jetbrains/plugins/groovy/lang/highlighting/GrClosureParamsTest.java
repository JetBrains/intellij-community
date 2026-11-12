// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrClosureParamsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath() + "closureParams/");

  @Test
  public void testFromStringWithNonFqnOptionsNoError() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigInteger(myFixture);
    doTest("fromString/nonFqnOptions");
  }

  @Test
  public void testFromStringDoNotResolveUsageContext() {
    doTest("fromString/doNotResolveUsageContext", "com/foo/bar/test.groovy");
  }

  @Test
  public void testFromStringDoNotResolveUsageImportContext() {
    doTest("fromString/doNotResolveUsageImportContext", "com/foo/bar/test.groovy");
  }

  @Test
  public void testFromStringDoNotResolveMethodContext() {
    doTest("fromString/doNotResolveMethodContext");
  }

  @Test
  public void testFromStringResolveDefaultPackage() {
    doTest("fromString/resolveDefaultPackage");
  }

  @Test
  public void testFromStringResolveDefaultPackageGeneric() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("""
                          package com.foo.baz;

                          import groovy.lang.Closure;
                          import groovy.transform.stc.ClosureParams;
                          import groovy.transform.stc.FromString;

                          public class A<T> {
                              public void bar(@ClosureParams(value = FromString.class, options = "MyClass<T>") Closure c) {}
                          }
                          """);
    myFixture.addClass("public class MyClass<U> {}");
    myFixture.configureByText("a.groovy", """
      import com.foo.baz.A
      import groovy.transform.CompileStatic

      @CompileStatic
      class Usage {
          def foo() { new A<Integer>().bar { <error descr="Expected 'MyClass<java.lang.Integer>', found 'MyClass<java.lang.String>'">MyClass<String></error> a -> } }
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCastArgument() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("a.groovy", """
      import groovy.transform.CompileStatic

      @CompileStatic
      def m() {
          List<String> l = []
          l.findAll {
              Object o -> false
          }
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCastArgumentError() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("a.groovy", """
      import groovy.transform.CompileStatic

      @CompileStatic
      def m() {
          List<Object> l = []
          l.findAll {
              <error>Integer</error> o -> false
          }
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testSeveralSignatures() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);
    myFixture.configureByText("a.groovy", """
      import groovy.transform.CompileStatic
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def m1(String o, @ClosureParams(value=SimpleType.class, options="java.lang.String") Closure c) {}
      def m1(Double o, @ClosureParams(value=SimpleType.class, options="java.lang.Double") Closure c) {}

      @CompileStatic
      def m() {
          def a;
          m1<error descr="'m1' in 'a' cannot be applied to '(java.lang.Object, groovy.lang.Closure<java.lang.Void>)'">(a)</error> {
              long l -> println(l)
          }
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCastSeveralArguments() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.configureByText("a.groovy", """
      import groovy.transform.CompileStatic
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      void forEachTyped(Map<String, Integer> self, @ClosureParams(value=SimpleType.class, options=["java.lang.String", "java.lang.Integer"]) Closure closure) {
          self.each {
              k, v -> closure(k, v)
          };
      }

      @CompileStatic
      def m() {
          Map<String,Integer> m = [:]
          forEachTyped(m) {
              String key, <error>String</error> value -> println( key + ' ' + value)
          }
      }
      """);
    myFixture.checkHighlighting();
  }

  private void doTest(String dir, String path) {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection(), new GrUnresolvedAccessInspection());
    myFixture.copyDirectoryToProject(dir, ".");
    VirtualFile file = myFixture.findFileInTempDir(path);
    myFixture.allowTreeAccessForAllFiles();
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting();
  }

  private void doTest(String dir) {
    doTest(dir, "test.groovy");
  }
}
