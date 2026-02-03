// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.logging.Logger;

/**
 * Runtime component that records test execution locations to support code ownership resolution.
 *
 * <p>This class is invoked during test execution by JUnit Platform listeners to capture metadata
 * about each test's location in the codebase. It extracts the test's module name, package path,
 * and source file name.</p>
 *
 * <h2>Output Format</h2>
 * <p>Test locations are written to an NDJSON file (one JSON object per line) at
 * {@code out/test-artifacts/test-locations.ndjson}. Each entry contains:</p>
 * <ul>
 *   <li>Test name (fully qualified)</li>
 *   <li>Module name (from JAR location)</li>
 *   <li>Package path (from bytecode)</li>
 *   <li>Source file name (from bytecode)</li>
 *   <li>Failure status (boolean)</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <p>This class works together with the build-time artifact generators and test owner resolver:</p>
 * <ul>
 *   <li>{@link TestsPaths} - Generates file location mappings at build time</li>
 *   <li>{@link ModulePaths} - Generates module path mappings at build time</li>
 *   <li>{@link CodeOwners} - Generates code ownership index at build time</li>
 *   <li>{@link ResolveTestOwners} - Consumes this output to determine test owners</li>
 * </ul>
 *
 * @see ResolveTestOwners
 * @see TestsPaths
 * @see ModulePaths
 * @see CodeOwners
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

  public static String getPackagePath(Class<?> testClass) {
    if (testClass == null) return "";

    try {
      String resourcePath = "/" + testClass.getName().replace('.', '/') + ".class";
      InputStream is = testClass.getResourceAsStream(resourcePath);

      if (is == null) {
        return "";
      }

      try (is) {
        ClassReader classReader = new ClassReader(is);
        PackageExtractor extractor = new PackageExtractor();
        classReader.accept(extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return extractor.packagePath;
      }
    }
    catch (Exception e) {
      return "";
    }
  }

  private static class PackageExtractor extends ClassVisitor {
    String packagePath = "";

    PackageExtractor() {
      super(Opcodes.ASM9);
    }
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      if (name != null) {
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash > 0) {
          packagePath = name.substring(0, lastSlash);
        }
      }
    }
  }

  private static String getModuleName(Path jarPath) {
    if (jarPath == null || !Files.exists(jarPath) || !jarPath.toString().endsWith(".jar")) {
      return null;
    }
    Path parent = jarPath.getParent();
    if (parent != null) {
      return parent.getFileName().toString();
    }
    return null;
  }

  public static String getFileName(Class<?> testClass) {
    try {
      String resourcePath = "/" + testClass.getName().replace('.', '/') + ".class";
      InputStream is = testClass.getResourceAsStream(resourcePath);
      if (is == null) {
        return null;
      }
      try (is) {
        ClassReader classReader = new ClassReader(is);
        SourceFileExtractor extractor = new SourceFileExtractor();
        classReader.accept(extractor, 0);
        return extractor.sourceFile;
      }
    }
    catch (Exception e) {
      return null;
    }
  }

  private static class SourceFileExtractor extends ClassVisitor {
    String sourceFile;

    SourceFileExtractor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visitSource(String source, String debug) {
      this.sourceFile = source;
    }
  }

  private static String escapeJson(String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t");
  }

  static void recordTestLocation(TestIdentifier testIdentifier, TestExecutionResult.Status status, String testName) {
    TestSource source = testIdentifier.getSource().orElse(null);
    String className = getClassNameFromTestSource(source);

    if (className == null) {
      LOG.info("Cannot find class name for " + testName);
      return;
    }

    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader == null) {
        LOG.info("Could not find class loader for test " + testName);
        return;
      }

      Class<?> testClass = Class.forName(className, false, classLoader);
      CodeSource codeSource = testClass.getProtectionDomain().getCodeSource();

      if (codeSource != null && codeSource.getLocation() != null) {
        Path jarPath = Paths.get(codeSource.getLocation().toURI());
        String moduleName = getModuleName(jarPath);
        if (moduleName == null) {
          LOG.info("No module found for " + codeSource.getLocation());
          return;
        }
        String packagePath = getPackagePath(testClass);
        String fileName = getFileName(testClass);
        if (fileName == null) {
          LOG.info("No file found for " + codeSource.getLocation());
          return;
        }
        boolean failed = (status == TestExecutionResult.Status.FAILED);

        String json = String.format(
          "{\"test\":\"%s\",\"module\":\"%s\",\"package\":\"%s\",\"file\":\"%s\",\"failed\":%s}%n",
          escapeJson(testName),
          escapeJson(moduleName),
          escapeJson(packagePath),
          escapeJson(fileName),
          failed
        );

        LOG.info("Writing to " + TEST_LOCATION_ARTIFACT.toAbsolutePath());
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
    }
    catch (Exception e) {
      LOG.info(e.getMessage());
    }
  }
}