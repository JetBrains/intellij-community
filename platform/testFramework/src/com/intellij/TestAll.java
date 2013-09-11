/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.idea.RecordExecution;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.*;
import com.intellij.tests.ExternalClasspathClassLoader;
import com.intellij.util.ArrayUtil;
import junit.framework.*;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"HardCodedStringLiteral", "CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class TestAll implements Test {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private final TestCaseLoader myTestCaseLoader;
  private long myStartTime = 0;
  private boolean myInterruptedByOutOfTime = false;
  private long myLastTestStartTime = 0;
  private String myLastTestClass;
  private int myRunTests = -1;
  private boolean mySavingMemorySnapshot;

  private static final int SAVE_MEMORY_SNAPSHOT = 1;
  private static final int START_GUARD = 2;
  private static final int RUN_GC = 4;
  private static final int CHECK_MEMORY = 8;
  private static final int FILTER_CLASSES = 16;

  public static int ourMode = SAVE_MEMORY_SNAPSHOT /*| START_GUARD | RUN_GC | CHECK_MEMORY*/ | FILTER_CLASSES;
  private static final boolean PERFORMANCE_TESTS_ONLY = System.getProperty(TestCaseLoader.PERFORMANCE_TESTS_ONLY_FLAG) != null;
  private int myLastTestTestMethodCount = 0;
  public static final int MAX_FAILURE_TEST_COUNT = 150;

  private TestRecorder myTestRecorder;

  private static final Filter PERFORMANCE_ONLY = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      String className = description.getClassName();
      String methodName = description.getMethodName();
      return className != null && hasPerformance(className) ||
             methodName != null && hasPerformance(methodName);
    }

    @Override
    public String describe() {
      return "Performance Tests Only";
    }
  };

  private static final Filter NO_PERFORMANCE = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      return !PERFORMANCE_ONLY.shouldRun(description);
    }

    @Override
    public String describe() {
      return "All Except Performance";
    }
  };

  @Override
  public int countTestCases() {
    List<Class> classes = myTestCaseLoader.getClasses();

    int count = 0;

    for (final Object aClass : classes) {
      Class testCaseClass = (Class)aClass;
      Test test = getTest(testCaseClass);
      if (test != null) count += test.countTestCases();
    }

    return count;
  }

  private void beforeFirstTest() {
    if ((ourMode & START_GUARD) != 0) {
      Thread timeAndMemoryGuard = new Thread() {
        @Override
        public void run() {
          log("Starting Time and Memory Guard");
          while (true) {
            try {
              try {
                Thread.sleep(10000);
              }
              catch (InterruptedException e) {
                e.printStackTrace();
              }
              // check for time spent on current test
              if (myLastTestStartTime != 0) {
                long currTime = System.currentTimeMillis();
                long secondsSpent = (currTime - myLastTestStartTime) / 1000L;
                Thread currentThread = getCurrentThread();
                if (!mySavingMemorySnapshot) {
                  if (secondsSpent > PlatformTestCase.ourTestTime * myLastTestTestMethodCount) {
                    UsefulTestCase.printThreadDump();
                    log("Interrupting current Test (out of time)! Test class: "+ myLastTestClass +" Seconds spent = " + secondsSpent);
                    myInterruptedByOutOfTime = true;
                    if (currentThread != null) {
                      currentThread.interrupt();
                      if (!currentThread.isInterrupted()) {
                        //noinspection deprecation
                        currentThread.stop(new RuntimeException("Current Test Interrupted: OUT OF TIME!"));
                      }

                      break;
                    }
                  }
                }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
            }
          }
          log("Time and Memory Guard finished.");
        }
      };
      timeAndMemoryGuard.setDaemon(true);
      timeAndMemoryGuard.start();
    }
    myStartTime = System.currentTimeMillis();
  }

  private static Thread getCurrentThread() {
    if (PlatformTestCase.ourTestThread != null) {
      return PlatformTestCase.ourTestThread;
    }
    else return LightPlatformTestCase.ourTestThread;
  }

  private void addErrorMessage(TestResult testResult, String message) {
    String processedTestsMessage = myRunTests <= 0 ? "None of tests was run" : myRunTests + " tests processed";
    try {
      testResult.startTest(this);
      testResult.addError(this, new Throwable(processedTestsMessage + " before: " + message));
      testResult.endTest(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run(final TestResult testResult) {
    loadTestRecorder();
    List<Class> classes = myTestCaseLoader.getClasses();
    int totalTests = classes.size();
    for (final Class aClass : classes) {
      boolean recording = false;
      if (myTestRecorder != null && shouldRecord(aClass)) {
        myTestRecorder.beginRecording(aClass, (RecordExecution) aClass.getAnnotation(RecordExecution.class));
        recording = true;
      }
      try {
        runNextTest(testResult, totalTests, aClass);
      }
      finally {
        if (recording) {
          myTestRecorder.endRecording();
        }
      }
      if (testResult.shouldStop()) break;
    }
    tryGc(10);
  }

  private boolean shouldRecord(Class aClass) {
    if (aClass.getAnnotation(RecordExecution.class) != null) {
      return true;
    }
    return false;
  }

  private void loadTestRecorder() {
    String recorderClassName = System.getProperty("test.recorder.class");
    if (recorderClassName != null) {
      try {
        Class<?> recorderClass = Class.forName(recorderClassName);
        myTestRecorder = (TestRecorder) recorderClass.newInstance();
      }
      catch (ClassNotFoundException e) {
        System.out.println("CNFE loading test recorder class: " + e);
      }
      catch (InstantiationException e) {
        System.out.println("InstantiationException loading test recorder class: " + e);
      }
      catch (IllegalAccessException e) {
        System.out.println("IAE loading test recorder class: " + e);
      }
    }
  }

  private void runNextTest(final TestResult testResult, int totalTests, Class testCaseClass) {
    myRunTests++;
    if (!checkAvaliableMemory(35, testResult)) {
      testResult.stop();
      return;
    }
    if (testResult.errorCount() + testResult.failureCount() > MAX_FAILURE_TEST_COUNT) {
      addErrorMessage(testResult, "Too many errors. Tests stopped. Total " + myRunTests + " of " + totalTests + " tests run");
      testResult.stop();
      return;
    }
    if (myStartTime == 0) {
      boolean ourClassLoader = getClass().getClassLoader().getClass().getName().startsWith("com.intellij.");
      if (!ourClassLoader) {
        beforeFirstTest();
      }
    }
    else {
      if (myInterruptedByOutOfTime) {
        addErrorMessage(testResult,
                        "Current Test Interrupted: OUT OF TIME! Class = " + myLastTestClass + " Total " + myRunTests + " of " +
                        totalTests +
                        " tests run");
        testResult.stop();
        return;
      }
    }

    log("\nRunning " + testCaseClass.getName());
    final Test test = getTest(testCaseClass);

    if (test == null) return;

    myLastTestClass = null;

    myLastTestClass = testCaseClass.getName();
    myLastTestStartTime = System.currentTimeMillis();
    myLastTestTestMethodCount = test.countTestCases();

    try {
      test.run(testResult);
    }
    catch (Throwable t) {
      if (t instanceof OutOfMemoryError) {
        if ((ourMode & SAVE_MEMORY_SNAPSHOT) != 0) {
          try {
            mySavingMemorySnapshot = true;
            log("OutOfMemoryError detected. Saving memory snapshot started");
          }
          finally {
            log("Saving memory snapshot finished");
            mySavingMemorySnapshot = false;
          }
        }
      }
      testResult.addError(test, t);
    }
  }

  private boolean checkAvaliableMemory(int neededMemory, TestResult testResult) {
    if ((ourMode & CHECK_MEMORY) == 0) return true;

    boolean possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
    if (possibleOutOfMemoryError) {
      tryGc(5);
      possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
      if (possibleOutOfMemoryError) {
        log("OutOfMemoryError: dumping memory");
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        String errorMessage = "Too much memory used. Total: " + total + " free: " + free + " used: " + (total - free) + "\n";
        addErrorMessage(testResult, errorMessage);
      }
    }
    return !possibleOutOfMemoryError;
  }

  private static boolean possibleOutOfMemory(int neededMemory) {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long realFreeMemory = runtime.freeMemory() + (maxMemory - runtime.totalMemory());
    long meg = 1024 * 1024;
    long needed = neededMemory * meg;
    return realFreeMemory < needed;
  }

  private static boolean isPerformanceTestsRun() {
    return PERFORMANCE_TESTS_ONLY;
  }

  @Nullable
  private static Test getTest(final Class testCaseClass) {
    if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) return null;

    Method suiteMethod = safeFindMethod(testCaseClass, "suite");
    if (suiteMethod != null && !isPerformanceTestsRun()) {
      try {
        return (Test)suiteMethod.invoke(null, ArrayUtil.EMPTY_CLASS_ARRAY);
      }
      catch (Exception e) {
        System.err.println("Failed to execute suite ()");
        e.printStackTrace();
      }
    }
    else {
      if (TestRunnerUtil.isJUnit4TestClass(testCaseClass)) {
        JUnit4TestAdapter adapter = new JUnit4TestAdapter(testCaseClass);
        if (!hasPerformance(testCaseClass.getSimpleName()) || !isPerformanceTestsRun()) {
          try {
            adapter.filter(isPerformanceTestsRun() ? PERFORMANCE_ONLY : NO_PERFORMANCE);
          }
          catch (NoTestsRemainException e1) {
            // Ignore
          }
        }
        return adapter;
      }

      final int[] testsCount = {0};
      TestSuite suite = new TestSuite(testCaseClass) {
        @Override
        public void addTest(Test test) {
          if (!(test instanceof TestCase)) {
            testsCount[0]++;
            super.addTest(test);
          }
          else {
            String name = ((TestCase)test).getName();
            if ("warning".equals(name)) return; // Mute TestSuite's "no tests found" warning
            if (isPerformanceTestsRun() ^ (hasPerformance(name) || hasPerformance(testCaseClass.getSimpleName())))
              return;

            Method method = findTestMethod((TestCase)test);
            if (method == null || !TestCaseLoader.isBombed(method)) {
              testsCount[0]++;
              super.addTest(test);
            }
          }
        }

        @Nullable
        private Method findTestMethod(final TestCase testCase) {
          return safeFindMethod(testCase.getClass(), testCase.getName());
        }
      };

      return testsCount[0] > 0 ? suite : null;
    }

    return null;
  }

  private static boolean hasPerformance(String name) {
    return name.toLowerCase().contains("performance");
  }

  @Nullable
  private static Method safeFindMethod(Class klass, String name) {
    try {
      return klass.getMethod(name);
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Set<String> normalizePaths(String[] array) {
    Set<String> answer = new LinkedHashSet<String>(array.length);
    for (String path : array) {
      answer.add(path.replace('\\', '/'));
    }
    return answer;
  }

  public static String[] getClassRoots() {
    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots property: " + testRoots);
      return testRoots.split(";");
    }
    String[] roots = ExternalClasspathClassLoader.getRoots();
    if (roots != null) {
      if (Comparing.equal(System.getProperty(TestCaseLoader.SKIP_COMMUNITY_TESTS), "true")) {
        System.out.println("Skipping community tests");
        Set<String> set = normalizePaths(roots);
        set.removeAll(normalizePaths(ExternalClasspathClassLoader.getExcludeRoots()));
        roots = set.toArray(new String[set.size()]);
      }
      
      System.out.println("Collecting tests from roots specified by classpath.file property: " + Arrays.toString(roots));
      return roots;
    }
    else {
      final ClassLoader loader = TestAll.class.getClassLoader();
      if (loader instanceof URLClassLoader) {
        final URL[] urls = ((URLClassLoader)loader).getURLs();
        final String[] classLoaderRoots = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
          classLoaderRoots[i] = VfsUtil.urlToPath(VfsUtil.convertFromUrl(urls[i]));
        }
        System.out.println("Collecting tests from classloader: " + Arrays.toString(classLoaderRoots));
        return classLoaderRoots;
      }
      return System.getProperty("java.class.path").split(File.pathSeparator);
    }
  }

  public TestAll(String packageRoot) throws Throwable {
    this(packageRoot, getClassRoots());
  }

  public TestAll(String packageRoot, String... classRoots) throws IOException, ClassNotFoundException {
    String classFilterName = "tests/testGroups.properties";
    if (Boolean.parseBoolean(System.getProperty("idea.ignore.predefined.groups")) || (ourMode & FILTER_CLASSES) == 0) {
      classFilterName = "";
    }
    myTestCaseLoader = new TestCaseLoader(classFilterName, isPerformanceTestsRun());
    myTestCaseLoader.addFirstTest(Class.forName("_FirstInSuiteTest"));
    myTestCaseLoader.addLastTest(Class.forName("_LastInSuiteTest"));

    fillTestCases(myTestCaseLoader, packageRoot, classRoots);
  }

  public static void fillTestCases(TestCaseLoader testCaseLoader, String packageRoot, String... classRoots) throws IOException {
    for (String classRoot : classRoots) {
      int oldCount = testCaseLoader.getClasses().size();
      ClassFinder classFinder = new ClassFinder(new File(FileUtil.toSystemDependentName(classRoot)), packageRoot);
      testCaseLoader.loadTestCases(classFinder.getClasses());
      int newCount = testCaseLoader.getClasses().size();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classRoot);
      }
    }

    if (testCaseLoader.getClasses().size() == 1) {
      testCaseLoader.clearClasses();
    }

    log("Number of test classes found: " + testCaseLoader.getClasses().size());
  }

  private static void log(String message) {
    TeamCityLogger.info(message);
  }

  // [myakovlev] Do not delete - it is for debugging
  public static void tryGc(int times) {
    if ((ourMode & RUN_GC) == 0) return;

    for (int qqq = 1; qqq < times; qqq++) {
      try {
        Thread.sleep(qqq * 1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.gc();
      //long mem = Runtime.getRuntime().totalMemory();
      log("Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
    }
  }

}
