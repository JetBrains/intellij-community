/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.openapi.util.BuildNumber;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginManagerTest {
  @Test
  public void compatibilityBranchBased() {
    assertCompatible("145.2", null, null);
    assertCompatible("145.2.2", null, null);

    assertCompatible("145.2", "145", null);
    assertCompatible("145.2", null, "146");
    assertCompatible("145.2.2", "145", null);
    assertCompatible("145.2.2", null, "146");
    assertIncompatible("145.2", null, "145");

    assertIncompatible("145.2", "146", null);
    assertIncompatible("145.2", null, "144");
    assertIncompatible("145.2.2", "146", null);
    assertIncompatible("145.2.2", null, "144");

    assertCompatible("145.2", "145.2", null);
    assertCompatible("145.2", null, "145.2");
    assertCompatible("145.2.2", "145.2", null);
    assertIncompatible("145.2.2", null, "145.2");

    assertIncompatible("145.2", "145.3", null);
    assertIncompatible("145.2", null, "145.1");
    assertIncompatible("145.2.2", "145.3", null);
    assertIncompatible("145.2.2", null, "145.1");

    assertCompatible("145.2", "140.3", null);
    assertCompatible("145.2", null, "146.1");
    assertCompatible("145.2.2", "140.3", null);
    assertCompatible("145.2.2", null, "146.1");

    assertIncompatible("145.2", "145.2.0", null);
    assertIncompatible("145.2", "145.2.1", null);
    assertCompatible("145.2", null, "145.2.3");
    assertCompatible("145.2.2", "145.2.0", null);
    assertCompatible("145.2.2", null, "145.2.3");
  }

  @Test
  public void compatibilityBranchBasedStar() {
    assertCompatible("145.10", "144.*", null);
    assertIncompatible("145.10", "145.*", null);
    assertIncompatible("145.10", "146.*", null);
    assertIncompatible("145.10", null, "144.*");
    assertCompatible("145.10", null, "145.*");
    assertCompatible("145.10", null, "146.*");
    
    assertCompatible("145.10.1", null, "145.*");
    assertCompatible("145.10.1", "145.10", "145.10.*");

    assertCompatible("145.SNAPSHOT", null, "145.*");
  }
  
  @Test
  public void compatibilitySnapshots() {
    assertIncompatible("145.SNAPSHOT", "146", null);
    assertIncompatible("145.2.SNAPSHOT", "145.3", null);

    assertCompatible("145.SNAPSHOT", "145.2", null);

    assertCompatible("145.SNAPSHOT", null, "146");
    assertIncompatible("145.SNAPSHOT", null, "145");
    assertIncompatible("145.SNAPSHOT", null, "144");
    assertIncompatible("145.2.SNAPSHOT", null, "145");
    assertIncompatible("145.2.SNAPSHOT", null, "144");
  }

  @Test
  public void convertExplicitBigNumberInUntilBuildToStar() {
    assertConvertsTo(null, null);
    assertConvertsTo("145", "145");
    assertConvertsTo("145.999", "145.999");
    assertConvertsTo("145.9999", "145.*");
    assertConvertsTo("145.99999", "145.*");
    assertConvertsTo("145.9999.1", "145.9999.1");
    assertConvertsTo("145.1000", "145.1000");
    assertConvertsTo("145.10000", "145.*");
    assertConvertsTo("145.100000", "145.*");
  }

  private static void assertConvertsTo(String untilBuild, String result) {
    assertEquals(result, IdeaPluginDescriptorImpl.convertExplicitBigNumberInUntilBuildToStar(untilBuild));
  }

  private static void assertIncompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString(ideVersion), sinceBuild, untilBuild, null, null));
  }

  private static void assertCompatible(String ideVersion, String sinceBuild, String untilBuild) {
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString(ideVersion), sinceBuild, untilBuild, null, null));
  }
}