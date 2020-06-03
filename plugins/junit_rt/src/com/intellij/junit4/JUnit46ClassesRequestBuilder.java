/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.junit4;

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.internal.builders.SuiteMethodBuilder;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JUnit46ClassesRequestBuilder {
  private JUnit46ClassesRequestBuilder() {}

  public static Request getClassesRequest(final String suiteName,
                                          Class<?>[] classes,
                                          Map<String, Set<String>> classMethods,
                                          Class<?> category) {
    boolean canUseSuiteMethod = canUseSuiteMethod(classMethods);
    try {
      if (category != null) {
        try {
          Class.forName("org.junit.experimental.categories.Categories");
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException("Categories are not available");
        }
      }

      Runner suite;
      if (canUseSuiteMethod) {
        try {
          Class.forName("org.junit.experimental.categories.Categories");
          suite = new IdeaSuite48(collectWrappedRunners(classes), suiteName, category);
        }
        catch (ClassNotFoundException e) {
          suite = new IdeaSuite(collectWrappedRunners(classes), suiteName);
        }
      } else {
        final AllDefaultPossibilitiesBuilder builder = new AllDefaultPossibilitiesBuilder(canUseSuiteMethod);
        try {
          Class.forName("org.junit.experimental.categories.Categories");
          suite = new IdeaSuite48(builder, classes, suiteName, category);
        }
        catch (ClassNotFoundException e) {
          suite = new IdeaSuite(builder, classes, suiteName);
        }
      }
      return Request.runner(suite);
    }
    catch (InitializationError e) {
      throw new RuntimeException(e);
    }
  }

  private static List<Runner> collectWrappedRunners(Class<?>[] classes) throws InitializationError {
    final List<Runner> runners = new ArrayList<Runner>();
    final List<Class<?>> nonSuiteClasses = new ArrayList<Class<?>>();
    final SuiteMethodBuilder suiteMethodBuilder = new SuiteMethodBuilder();
    for (Class<?> aClass : classes) {
      if (suiteMethodBuilder.hasSuiteMethod(aClass)) {
        try {
          runners.add(new ClassAwareSuiteMethod(aClass));
        }
        catch (Throwable throwable) {
          runners.add(new ErrorReportingRunner(aClass, throwable));
        }
      } else {
        nonSuiteClasses.add(aClass);
      }
    }
    runners.addAll(new AllDefaultPossibilitiesBuilder(false).runners(null, nonSuiteClasses.toArray(new Class[0])));
    return runners;
  }

  private static boolean canUseSuiteMethod(Map<String, Set<String>> classMethods) {
    for (Set<String> methods : classMethods.values()) {
      if (methods == null) {
        return true;
      }
      for (String methodName : methods) {
        if ("suite".equals(methodName)) {
          return true;
        }
      }
    }
    return classMethods.isEmpty();
  }
}