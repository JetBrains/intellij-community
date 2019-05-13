// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NotNull;

public class EclipseClasspath2ModulesTest extends Eclipse2ModulesTest {
  @Override
  protected String getTestPath() {
    return "round";
  }

  public void testAllProps() throws Exception {
    doTest("eclipse-ws-3.4.1-a", "all-props");
  }

  public void testMultiModuleDependencies() throws Exception {
    doTest("multi", "m1");
  }

  public void testRelativePaths() throws Exception {
    doTest("relPaths", "scnd");
  }

  public void testIDEA53188() throws Exception {
    doTest("multi", "main");
  }

  public void testSameNames() throws Exception {
    doTest("root", "proj1");
  }

  @Override
  protected void doTest(@NotNull final String workspaceRoot, @NotNull final String projectRoot) throws Exception {
    super.doTest(workspaceRoot, projectRoot);

    EclipseClasspathTest.doTest("/" + workspaceRoot + "/" + projectRoot, getProject());
  }
}