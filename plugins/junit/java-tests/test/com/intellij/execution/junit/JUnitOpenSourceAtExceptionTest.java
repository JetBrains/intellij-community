// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class JUnitOpenSourceAtExceptionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package junit.framework; public class TestCase {}");
  }

  public void testStackTraceParseerAcceptsJavaStacktrace() {
    myFixture.addClass("""
                         abstract class ATest extends junit.framework.TestCase {  public void testMe() {
                             int i = 0;
                             int j = 0;
                             int k = 0;
                             fail();
                           }
                         }""");
    myFixture.addClass("public class ChildTest extends ATest {}");

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
    assertEquals(5, fileDescriptor.getLine());
  }

}
