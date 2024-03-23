// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

public class GrClosureParamsTest extends GrHighlightingTestBase {
  public void testFromStringWithNonFqnOptionsNoError() {
    addBigInteger();
    doTest("fromString/nonFqnOptions");
  }

  public void testFromStringDoNotResolveUsageContext() {
    doTest("fromString/doNotResolveUsageContext", "com/foo/bar/test.groovy");
  }

  public void testFromStringDoNotResolveUsageImportContext() {
    doTest("fromString/doNotResolveUsageImportContext", "com/foo/bar/test.groovy");
  }

  public void testFromStringDoNotResolveMethodContext() {
    doTest("fromString/doNotResolveMethodContext");
  }

  public void testFromStringResolveDefaultPackage() {
    doTest("fromString/resolveDefaultPackage");
  }

  public void testFromStringResolveDefaultPackageGeneric() {
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

  public void testCastArgument() {
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

  public void testCastArgumentError() {
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

  public void testSeveralSignatures() {
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

  public void testCastSeveralArguments() {
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

  @Override
  public final String getBasePath() {
    return super.getBasePath() + "closureParams/";
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
