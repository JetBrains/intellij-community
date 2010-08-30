/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
 * Date: 29-Mar-2010
 */
package org.jetbrains.idea.eclipse;

public class EclipseLinkedResourcesTest extends EclipseVarsTest{
  @Override
  protected String getRelativeTestPath() {
    return "linked";
  }

  public void testResolvedVars() throws Exception {
    EclipseClasspathTest.doTest("/test", getProject());
  }

  public void testResolvedVarsInOutput() throws Exception {
    EclipseClasspathTest.doTest("/test", getProject());
  }

  public void testResolvedVarsInIml() throws Exception {
    EclipseImlTest.doTest("/test", getProject());
  }

  public void testResolvedVarsInOutputImlCheck() throws Exception {
    EclipseImlTest.doTest("/test", getProject());
  }
}
