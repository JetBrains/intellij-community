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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginManagerTest {
  @Test
  public void compatibilityBranchBased() throws Exception {
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, null, null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "145", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "146", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "145", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "146", null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "145", null, null));

    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "146", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "144", null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "146", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "144", null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "145.2", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "145.2", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "145.2", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "145.2", null, null));

    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "145.3", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "145.1", null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "145.3", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "145.1", null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "140.3", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "146.1", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "140.3", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "146.1", null, null));

    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "145.2.0", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), "145.2.1", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2"), null, "145.2.3", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), "145.2.0", null, null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.2"), null, "145.2.3", null, null));
  }

  @Test
  public void compatibilityBranchBasedStar() throws Exception {
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), "144.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), "145.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), "146.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), null, "144.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), null, "145.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10"), null, "146.*", null, null));
    
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10.1"), null, "145.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.10.1"), "145.10", "145.10.*", null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.SNAPSHOT"), null, "145.*", null, null));
  }
  
  @Test
  public void compatibilityYearBasedStar() throws Exception {
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), "2016.1.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), "2016.2.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), "2016.2.*", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), null, "2016.1.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), null, "2016.2.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2"), null, "20163.*", null, null));
    
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.1"), null, "2016.2.*", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.1"), "2016.2", "2016.2.*", null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), null, "2016.2.*", null, null));
  }

  @Test
  public void compatibilityBranchBasedSnapshots() throws Exception {
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.SNAPSHOT"), "146", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.SNAPSHOT"), "145.3", null, null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.SNAPSHOT"), "145.2", null, null, null));

    // snapshot ignore until build (special case)
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.SNAPSHOT"), null, "145", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.SNAPSHOT"), null, "144", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.SNAPSHOT"), null, "145", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("145.2.SNAPSHOT"), null, "144", null, null));
  }

  @Test
  public void compatibilityYearBasedSnapshots() throws Exception {
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), "2016.3", null, null, null));
    assertTrue(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.2.SNAPSHOT"), "2016.2.3", null, null, null));

    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), "2016.2.2", null, null, null));

    // snapshot ignore until build (special case)
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), null, "2016.2", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), null, "2016.1", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.SNAPSHOT"), null, "2017.2", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.2.SNAPSHOT"), null, "2016.2", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.2.SNAPSHOT"), null, "2016.1", null, null));
    assertFalse(PluginManagerCore.isIncompatible(BuildNumber.fromString("2016.2.2.SNAPSHOT"), null, "144", null, null));
  }
}
