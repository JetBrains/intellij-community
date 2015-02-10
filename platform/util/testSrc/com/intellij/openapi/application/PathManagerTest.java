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
package com.intellij.openapi.application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class PathManagerTest {
  private static final String TEST_RPOP = "__ij_subst_test__";
  private static final String TEST_VALUE = "__" + new Random().nextInt(1000) + "__";

  @Before
  public void setUp() throws Exception {
    System.setProperty(TEST_RPOP, TEST_VALUE);
  }

  @After
  public void tearDown() throws Exception {
    System.clearProperty(TEST_RPOP);
  }

  @Test
  public void testVarSubstitution() {
    assertEquals("", PathManager.substituteVars(""));
    assertEquals("abc", PathManager.substituteVars("abc"));
    assertEquals("a$b$c", PathManager.substituteVars("a$b$c"));

    assertEquals("/" + TEST_VALUE + "/" + TEST_VALUE + "/", PathManager.substituteVars("/${" + TEST_RPOP + "}/${" + TEST_RPOP + "}/"));

    String home = System.clearProperty(PathManager.PROPERTY_HOME_PATH);
    try {
      assertEquals(PathManager.getHomePath() + "/build.txt", PathManager.substituteVars("${idea.home}/build.txt"));
      assertEquals(PathManager.getHomePath() + "\\build.txt", PathManager.substituteVars("${idea.home.path}\\build.txt"));
      assertEquals("/opt/idea/build.txt", PathManager.substituteVars("${idea.home}/build.txt", "/opt/idea"));
      assertEquals("C:\\opt\\idea\\build.txt", PathManager.substituteVars("${idea.home.path}\\build.txt", "C:\\opt\\idea"));
    }
    finally {
      if (home != null) {
        System.setProperty(PathManager.PROPERTY_HOME_PATH, home);
      }
    }

    String config = System.clearProperty(PathManager.PROPERTY_CONFIG_PATH);
    try {
      assertEquals(PathManager.getConfigPath() + "/opts", PathManager.substituteVars("${idea.config.path}/opts"));
    }
    finally {
      if (config != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, config);
      }
    }

    String system = System.clearProperty(PathManager.PROPERTY_SYSTEM_PATH);
    try {
      assertEquals(PathManager.getSystemPath() + "/logs2", PathManager.substituteVars("${idea.system.path}/logs2"));
    }
    finally {
      if (system != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, system);
      }
    }

    assertEquals(PathManager.getBinPath() + File.separator + "../license", PathManager.substituteVars("../license"));

    assertEquals("//", PathManager.substituteVars("/${unknown_property_ignore_the_error}/"));
  }
}
