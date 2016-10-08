/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.framework;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import org.fest.reflect.reference.TypeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

/**
 * Collects configuration information from a UI test method's {@link IdeGuiTest} and {@link IdeGuiTestSetup} annotations and applies it
 * to the test before execution, using the the IDE's {@code ClassLoader} (which is the {@code ClassLoader} used by UI tests, to be able to
 * access IDE's services, state and components.)
 */
class GuiTestConfigurator {
  private static final String CLOSE_PROJECT_BEFORE_EXECUTION_KEY = "closeProjectBeforeExecution";
  private static final String RETRY_COUNT_KEY = "retryCount";
  private static final String RUN_WITH_MINIMUM_JDK_VERSION_KEY = "runWithMinimumJdkVersion";
  private static final String SKIP_SOURCE_GENERATION_ON_SYNC_KEY = "skipSourceGenerationOnSync";
  private static final String TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY = "takeScreenshotOnTestFailure";

  @NotNull private final String myTestName;
  @NotNull private final Object myTest;
  @NotNull private final ClassLoader myClassLoader;

  @Nullable private final Object myMinimumJdkVersion;

  private final boolean myCloseProjectBeforeExecution;
  private final int myRetryCount;
  private final boolean mySkipSourceGenerationOnSync;
  private final boolean myTakeScreenshotOnTestFailure;

  @NotNull
  static GuiTestConfigurator createNew(@NotNull Method testMethod, @NotNull Object test) throws Throwable {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> target = classLoader.loadClass(GuiTestConfigurator.class.getCanonicalName());
    Map<String, Object> testConfig = method("extractTestConfiguration").withReturnType(new TypeRef<Map<String, Object>>() {})
      .withParameterTypes(Method.class)
      .in(target)
      .invoke(testMethod);
    assertNotNull(testConfig);
    return new GuiTestConfigurator(testConfig, testMethod.getName(), test, classLoader);
  }

  // Invoked using reflection and the IDE's ClassLoader.
  @NotNull
  private static Map<String, Object> extractTestConfiguration(@NotNull Method testMethod) {
    Map<String, Object> config = new HashMap<String, Object>();
    IdeGuiTest guiTest = testMethod.getAnnotation(IdeGuiTest.class);
    if (guiTest != null) {
      config.put(CLOSE_PROJECT_BEFORE_EXECUTION_KEY, guiTest.closeProjectBeforeExecution());
      config.put(RUN_WITH_MINIMUM_JDK_VERSION_KEY, guiTest.runWithMinimumJdkVersion());
      config.put(RETRY_COUNT_KEY, guiTest.retryCount());
    }
    //TODO: replace with system properties (or VM options)
    config.put(SKIP_SOURCE_GENERATION_ON_SYNC_KEY, false);
    config.put(TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY, true);
    return config;
  }

  private GuiTestConfigurator(@NotNull Map<String, Object> configuration,
                              @NotNull String testName,
                              @NotNull Object test,
                              @NotNull ClassLoader classLoader) {
    myCloseProjectBeforeExecution = getBooleanValue(CLOSE_PROJECT_BEFORE_EXECUTION_KEY, configuration, true);
    myMinimumJdkVersion = getValue(RUN_WITH_MINIMUM_JDK_VERSION_KEY, configuration, JavaSdkVersion.class);
    myRetryCount = getIntValue(RETRY_COUNT_KEY, configuration, 0);
    mySkipSourceGenerationOnSync = getBooleanValue(SKIP_SOURCE_GENERATION_ON_SYNC_KEY, configuration, false);
    myTakeScreenshotOnTestFailure = getBooleanValue(TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY, configuration, true);

    myTestName = testName;
    myTest = test;
    myClassLoader = classLoader;
  }

  private static boolean getBooleanValue(@NotNull String key, @NotNull Map<String, Object> configuration, boolean defaultValue) {
    Object value = configuration.get(key);
    if (value instanceof Boolean) {
      return ((Boolean)value);
    }
    return defaultValue;
  }

  @Nullable
  private static Object getValue(@NotNull String key, @NotNull Map<String, Object> configuration, @NotNull Class<?> type) {
    Object value = configuration.get(key);
    if (value != null && value.getClass().getCanonicalName().equals(type.getCanonicalName())) {
      return value;
    }
    return null;
  }

  private static int getIntValue(@NotNull String key, @NotNull Map<String, Object> configuration, int defaultValue) {
    Object value = configuration.get(key);
    if (value instanceof Integer) {
      return ((Integer)value);
    }
    return defaultValue;
  }

  void executeSetupTasks() throws Throwable {
    closeAllProjects();
    skipSourceGenerationOnSync();
  }

  private void closeAllProjects() {
    if (myCloseProjectBeforeExecution) {
      method("closeAllProjects").in(myTest).invoke();
    }
  }

  private void skipSourceGenerationOnSync() throws Throwable {
    if (mySkipSourceGenerationOnSync) {
      Class<?> target = loadMyClassWithTestClassLoader();
      method("doSkipSourceGenerationOnSync").in(target).invoke();
    }
  }

  // Invoked using reflection and the IDE's ClassLoader.
  private static void doSkipSourceGenerationOnSync() {
    System.out.println("Skipping source generation on project sync.");
  }

  boolean shouldSkipTest() throws Throwable {
    if (myMinimumJdkVersion != null) {
      Class<?> target = loadMyClassWithTestClassLoader();
      Class<?> javaSdkVersionClass = myClassLoader.loadClass(JavaSdkVersion.class.getCanonicalName());
      Boolean hasRequiredJdk = method("hasRequiredJdk").withReturnType(boolean.class)
        .withParameterTypes(javaSdkVersionClass)
        .in(target)
        .invoke(myMinimumJdkVersion);
      assertNotNull(hasRequiredJdk);
      if (!hasRequiredJdk) {
        String jdkVersion = method("getDescription").withReturnType(String.class).in(myMinimumJdkVersion).invoke();
        System.out.println(String.format("Skipping test '%1$s'. It needs JDK %2$s or newer.", myTestName, jdkVersion));
        return true;
      }
    }

    return false;
  }

  // Invoked using reflection and the IDE's ClassLoader.
  private static boolean hasRequiredJdk(@NotNull JavaSdkVersion jdkVersion) {
    //Sdk jdk = IdeSdks.getJdk();
    Sdk jdk = null;
    assertNotNull("Expecting to have a JDK", jdk);
    JavaSdkVersion currentVersion = JavaSdk.getInstance().getVersion(jdk);
    return currentVersion != null && currentVersion.isAtLeast(jdkVersion);
  }

  boolean shouldTakeScreenshotOnFailure() {
    return myTakeScreenshotOnTestFailure;
  }

  int getRetryCount() {
    return myRetryCount;
  }

  @NotNull
  private Class<?> loadMyClassWithTestClassLoader() throws ClassNotFoundException {
    return myClassLoader.loadClass(GuiTestConfigurator.class.getCanonicalName());
  }
}
