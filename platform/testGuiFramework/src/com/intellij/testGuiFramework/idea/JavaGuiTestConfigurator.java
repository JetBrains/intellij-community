/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.idea;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testGuiFramework.framework.GuiTestConfiguratorBase;
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
public class JavaGuiTestConfigurator extends GuiTestConfiguratorBase {
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


  // Invoked using reflection and the IDE's ClassLoader.
  @NotNull
  private static Map<String, Object> extractTestConfiguration(@NotNull Method testMethod) {
    Map<String, Object> config = new HashMap<>();
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

  private JavaGuiTestConfigurator(@NotNull Map<String, Object> configuration,
                                  @NotNull String testName,
                                  @NotNull Object test,
                                  @NotNull ClassLoader classLoader) {
    super(configuration, testName, test, classLoader);
    myCloseProjectBeforeExecution = getBooleanValue(CLOSE_PROJECT_BEFORE_EXECUTION_KEY, configuration, true);
    myMinimumJdkVersion = getValue(RUN_WITH_MINIMUM_JDK_VERSION_KEY, configuration, JavaSdkVersion.class);
    myRetryCount = getIntValue(RETRY_COUNT_KEY, configuration, 0);
    mySkipSourceGenerationOnSync = getBooleanValue(SKIP_SOURCE_GENERATION_ON_SYNC_KEY, configuration, false);
    myTakeScreenshotOnTestFailure = getBooleanValue(TAKE_SCREENSHOT_ON_TEST_FAILURE_KEY, configuration, true);

    myTestName = testName;
    myTest = test;
    myClassLoader = classLoader;
  }


  // Invoked using reflection and the IDE's ClassLoader.
  private static void doSkipSourceGenerationOnSync() {
    System.out.println("Skipping source generation on project sync.");
  }

  protected boolean shouldSkipTest() throws Throwable {
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
    return myClassLoader.loadClass(JavaGuiTestConfigurator.class.getCanonicalName());
  }
}
