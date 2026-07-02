// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Runtime component that records executed tests to support code ownership resolution.
 *
 * <p>This class is invoked during test execution by test listeners (JUnit Platform, Rider's TestNG) to capture
 * the identity of each executed test. It writes one NDJSON record (one JSON object per line) per test to
 * {@code out/test-artifacts/test-locations.ndjson}:</p>
 * <ul>
 *   <li>Test name as reported to TeamCity</li>
 *   <li>Test class's fully qualified name</li>
 *   <li>Failure status (boolean)</li>
 * </ul>
 *
 * <p>Test class FQNs are unique across the monorepo (enforced by {@code IntelliJProjectTestNamesUniquenessTest}),
 * so the FQN alone identifies the test's source file and therefore its code owner. After the test run, the
 * {@code ResolveTestOwners} build step (in {@code intellij.idea.ultimate.build}) joins these records with the
 * build-time FQN-to-owner artifact {@code test-class-owners.ndjson} and emits TeamCity test metadata with the
 * owner of each test.</p>
 */
public final class TestLocationStorage {

  public static final Logger LOG = Logger.getLogger(TestLocationStorage.class.getName());

  /**
   * Path to the test location artifact file (NDJSON format)
   */
  private static final Path TEST_LOCATION_ARTIFACT = getDefaultArtifactPath();

  private static Path getDefaultArtifactPath() {
    String customPath = System.getProperty("intellij.test.location.artifact");
    if (customPath != null) {
      return Paths.get(customPath);
    }

    Path parent = Paths.get("").getParent();
    if (parent != null) {
      Path grandParent = parent.getParent();
      if (grandParent != null) {
        if (Files.exists(grandParent.resolve("out"))) {
          return grandParent.resolve("out/test-artifacts/test-locations.ndjson");
        }
      }

      // We're in community folder
      if (Files.exists(parent.resolve("out"))) {
        return parent.resolve("out/test-artifacts/test-locations.ndjson");
      }
    }
    return Paths.get("out/test-artifacts/test-locations.ndjson");
  }

  private TestLocationStorage() {
  }

  private static String getClassNameFromTestSource(TestSource testSource) {
    if (testSource instanceof ClassSource) {
      return ((ClassSource)testSource).getClassName();
    }
    else if (testSource instanceof MethodSource) {
      return ((MethodSource)testSource).getClassName();
    }
    return null;
  }

  private static String escapeJson(String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t");
  }

  public static void recordTestLocation(String testName, Class<?> testClass, boolean failed) {
    if (testClass == null) {
      return;
    }

    recordTestLocation(testName, testClass.getName(), failed);
  }

  public static void recordTestLocation(TestIdentifier testIdentifier, TestExecutionResult.Status status, String testName) {
    TestSource source = testIdentifier.getSource().orElse(null);
    String className = getClassNameFromTestSource(source);
    if (className == null) {
      LOG.info("Cannot find class name for " + testIdentifier.getDisplayName());
      return;
    }

    recordTestLocation(testName, className, status == TestExecutionResult.Status.FAILED);
  }

  private static void recordTestLocation(String testName, String className, boolean failed) {
    String json = String.format(
      "{\"test\":\"%s\",\"class\":\"%s\",\"failed\":%s}%n",
      escapeJson(testName),
      escapeJson(className),
      failed
    );

    LOG.info("Writing to " + TEST_LOCATION_ARTIFACT.toAbsolutePath());
    try {
      synchronized (TEST_LOCATION_ARTIFACT) {
        // Ensure parent directory exists
        Path parentDir = TEST_LOCATION_ARTIFACT.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
          Files.createDirectories(parentDir);
        }
        Files.writeString(TEST_LOCATION_ARTIFACT, json,
                          StandardOpenOption.CREATE,
                          StandardOpenOption.APPEND);
      }
    }
    catch (Exception e) {
      LOG.info(e.getMessage());
    }
  }
}
