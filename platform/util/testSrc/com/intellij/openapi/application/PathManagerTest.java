// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.Contract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathManagerTest {
  private static final String TEST_PROP = "__ij_subst_test__";
  private static final String TEST_VALUE = "__" + new Random().nextInt(1000) + "__";

  @BeforeEach
  public void setUp() {
    System.setProperty(TEST_PROP, TEST_VALUE);
  }

  @AfterEach
  public void tearDown() {
    System.clearProperty(TEST_PROP);
  }

  @TempDir
  Path tempDir;

  @Test
  public void testResourceRoot() throws Exception {
    var resourceName = "/" + Test.class.getName().replace('.', '/') + ".class";
    var jarRoot = PathManager.getResourceRoot(getClass(), resourceName);
    var jar = Path.of(jarRoot);
    assertNotNull(jarRoot);
    assertTrue(jarRoot.endsWith(".jar"), jarRoot);
    assertTrue(Files.isRegularFile(jar));

    var directory = tempDir.resolve("extracted-jar");
    new Decompressor.Zip(jar).extract(directory);

    var loader = UrlClassLoader.build().files(List.of(directory)).get();
    var loadedClass = loader.loadClass(Test.class.getName());

    var dirRoot = PathManager.getResourceRoot(loadedClass, resourceName);
    assertNotNull(dirRoot);
    assertFalse(dirRoot.endsWith("/"), dirRoot);
    assertTrue(Files.isDirectory(Path.of(dirRoot)));
    assertEquals(directory.toString(), dirRoot);
  }

  @Test
  public void testVarSubstitution() {
    assertEquals("", substituteVars(""));
    assertEquals("abc", substituteVars("abc"));
    assertEquals("a$b$c", substituteVars("a$b$c"));

    assertEquals("/" + TEST_VALUE + "/" + TEST_VALUE + "/", substituteVars("/${" + TEST_PROP + "}/${" + TEST_PROP + "}/"));

    var home = System.clearProperty(PathManager.PROPERTY_HOME_PATH);
    try {
      assertEquals(PathManager.getHomeDir() + "\\build.txt", substituteVars("${idea.home.path}\\build.txt"));
      assertEquals("C:\\opt\\idea\\build.txt", PathManager.substituteVars("${idea.home.path}\\build.txt", "C:\\opt\\idea"));
    }
    finally {
      if (home != null) {
        System.setProperty(PathManager.PROPERTY_HOME_PATH, home);
      }
    }

    var config = System.clearProperty(PathManager.PROPERTY_CONFIG_PATH);
    try {
      assertEquals(PathManager.getConfigDir() + "/opts", substituteVars("${idea.config.path}/opts"));
    }
    finally {
      if (config != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, config);
      }
    }

    var system = System.clearProperty(PathManager.PROPERTY_SYSTEM_PATH);
    try {
      assertEquals(PathManager.getSystemDir() + "/logs2", substituteVars("${idea.system.path}/logs2"));
    }
    finally {
      if (system != null) {
        System.setProperty(PathManager.PROPERTY_CONFIG_PATH, system);
      }
    }

    assertEquals(PathManager.getBinDir().resolve("../license"), Path.of(substituteVars("../license")));

    assertEquals("//", substituteVars("/${unknown_property_ignore_the_error}/"));
  }

  @Test
  public void testDefaultCommonDataPath() {
    var vendorName = System.getProperty("idea.vendor.name", "JetBrains");

    assertEquals(
      "C:\\Users\\test\\AppData\\Roaming\\" + vendorName,
      PathManager.getDefaultCommonDataPathFor(OS.Windows, "C:\\Users\\test", Map.of())
    );
    assertEquals(
      "C:\\Data\\" + vendorName,
      PathManager.getDefaultCommonDataPathFor(OS.Windows, "C:\\Users\\test", Map.of("APPDATA", "C:\\Data"))
    );
    assertEquals(
      "/Users/test/Library/Application Support/" + vendorName,
      PathManager.getDefaultCommonDataPathFor(OS.macOS, "/Users/test", Map.of())
    );
    assertEquals(
      "/home/test/.local/share/" + vendorName,
      PathManager.getDefaultCommonDataPathFor(OS.Linux, "/home/test", Map.of())
    );
    assertEquals(
      "/var/data/" + vendorName,
      PathManager.getDefaultCommonDataPathFor(OS.Linux, "/home/test", Map.of("XDG_DATA_HOME", "/var/data"))
    );
  }

  @Contract("null -> null")
  private static String substituteVars(String s) {
    return PathManager.substituteVars(s, PathManager.getHomeDir().toString());
  }
}
