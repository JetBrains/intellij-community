// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import com.intellij.idea.IJIgnore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class JUnit5BazelRunnerTest {
  @Test
  void discoversJupiterAndVintageTestsByDefault() {
    assertThat(discoverTestClasses(null)).containsExactlyInAnyOrder(
      VintageSampleTest.class.getName(),
      JupiterSampleTest.class.getName()
    );
  }

  @Test
  @IJIgnore(issue = "MRI-3013")
  // failed on teamcity as com.intellij.TestCaseLoader.ourCommonTestClassesFilter evaluated before setting required properties
  void filtersTestsByConfiguredTestGroups(@TempDir Path tempDir) throws Exception {
    Path resourceRoot = tempDir.resolve("resources");
    Path testGroupsFile = resourceRoot.resolve("tests/testGroups.properties");
    Files.createDirectories(testGroupsFile.getParent());
    Files.writeString(testGroupsFile, "[JUPITER]\n" + JupiterSampleTest.class.getName() + "\n");

    Map<String, String> properties = new HashMap<>();
    properties.put("intellij.build.test.patterns", "");
    properties.put("intellij.build.test.groups", "JUPITER");
    properties.put("test.group.roots", null);
    try (URLClassLoader resourceClassLoader = new URLClassLoader(new URL[]{resourceRoot.toUri().toURL()}, getContextClassLoader())) {
      withSystemProperties(properties, () -> withContextClassLoader(resourceClassLoader, () -> {
        assertThat(discoverTestClasses(null)).containsExactly(JupiterSampleTest.class.getName());
        assertThat(System.getProperty("test.group.roots")).contains(testGroupsFile.toAbsolutePath().toString());
      }));
    }
  }

  @Test
  void excludesVintageTestsWhenVintageEngineIsDisabled() {
    assertThat(discoverTestClasses("false")).containsExactly(JupiterSampleTest.class.getName());
  }

  @Test
  void discoversOnlyVintageTestsWhenRequested() {
    assertThat(discoverTestClasses("only")).containsExactly(VintageSampleTest.class.getName());
  }

  @Test
  void rejectsUnsupportedVintageMode() {
    assertThatThrownBy(() -> JUnit5BazelRunner.createDiscoveryRequest(selectors(), "unsupportedEngine"))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("unsupportedEngine");
  }

  private static Set<String> discoverTestClasses(String engineVintage) {
    var request = JUnit5BazelRunner.createDiscoveryRequest(selectors(), engineVintage);
    var launcher = LauncherFactory.create(LauncherConfig.builder()
                                         .enableLauncherSessionListenerAutoRegistration(false)
                                         .build());
    var testPlan = launcher.discover(request);
    return testPlan
      .getRoots()
      .stream()
      .flatMap(root -> testPlan.getDescendants(root).stream())
      .filter(TestIdentifier::isTest)
      .map(JUnit5BazelRunnerTest::getClassName)
      .collect(Collectors.toSet());
  }

  private static List<DiscoverySelector> selectors() {
    return List.of(selectClass(VintageSampleTest.class), selectClass(JupiterSampleTest.class));
  }

  private static String getClassName(TestIdentifier testIdentifier) {
    return testIdentifier.getSource()
      .map(source -> {
        if (source instanceof MethodSource methodSource) {
          return methodSource.getClassName();
        }
        if (source instanceof ClassSource classSource) {
          return classSource.getClassName();
        }
        throw new AssertionError("Unexpected source: " + source);
      })
      .orElseThrow(() -> new AssertionError("Missing source for " + testIdentifier.getUniqueId()));
  }

  private static void withSystemProperties(Map<String, String> properties, ThrowingRunnable action) throws Exception {
    Map<String, String> previousValues = new HashMap<>();
    properties.keySet().forEach(key -> previousValues.put(key, System.getProperty(key)));
    try {
      properties.forEach((key, value) -> {
        if (value == null) {
          System.clearProperty(key);
        }
        else {
          System.setProperty(key, value);
        }
      });
      action.run();
    }
    finally {
      previousValues.forEach((key, value) -> {
        if (value == null) {
          System.clearProperty(key);
        }
        else {
          System.setProperty(key, value);
        }
      });
    }
  }

  private static ClassLoader getContextClassLoader() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
  }

  private static void withContextClassLoader(ClassLoader classLoader, ThrowingRunnable action) throws Exception {
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      action.run();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  public static class VintageSampleTest {
    @org.junit.Test
    public void vintageTest() {
    }
  }

  public static class JupiterSampleTest {
    @Test
    void jupiterTest() {
    }
  }
}
