// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JUnitOpenSourceAtExceptionTest extends LightCodeInsightFixtureTestCase  {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package junit.framework; public class TestCase {}");
  }

  public void testStackTraceParseerAcceptsJavaStacktrace() {
    myFixture.addClass("abstract class ATest extends junit.framework.TestCase {" +
                       "  public void testMe() {\n" +
                       "    int i = 0;\n" +
                       "    int j = 0;\n" +
                       "    int k = 0;\n" +
                       "    fail();\n" +
                       "  }\n" +
                       "}");
    myFixture.addClass("public class ChildTest extends ATest {}");

    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest/testMe");
    testProxy.setTestFailed("failure", "\tat junit.framework.Assert.fail(Assert.java:57)\n" +
                                       "\tat junit.framework.Assert.failNotEquals(Assert.java:329)\n" +
                                       "\tat junit.framework.Assert.assertEquals(Assert.java:78)\n" +
                                       "\tat junit.framework.Assert.assertEquals(Assert.java:234)\n" +
                                       "\tat junit.framework.Assert.assertEquals(Assert.java:241)\n" +
                                       "\tat junit.framework.TestCase.assertEquals(TestCase.java:409)\n" +
                                       "\tat ATest.testMe(Dummy.java:6)\n", true);
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
