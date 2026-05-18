// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherSessionListener;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for running JUnit 5 tests without JUnit 3/4 on
 * the classpath must still be able to load {@link JUnit5TestSessionListener} via the
 * JUnit Platform's {@code ServiceLoader}.
 * <p>
 * The test reproduces such an environment by loading the listener through a
 * {@link URLClassLoader} that contains only the test-framework JAR plus the JUnit
 * Platform JARs, with the JVM platform classloader as parent. JUnit 3/4 classes are
 * therefore not reachable through this classloader.
 */
class JUnit5TestSessionListenerInstantiationTest {
  @Test
  void instantiatesWithoutJunit3Or4OnClasspath() throws Exception {
    URL[] urls = isolatedClasspath();
    try (URLClassLoader cl = new URLClassLoader("junit3-free", urls, ClassLoader.getPlatformClassLoader())) {
      assertThrows(ClassNotFoundException.class, () -> cl.loadClass("junit.framework.TestCase"));
      cl.loadClass(JUnit5TestSessionListener.class.getName()).getDeclaredConstructor().newInstance();
    }
  }

  private static URL[] isolatedClasspath() {
    return new URL[]{
      classpathRootOf(JUnit5TestSessionListener.class),   // intellij.platform.testFramework
      classpathRootOf(LauncherSessionListener.class),     // junit-platform-launcher
    };
  }

  /**
   * Returns the classpath root from which {@code c} was loaded -- either the enclosing JAR
   * (when launched under Bazel / IntelliJ test runners that use JAR-backed classloaders)
   * or the output directory (when launched from the IDE with directory-backed module output).
   * Avoids {@code ProtectionDomain#getCodeSource()} because some IntelliJ classloaders do
   * not populate it.
   */
  private static URL classpathRootOf(Class<?> c) {
    String resourceName = c.getName().replace('.', '/') + ".class";
    URL classUrl = c.getClassLoader().getResource(resourceName);
    assertNotNull(classUrl, "cannot locate class resource " + resourceName + " for " + c.getName());
    String s = classUrl.toString();
    String rootStr;
    if (s.startsWith("jar:") && s.contains("!/")) {
      rootStr = s.substring("jar:".length(), s.indexOf("!/"));
    }
    else if (s.endsWith(resourceName)) {
      rootStr = s.substring(0, s.length() - resourceName.length());
    }
    else {
      throw new IllegalStateException("unsupported class resource URL: " + s);
    }
    try {
      return URI.create(rootStr).toURL();
    }
    catch (java.net.MalformedURLException e) {
      throw new IllegalStateException("invalid classpath root URL: " + rootStr, e);
    }
  }
}
