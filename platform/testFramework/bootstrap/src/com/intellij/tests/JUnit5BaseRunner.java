// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"CallToPrintStackTrace"})
public abstract class JUnit5BaseRunner {
  private static final ClassLoader ourClassLoader = Thread.currentThread().getContextClassLoader();
  private static final Launcher launcher = LauncherFactory.create();

  abstract List<? extends DiscoverySelector> getTestsSelectors(ClassLoader classLoader);

  abstract Filter<?>[] getTestFilters(ClassLoader classLoader);

  abstract TestExecutionListener getTestExecutionListener();

  private LauncherDiscoveryRequest getDiscoveryRequest() {
    return LauncherDiscoveryRequestBuilder.request()
      .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
      .selectors(this.getTestsSelectors(ourClassLoader))
      .filters(this.getTestFilters(ourClassLoader))
      .build();
  }

  public static Set<Path> getClassPathRoots(ClassLoader classLoader) throws Throwable {
    //noinspection unchecked
    List<Path> paths = (List<Path>)MethodHandles.publicLookup()
      .findStatic(Class.forName("com.intellij.TestAll", false, classLoader),
                  "getClassRoots", MethodType.methodType(List.class))
      .invokeExact();
    if (paths == null) return null;

    // Skip unrelated jars and any other archives, otherwise we will end up with test classes from dependencies.
    String relevantJarsRoot = System.getProperty("intellij.test.jars.location");
    return paths.stream().filter(path -> {
      return Files.isDirectory(path) ||
             (
               relevantJarsRoot != null &&
               path.getFileName().toString().endsWith(".jar") &&
               path.startsWith(relevantJarsRoot)
             );
    }).collect(Collectors.toSet());
  }

  public List<? extends DiscoverySelector> getTestSelectorsByClassPathRoots(ClassLoader classLoader) {
    Set<Path> classPathRoots;
    try {
      classPathRoots = getClassPathRoots(classLoader);
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return getSelectors(classPathRoots);
  }

  public static List<? extends DiscoverySelector> getSelectors(Set<Path> classPathRoots) {
    List<? extends DiscoverySelector> selectors;
    if (classPathRoots != null) {
      selectors = DiscoverySelectors.selectClasspathRoots(classPathRoots);
    }
    else {
      selectors = Collections.singletonList(DiscoverySelectors.selectPackage(""));
    }

    return selectors;
  }

  public final TestPlan getTestPlan() {
    LauncherDiscoveryRequest discoveryRequest = this.getDiscoveryRequest();
    return launcher.discover(discoveryRequest);
  }

  public static void execute(TestPlan testPlan, TestExecutionListener listener) {
    launcher.execute(testPlan, listener);
  }
}
