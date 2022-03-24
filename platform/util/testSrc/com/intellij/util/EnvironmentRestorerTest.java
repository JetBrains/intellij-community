// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnvironmentRestorerTest {
  @Test
  public void restoreOverriddenVars() {
    Map<String, String> envs = new HashMap<>();

    envs.put(EnvironmentRestorer.RESERVED_ORIGINAL_VARIABLE_PREFIX + "VAR_A", "ORIGINAL_A");
    envs.put("VAR_A", "OVERRIDDEN_A");
    envs.put("VAR_B", "ORIGINAL_B");
    envs.put(EnvironmentRestorer.RESERVED_ORIGINAL_VARIABLE_PREFIX + "VAR_C", "");
    envs.put("VAR_C", "CREATED_C");
    envs.put(EnvironmentRestorer.RESERVED_ORIGINAL_VARIABLE_PREFIX + "VAR_D", "ORIGINAL_D");
    envs.put("VAR_D", "OVERRIDDEN_D");
    envs.put("VAR_E", "ORIGINAL_E");
    envs.put(EnvironmentRestorer.RESERVED_ORIGINAL_VARIABLE_PREFIX + "VAR_F", "");
    envs.put("VAR_F", "CREATED_F");

    EnvironmentRestorer.restoreOverriddenVars(envs);

    assertTrue(envs.containsKey("VAR_A"));
    assertEquals("ORIGINAL_A", envs.get("VAR_A"));
    assertTrue(envs.containsKey("VAR_B"));
    assertEquals("ORIGINAL_B", envs.get("VAR_B"));
    assertTrue(envs.containsKey("VAR_D"));
    assertEquals("ORIGINAL_D", envs.get("VAR_D"));
    assertTrue(envs.containsKey("VAR_E"));
    assertEquals("ORIGINAL_E", envs.get("VAR_E"));

    assertEquals(envs.size(), 4);
  }
}