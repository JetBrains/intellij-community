// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Contract;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class PathManagerTest {
  private static final String TEST_RPOP = "__ij_subst_test__";
  private static final String TEST_VALUE = "__" + new Random().nextInt(1000) + "__";

  @Before
  public void setUp() {
    System.setProperty(TEST_RPOP, TEST_VALUE);
  }

  @After
  public void tearDown() {
    System.clearProperty(TEST_RPOP);
  }

  @Rule
  public final TempDirectory tempDir = new TempDirectory();

  @Test
  public void testResourceRoot() throws Exception {
    String resourceName = "/" + Test.class.getName().replace('.', '/') + ".class";
    String jarRoot = PathManager.getResourceRoot(getClass(), resourceName);
    assertNotNull(jarRoot);
    assertTrue(jarRoot, jarRoot.endsWith(".jar"));
    assertTrue(new File(jarRoot).isFile());

    Path jar = Path.of(jarRoot);
    Path directory = tempDir.newDirectoryPath("extracted-jar");
    new Decompressor.Zip(jar).extract(directory);

    UrlClassLoader loader = UrlClassLoader.build().files(List.of(directory)).get();
    Class<?> loadedClass = loader.loadClass(Test.class.getName());

    String dirRoot = PathManager.getResourceRoot(loadedClass, resourceName);
    assertNotNull(dirRoot);
    assertFalse(dirRoot, dirRoot.endsWith("/"));
    assertTrue(new File(dirRoot).isDirectory());
    assertEquals(directory.toString(), dirRoot);
  }

  @Test
  public void testVarSubstitution() {
    assertEquals("", substituteVars(""));
    assertEquals("abc", substituteVars("abc"));
    assertEquals("a$b$c", substituteVars("a$b$c"));

    assertEquals("/" + TEST_VALUE + "/" + TEST_VALUE + "/", substituteVars("/${" + TEST_RPOP + "}/${" + TEST_RPOP + "}/"));

    String home = System.clearProperty(PathManager.PROPERTY_HOME_PATH);
    try {
      assertEquals(PathManager.getHomePath() + "/build.txt", substituteVars("${idea.home}/build.txt"));
      assertEquals(PathManager.getHomePath() + "\\build.txt", substituteVars("${idea.home.path}\\build.txt"));
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
      assertEquals(PathManager.getConfigPath() + "/opts", substituteVars("${idea.config.path}/opts"));
    }
    finally {
      if (config != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, config);
      }
    }

    String system = System.clearProperty(PathManager.PROPERTY_SYSTEM_PATH);
    try {
      assertEquals(PathManager.getSystemPath() + "/logs2", substituteVars("${idea.system.path}/logs2"));
    }
    finally {
      if (system != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, system);
      }
    }

    assertTrue(FileUtil.pathsEqual(PathManager.getBinPath() + "/../license", substituteVars("../license")));

    assertEquals("//", substituteVars("/${unknown_property_ignore_the_error}/"));
  }

  @Contract("null -> null")
  public static String substituteVars(String s) {
    return PathManager.substituteVars(s, PathManager.getHomePath());
  }
}