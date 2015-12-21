/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 28-Nov-2008
 */
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