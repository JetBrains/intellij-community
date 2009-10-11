/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.openapi.util;

import junit.framework.TestCase;

public class BuildNumberTest extends TestCase {
  public void testHistoricBuild() {
    assertEquals(new BuildNumber("", 75, 7512), BuildNumber.fromString("7512"));
  }
  
  public void testSnapshotDominates() {
    assertTrue(BuildNumber.fromString("90.SNAPSHOT").compareTo(BuildNumber.fromString("90.12345")) > 0);
    assertTrue(BuildNumber.fromString("IU-90.SNAPSHOT").compareTo(BuildNumber.fromString("RM-90.12345")) > 0);
  }
}
