// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NotNull;

public class EclipseIml2ModulesTest extends Eclipse2ModulesTest {
  @Override
  protected String getTestPath() {
    return "iml";
  }

  public void testAllProps() throws Exception {
    doTest("eclipse-ws-3.4.1-a", "all-props");
  }

  public void testRelativePaths() throws Exception {
    doTest("relPaths", "scnd");
  }

  @Override
  protected void doTest(@NotNull final String workspaceRoot, @NotNull final String projectRoot) throws Exception {
    super.doTest(workspaceRoot, projectRoot);
    EclipseImlTest.doTest("/" + workspaceRoot + "/" + projectRoot, getProject());
  }
}