// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JUnitOpenSourceAtExceptionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package junit.framework; public class TestCase {}");
  }

  public void testStackTraceParserAcceptsInnerClassStacktrace() {
    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://org.example.Outer1Test$Outer2Test$InnerTest/testMe");
    testProxy.setTestFailed("failure", """
      \tat junit.framework.Assert.fail(Assert.java:55)
      \tat junit.framework.Assert.fail(Assert.java:64)
      \tat junit.framework.TestCase.fail(TestCase.java:230)
      \tat org.example.Outer1Test$Outer2Test$InnerTest.testMe(Dummy.java:8)
      \tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
      """, true);

    doTest("""
             package org.example;
             
             public class Outer1Test {
               public static class Outer2Test {
                 public static class InnerTest extends junit.framework.TestCase {
                   public void testMe() {
                     int i = 0;
                     fail(); // here
                   }
                 }
               }
             }""", testProxy, 7);
  }

  public void testStackTraceParserAcceptsLocalClassStacktrace() {
    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://org.example.MyTest$InnerTest/testMe");
    testProxy.setTestFailed("failure", """
      \tat junit.framework.Assert.fail(Assert.java:55)
      \tat junit.framework.Assert.fail(Assert.java:64)
      \tat junit.framework.TestCase.fail(TestCase.java:230)
      \tat org.example.MyTest$InnerTest$1LocalClass.myFail(Dummy.java:9)
      \tat org.example.MyTest$InnerTest.testMe(Dummy.java:13)
      \tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
      """, true);

    doTest("""
             package org.example;
             
             final class MyTest {
                 public static class InnerTest extends junit.framework.TestCase {
                   public void testMe() {
                     class LocalClass {
                       public void myFail() {
                         int i = 0;
                         fail();
                       }
                     }
                     LocalClass cls = new LocalClass();
                     cls.myFail(); // here
                   }
                 }
             }
             """, testProxy, 12);
  }

  public void testStackTraceParserAcceptsAnnonymousClassStacktrace() {
    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://org.example.MyTest$InnerTest/testMe");
    testProxy.setTestFailed("failure", """
      \tat junit.framework.Assert.fail(Assert.java:57)
      \tat junit.framework.TestCase.fail(TestCase.java:223)
      \tat org.example.MyTest$InnerTest.lambda$testMe$0(Dummy.java:7)
      \tat org.example.MyTest$InnerTest.testMe(Dummy.java:9)
      \tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
      """, true);

    doTest("""
             package org.example;
             
             final class MyTest {
               public static class InnerTest extends junit.framework.TestCase {
                 public void testMe() {
                   java.util.function.Consumer<String> check = str -> {
                     fail(str);
                   };
                   check.accept("test failed"); // here
                 }
               }
             }
             """, testProxy, 8);
  }

  public void testStackTraceParserAcceptsJavaStacktrace() {
    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest/testMe");
    testProxy.setTestFailed("failure", """
      \tat junit.framework.Assert.fail(Assert.java:57)
      \tat junit.framework.Assert.failNotEquals(Assert.java:329)
      \tat junit.framework.Assert.assertEquals(Assert.java:78)
      \tat junit.framework.Assert.assertEquals(Assert.java:234)
      \tat junit.framework.Assert.assertEquals(Assert.java:241)
      \tat junit.framework.TestCase.assertEquals(TestCase.java:409)
      \tat ATest.testMe(Dummy.java:6)
      """, true);
    doTest("""
             abstract class ATest extends junit.framework.TestCase {  public void testMe() {
                 int i = 0;
                 int j = 0;
                 int k = 0;
                 fail();
               }
             }
             public class ChildTest extends ATest {}
             """, testProxy, 5);
  }


  private void doTest(@Language("JAVA") final @NotNull @NonNls String content, @NotNull SMTestProxy testProxy, int expectedLine) {
    myFixture.addClass(content);

    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);

    final Location location = testProxy.getLocation(project, searchScope);
    assertNotNull(location);
    assertInstanceOf(location, MethodLocation.class);

    final JUnitConfiguration configuration = new JUnitConfiguration("p", getProject());
    final Navigatable descriptor =
      testProxy.getDescriptor(location, new JUnitConsoleProperties(configuration, DefaultRunExecutor.getRunExecutorInstance()));
    assertInstanceOf(descriptor, OpenFileDescriptor.class);
    final OpenFileDescriptor fileDescriptor = (OpenFileDescriptor)descriptor;
    final VirtualFile file = fileDescriptor.getFile();
    assertNotNull(file);
    assertEquals(expectedLine, fileDescriptor.getLine());
  }
}
