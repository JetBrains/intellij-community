// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.vintage.engine.descriptor.VintageTestDescriptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class JUnit5AllRunner {
  
  public static void main(String[] args) {
    Launcher launcher = LauncherFactory.create();
    
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    boolean includeFirstLast = !"true".equals(System.getProperty("intellij.build.test.ignoreFirstAndLastTests"));
    ClassNameFilter nameFilter;
    try {
      nameFilter = createClassNameFilter(classLoader, includeFirstLast);
    }
    catch (Throwable e) {
      e.printStackTrace();
      return;
    }
    List<DiscoverySelector> selectors = new ArrayList<>();
    if (includeFirstLast) {
      selectors.add(DiscoverySelectors.selectClass("_FirstInSuiteTest"));
      selectors.add(DiscoverySelectors.selectPackage(""));
      selectors.add(DiscoverySelectors.selectClass("_LastInSuiteTest"));
    }
    else {
      selectors.add(DiscoverySelectors.selectPackage(""));
    }

    System.out.println("Number of test engines: " + ServiceLoader.load(TestEngine.class).stream().count());
    
    LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
      .selectors(selectors)
      .filters(nameFilter, EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID)).build();
    TestPlan testPlan = launcher.discover(discoveryRequest);
    if (testPlan.containsTests()) {
      launcher.execute(testPlan, new JUnit5Runner.TCExecutionListener());
    }
    System.exit(0);
  }

  private static ClassNameFilter createClassNameFilter(ClassLoader classLoader, boolean includeFirstLast) throws NoSuchMethodException, ClassNotFoundException {
    Method included = Class.forName("com.intellij.TestCaseLoader", true, classLoader)
      .getDeclaredMethod("isClassIncluded", String.class);
    return new ClassNameFilter() {
      @Override
      public FilterResult apply(String className) {
        if (includeFirstLast && ("_FirstInSuiteTest".equals(className) || "_LastInSuiteTest".equals(className))) {
          return FilterResult.included("Include '" + className + "'");
        }

        try {
          if ((Boolean)included.invoke(null, className)) {
            return FilterResult.included(null);
          }
          return FilterResult.excluded(null);
        }
        catch (Throwable e) {
          return FilterResult.excluded(e.getMessage());
        }
      }
    };
  }
}
