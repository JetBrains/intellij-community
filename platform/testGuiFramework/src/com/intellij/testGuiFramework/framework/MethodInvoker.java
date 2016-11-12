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

import com.intellij.testGuiFramework.script.GuiTestCase;
import org.fest.swing.image.ScreenshotTaker;
import org.jetbrains.annotations.NotNull;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import static com.intellij.testGuiFramework.framework.GuiTestRunner.canRunGuiTests;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.reflect.core.Reflection.method;

public class MethodInvoker extends Statement {
  @NotNull private final GuiTestConfigurator myTestConfigurator;
  @NotNull private final FrameworkMethod myTestMethod;
  @NotNull private final Object myTest;
  @NotNull private final ScreenshotTaker myScreenshotTaker;

  MethodInvoker(@NotNull FrameworkMethod testMethod, @NotNull Object test, @NotNull ScreenshotTaker screenshotTaker) throws Throwable {
    myTestConfigurator = GuiTestConfigurator.createNew(testMethod.getMethod(), test);
    myTestMethod = testMethod;
    myTest = test;
    myScreenshotTaker = screenshotTaker;
  }

  @Override
  public void evaluate() throws Throwable {
    if (myTestConfigurator.shouldSkipTest()) {
      //Message already printed in console.
      return;
    }
    String testFqn = getTestFqn();
    if (doesIdeHaveFatalErrors()) {
      // Fatal errors were caused by previous test. Skipping this test.
      System.out.println(String.format("Skipping test '%1$s': a fatal error has occurred in the IDE", testFqn));
      return;
    }
    System.out.println(String.format("Executing test '%1$s'", testFqn));

    int retryCount = myTestConfigurator.getRetryCount();
    for (int i = 0; i <= retryCount; i++) {
      if (i > 0) {
        System.out.println(String.format("Retrying execution of test '%1$s'", testFqn));
      }
      try {
        runTest(i);
        break; // no need to retry.
      }
      catch (Throwable throwable) {
        if (retryCount == i) {
          throw throwable; // Last run, throw any exceptions caught.
        }
        else {
          throwable.printStackTrace();
          failIfIdeHasFatalErrors();
        }
      }
    }
    failIfIdeHasFatalErrors();
  }

  public static boolean doesIdeHaveFatalErrors() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try {
      Class<?> guiTestsType = Class.forName(GuiTestUtil.class.getCanonicalName(), true, classLoader);
      //noinspection ConstantConditions
      return method("doesIdeHaveFatalErrors").withReturnType(boolean.class).in(guiTestsType).invoke();
    } catch (ClassNotFoundException ex) {
      // ignore exception
      return true;
    }
  }

  private static void failIfIdeHasFatalErrors() throws ClassNotFoundException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> guiTestsType = Class.forName(GuiTestUtil.class.getCanonicalName(), true, classLoader);
    method("failIfIdeHasFatalErrors").in(guiTestsType).invoke();
  }

  private void runTest(int executionIndex) throws Throwable {
    myTestConfigurator.executeSetupTasks();

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> guiTestCaseType = Class.forName(GuiTestCase.class.getCanonicalName(), true, classLoader);

    if (guiTestCaseType.isInstance(myTest)) {
      if (!canRunGuiTests()) {
        // We don't run tests in headless environment.
        return;
      }
      field("myTestName").ofType(String.class).in(myTest).set(myTestMethod.getName());
    }
    try {
      myTestMethod.invokeExplosively(myTest);
    }
    catch (Throwable e) {
      e.printStackTrace();
      takeScreenshot(executionIndex);
      throw e;
    }
  }

  @NotNull
  private String getTestFqn() {
    return myTestMethod.getMethod().getDeclaringClass() + "#" + myTestMethod.getName();
  }

  private void takeScreenshot(int executionIndex) {
    if (myTestConfigurator.shouldTakeScreenshotOnFailure()) {
      Method method = myTestMethod.getMethod();
      String fileNamePrefix = method.getDeclaringClass().getSimpleName() + "." + (executionIndex + 1) + "." + method.getName();
      String extension = ".png";

      try {
        File rootDir = IdeTestApplication.getFailedTestScreenshotDirPath();

        File screenshotFilePath = new File(rootDir, fileNamePrefix + extension);
        if (screenshotFilePath.isFile()) {
          SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy.HH:mm:ss");
          String now = format.format(new GregorianCalendar().getTime());
          screenshotFilePath = new File(rootDir, fileNamePrefix + "." + now + extension);
        }
        myScreenshotTaker.saveDesktopAsPng(screenshotFilePath.getPath());
        System.out.println("Screenshot of failed test taken and stored at " + screenshotFilePath.getPath());
      }
      catch (Throwable ignored) {
        System.out.println("Failed to take screenshot. Cause: " + ignored.getMessage());
      }
    }
  }
}
