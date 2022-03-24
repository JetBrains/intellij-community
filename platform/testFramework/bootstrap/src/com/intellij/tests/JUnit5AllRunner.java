// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests;

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
import java.util.ServiceLoader;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class JUnit5AllRunner {
  
  public static void main(String[] args) {
    try {
      Launcher launcher = LauncherFactory.create();

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      ClassNameFilter nameFilter;
      try {
        nameFilter = createClassNameFilter(classLoader);
      }
      catch (Throwable e) {
        e.printStackTrace();
        return;
      }
      System.out.println("Number of test engines: " + ServiceLoader.load(TestEngine.class).stream().count());

      LauncherDiscoveryRequest discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectPackage(""))
        .filters(nameFilter, EngineFilter.excludeEngines(VintageTestDescriptor.ENGINE_ID)).build();
      TestPlan testPlan = launcher.discover(discoveryRequest);
      if (testPlan.containsTests()) {
        launcher.execute(testPlan, new JUnit5Runner.TCExecutionListener());
      }
      else {
        //see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR
        System.exit(42);
      }
    }
    finally {
      System.exit(0);
    }
  }

  private static ClassNameFilter createClassNameFilter(ClassLoader classLoader) throws NoSuchMethodException, ClassNotFoundException {
    Method included = Class.forName("com.intellij.TestCaseLoader", true, classLoader)
      .getDeclaredMethod("isClassIncluded", String.class);
    return new ClassNameFilter() {
      @Override
      public FilterResult apply(String className) {
        
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
