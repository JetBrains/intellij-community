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

import org.fest.swing.image.ScreenshotTaker;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.awt.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;

public class GuiTestRunner extends BlockJUnit4ClassRunner {

  private TestClass myTestClass;

  private final List<GarbageCollectorMXBean> myGarbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
  private final MemoryMXBean myMemoryMXBean = ManagementFactory.getMemoryMXBean();

  @Nullable private final ScreenshotTaker myScreenshotTaker;

  public GuiTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    //BufferedImage image = UIUtil.createImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    //Graphics2D graphics = ((Graphics2D)image.getGraphics());
    //graphics.fill(new Rectangle(0,0,100,100));
    //Application.getApplication().setDockIconImage(image);
    myScreenshotTaker = canRunGuiTests() ? new ScreenshotTaker() : null;

  }

  @Override
  protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
    if (!canRunGuiTests()) {
      notifier.fireTestAssumptionFailed(new Failure(describeChild(method), new AssumptionViolatedException("Headless environment")));
      System.out.println(String.format("Skipping test '%1$s'. UI tests cannot run in a headless environment.", method.getName()));
    } else if (MethodInvoker.doesIdeHaveFatalErrors()) {
      notifier.fireTestIgnored(describeChild(method)); // TODO: can we restart the IDE at this point, instead of giving up?
      System.out.println(String.format("Skipping test '%1$s': a fatal error has occurred in the IDE", method.getName()));
      notifier.pleaseStop();
    } else {
      printPerfStats();
      printTimestamp();
      super.runChild(method, notifier);
      printTimestamp();
      printPerfStats();
    }
  }

  private void printTimestamp() {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    System.out.println(dateFormat.format(new Date()));
  }

  private void printPerfStats() {
    long gcCount = 0, gcTime = 0;
    for (GarbageCollectorMXBean garbageCollectorMXBean : myGarbageCollectorMXBeans) {
      gcCount += garbageCollectorMXBean.getCollectionCount();
      gcTime += garbageCollectorMXBean.getCollectionTime();
    }
    System.out.printf("%d garbage collections; cumulative %d ms%n", gcCount, gcTime);
    myMemoryMXBean.gc();
    System.out.printf("heap: %s%n", myMemoryMXBean.getHeapMemoryUsage());
    System.out.printf("non-heap: %s%n", myMemoryMXBean.getNonHeapMemoryUsage());
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    FrameworkMethod newMethod;
    try {
      loadClassesWithIdeClassLoader();
      Method methodFromClassLoader = myTestClass.getJavaClass().getMethod(method.getName());
      newMethod = new FrameworkMethod(methodFromClassLoader);
    }
    catch (Exception e) {
      return new Fail(e);
    }
    Object test;
    try {
      test = new ReflectiveCallable() {
        @Override
        protected Object runReflectiveCall() throws Throwable {
          return createTest();
        }
      }.run();
    }
    catch (Throwable e) {
      return new Fail(e);
    }

    Statement statement = methodInvoker(newMethod, test);

    List<FrameworkMethod> beforeMethods = myTestClass.getAnnotatedMethods(Before.class);
    if (!beforeMethods.isEmpty()) {
      statement = new RunBefores(statement, beforeMethods, test);
    }

    List<FrameworkMethod> afterMethods = myTestClass.getAnnotatedMethods(After.class);
    if (!afterMethods.isEmpty()) {
      statement = new RunAfters(statement, afterMethods, test);
    }

    return statement;
  }

  public static boolean canRunGuiTests() {
    return !GraphicsEnvironment.isHeadless();
  }

  private void loadClassesWithIdeClassLoader() throws Exception {
    ClassLoader ideClassLoader = IdeTestApplication.getInstance().getIdeClassLoader();
    Thread.currentThread().setContextClassLoader(ideClassLoader);

    Class<?> testClass = getTestClass().getJavaClass();
    myTestClass = new TestClass(ideClassLoader.loadClass(testClass.getName()));
  }

  @Override
  protected Object createTest() throws Exception {
    return myTestClass != null ? myTestClass.getJavaClass().newInstance() : super.createTest();
  }

  @Override
  protected Statement methodInvoker(final FrameworkMethod method, Object test) {
    try {
      assertNotNull(myScreenshotTaker);
      return new MethodInvoker(method, test, myScreenshotTaker);
    }
    catch (Throwable e) {
      return new Fail(e);
    }
  }


}
