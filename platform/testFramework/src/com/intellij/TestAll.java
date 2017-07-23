/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.idea.RecordExecution;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.*;
import com.intellij.tests.ExternalClasspathClassLoader;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import gnu.trove.THashSet;
import junit.framework.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "CallToPrintStackTrace", "UseOfSystemOutOrSystemErr", "TestOnlyProblems", "BusyWait"})
public class TestAll implements Test {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final int SAVE_MEMORY_SNAPSHOT = 1;
  private static final int START_GUARD = 2;
  private static final int RUN_GC = 4;
  private static final int CHECK_MEMORY = 8;
  private static final int FILTER_CLASSES = 16;

  private static final int ourMode = SAVE_MEMORY_SNAPSHOT /*| START_GUARD | RUN_GC | CHECK_MEMORY*/ | FILTER_CLASSES;

  private static final boolean PERFORMANCE_TESTS_ONLY = System.getProperty(TestCaseLoader.PERFORMANCE_TESTS_ONLY_FLAG) != null;
  private static final boolean INCLUDE_PERFORMANCE_TESTS = System.getProperty(TestCaseLoader.INCLUDE_PERFORMANCE_TESTS_FLAG) != null;
  private static final boolean INCLUDE_UNCONVENTIONALLY_NAMED_TESTS = System.getProperty(TestCaseLoader.INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG) != null;

  private static final int MAX_FAILURE_TEST_COUNT = 150;

  private static final Filter PERFORMANCE_ONLY = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      String className = description.getClassName();
      String methodName = description.getMethodName();
      return UsefulTestCase.isPerformanceTest(methodName, className);
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

  private final TestCaseLoader myTestCaseLoader;
  private long myStartTime;
  private boolean myInterruptedByOutOfTime;
  private long myLastTestStartTime;
  private String myLastTestClass;
  private int myRunTests = -1;
  private boolean mySavingMemorySnapshot;
  private int myLastTestTestMethodCount;
  private TestRecorder myTestRecorder;
  
  private static final List<Throwable> outClassLoadingProblems = new ArrayList<>();

  public TestAll(String rootPackage) throws Throwable {
    this(rootPackage, getClassRoots());
  }

  public TestAll(String rootPackage, List<File> classesRoots) throws IOException, ClassNotFoundException {
    String classFilterName = "tests/testGroups.properties";
    if ((ourMode & FILTER_CLASSES) == 0) {
      classFilterName = "";
    }

    myTestCaseLoader = new TestCaseLoader(classFilterName);
    myTestCaseLoader.addFirstTest(Class.forName("_FirstInSuiteTest"));
    myTestCaseLoader.addLastTest(Class.forName("_LastInSuiteTest"));
    fillTestCases(myTestCaseLoader, rootPackage, classesRoots);
  
    outClassLoadingProblems.addAll(myTestCaseLoader.getClassLoadingErrors());
  }
  
  public static List<Throwable> getLoadingClassProblems() {
    return outClassLoadingProblems;
  }

  public static List<File> getClassRoots() {
    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots property: " + testRoots);
      return StreamEx.of(testRoots.split(";")).map(File::new).toList();
    }
    List<File> roots = ExternalClasspathClassLoader.getRoots();
    if (roots != null) {
      List<File> excludeRoots = ExternalClasspathClassLoader.getExcludeRoots();
      if (excludeRoots != null) {
        System.out.println("Skipping tests from " + excludeRoots.size() + " roots");
        roots = new ArrayList<>(roots);
        roots.removeAll(new THashSet<>(excludeRoots, FileUtil.FILE_HASHING_STRATEGY));
      }

      System.out.println("Collecting tests from roots specified by classpath.file property: " + roots);
      return roots;
    }
    else {
      final ClassLoader loader = TestAll.class.getClassLoader();
      if (loader instanceof URLClassLoader) {
        return getClassRoots(((URLClassLoader)loader).getURLs());
      }
      final Class<? extends ClassLoader> loaderClass = loader.getClass();
      if (loaderClass.getName().equals("com.intellij.util.lang.UrlClassLoader")) {
        try {
          final Method declaredMethod = loaderClass.getDeclaredMethod("getBaseUrls");
          final List<URL> urls = (List<URL>)declaredMethod.invoke(loader);
          return getClassRoots(urls.toArray(new URL[urls.size()]));
        }
        catch (Throwable ignore) {}
      }
      return StreamEx.of(System.getProperty("java.class.path").split(File.pathSeparator)).map(File::new).toList();
    }
  }

  private static List<File> getClassRoots(URL[] urls) {
    final List<File> classLoaderRoots = StreamEx.of(urls).map(url -> new File(VfsUtilCore.urlToPath(VfsUtilCore.convertFromUrl(url)))).toList();
    System.out.println("Collecting tests from " + classLoaderRoots);
    return classLoaderRoots;
  }

  public static void fillTestCases(TestCaseLoader testCaseLoader, String rootPackage, List<File> classesRoots) throws IOException {
    long before = System.currentTimeMillis();
    for (File classesRoot : classesRoots) {
      int oldCount = testCaseLoader.getClasses().size();
      ClassFinder classFinder = new ClassFinder(classesRoot, rootPackage, INCLUDE_UNCONVENTIONALLY_NAMED_TESTS);
      testCaseLoader.loadTestCases(classesRoot.getName(), classFinder.getClasses());
      int newCount = testCaseLoader.getClasses().size();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classesRoot);
      }
    }

    if (testCaseLoader.getClasses().size() == 1) {
      testCaseLoader.clearClasses();
    }
    long after = System.currentTimeMillis();
    
    String message = "Number of test classes found: " + testCaseLoader.getClasses().size() 
                      + " time to load: " + (after - before) / 1000 + "s.";
    System.out.println(message);
    log(message);
  }

  @Override
  public int countTestCases() {
    int count = 0;
    for (Object aClass : myTestCaseLoader.getClasses()) {
      Test test = getTest((Class)aClass);
      if (test != null) count += test.countTestCases();
    }
    return count;
  }

  private void beforeFirstTest() {
    if ((ourMode & START_GUARD) != 0) {
      Thread timeAndMemoryGuard = new Thread("Time and Memory Guard") {
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
                        currentThread.stop();
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
    return LightPlatformTestCase.ourTestThread;
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

    final TestListener testListener = loadDiscoveryListener();
    if (testListener != null) {
      testResult.addListener(testListener);
    }

    List<Class> classes = myTestCaseLoader.getClasses();
    int totalTests = classes.size();
    for (Class<?> aClass : classes) {
      boolean recording = false;
      if (myTestRecorder != null && shouldRecord(aClass)) {
        myTestRecorder.beginRecording(aClass, aClass.getAnnotation(RecordExecution.class));
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

    if (testListener instanceof Closeable) {
      try {
        ((Closeable)testListener).close();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    tryGc(10);
  }

  private static TestListener loadDiscoveryListener() {
    final String discoveryListener = System.getProperty("test.discovery.listener");
    if (discoveryListener != null) {
      try {
        return (TestListener)Class.forName(discoveryListener).newInstance();
      }
      catch (Throwable e) {
        return null;
      }
    }
    return null;
  }

  private static boolean shouldRecord(@NotNull Class<?> aClass) {
    return aClass.getAnnotation(RecordExecution.class) != null;
  }

  private void loadTestRecorder() {
    String recorderClassName = System.getProperty("test.recorder.class");
    if (recorderClassName != null) {
      try {
        Class<?> recorderClass = Class.forName(recorderClassName);
        myTestRecorder = (TestRecorder) recorderClass.newInstance();
      }
      catch (Exception e) {
        System.out.println("Error loading test recorder class '" + recorderClassName + "': " + e);
      }
    }
  }

  private void runNextTest(final TestResult testResult, int totalTests, Class testCaseClass) {
    myRunTests++;
    if (!checkAvailableMemory(35, testResult)) {
      testResult.stop();
      return;
    }

    if (testResult.errorCount() + testResult.failureCount() > MAX_FAILURE_TEST_COUNT) {
      addErrorMessage(testResult, "Too many errors. Executed: " + myRunTests + " of " + totalTests);
      testResult.stop();
      return;
    }

    if (myStartTime == 0) {
      String loaderName = getClass().getClassLoader().getClass().getName();
      if (!loaderName.startsWith("com.intellij.")) {
        beforeFirstTest();
      }
    }
    else {
      if (myInterruptedByOutOfTime) {
        addErrorMessage(testResult, "Time out in " + myLastTestClass + ". Executed: " + myRunTests + " of " + totalTests);
        testResult.stop();
        return;
      }
    }

    log("\nRunning " + testCaseClass.getName());
    Test test = getTest(testCaseClass);
    if (test == null) return;

    myLastTestClass = testCaseClass.getName();
    myLastTestStartTime = System.currentTimeMillis();
    myLastTestTestMethodCount = test.countTestCases();

    try {
      test.run(testResult);
    }
    catch (OutOfMemoryError t) {
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
      testResult.addError(test, t);
    }
    catch (Throwable t) {
      testResult.addError(test, t);
    }
  }

  private boolean checkAvailableMemory(int neededMemory, TestResult testResult) {
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
    long realFreeMemory = runtime.freeMemory() + maxMemory - runtime.totalMemory();
    long meg = 1024 * 1024;
    long needed = neededMemory * meg;
    return realFreeMemory < needed;
  }

  static boolean isPerformanceTestsRun() {
    return PERFORMANCE_TESTS_ONLY;
  }
  
  private static boolean isIncludingPerformanceTestsRun() {
    return INCLUDE_PERFORMANCE_TESTS;
  }

  @Nullable
  private static Test getTest(@NotNull final Class<?> testCaseClass) {
    try {
      if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) {
        return null;
      }
      Bombed classBomb = testCaseClass.getAnnotation(Bombed.class);
      if (classBomb != null && PlatformTestUtil.bombExplodes(classBomb)) {
        return new ExplodedBomb(testCaseClass.getName(), classBomb);
      }

      Method suiteMethod = safeFindMethod(testCaseClass, "suite");
      if (suiteMethod != null && !isPerformanceTestsRun()) {
        return (Test)suiteMethod.invoke(null, ArrayUtil.EMPTY_CLASS_ARRAY);
      }

      if (TestRunnerUtil.isJUnit4TestClass(testCaseClass)) {
        JUnit4TestAdapter adapter = new JUnit4TestAdapter(testCaseClass);
        boolean runEverything = isIncludingPerformanceTestsRun() || isPerformanceTest(null, testCaseClass) && isPerformanceTestsRun();
        if (!runEverything) {
          try {
            adapter.filter(isPerformanceTestsRun() ? PERFORMANCE_ONLY : NO_PERFORMANCE);
          }
          catch (NoTestsRemainException ignored) {}
        }
        return adapter;
      }

      final int[] testsCount = {0};
      TestSuite suite = new TestSuite(testCaseClass) {
        @Override
        public void addTest(Test test) {
          if (!(test instanceof TestCase)) {
            doAddTest(test);
          }
          else {
            String name = ((TestCase)test).getName();
            if ("warning".equals(name)) return; // Mute TestSuite's "no tests found" warning
            if (!isIncludingPerformanceTestsRun() && (isPerformanceTestsRun() ^ isPerformanceTest(name, testCaseClass)))
              return;

            Method method = findTestMethod((TestCase)test);
            if (method == null) {
              doAddTest(test);
            }
            else {
              Bombed methodBomb = method.getAnnotation(Bombed.class);
              if (methodBomb == null) {
                doAddTest(test);
              }
              else if (PlatformTestUtil.bombExplodes(methodBomb)) {
                doAddTest(new ExplodedBomb(method.getDeclaringClass().getName() + "." + method.getName(), methodBomb));
              }
            }
          }
        }

        private void doAddTest(Test test) {
          testsCount[0]++;
          super.addTest(test);
        }

        @Nullable
        private Method findTestMethod(final TestCase testCase) {
          return safeFindMethod(testCase.getClass(), testCase.getName());
        }
      };

      return testsCount[0] > 0 ? suite : null;
    }
    catch (Throwable t) {
      System.err.println("Failed to load test: " + testCaseClass.getName());
      t.printStackTrace(System.err);
      return null;
    }
  }

  static boolean shouldIncludePerformanceTestCase(Class aClass) {
    return isIncludingPerformanceTestsRun() || isPerformanceTestsRun() || !isPerformanceTest(null,aClass);
  }

  private static boolean isPerformanceTest(String methodName, Class aClass) {
    return UsefulTestCase.isPerformanceTest(methodName, aClass.getSimpleName());
  }

  @Nullable
  private static Method safeFindMethod(Class<?> klass, String name) {
    return ReflectionUtil.getMethod(klass, name);
  }

  private static void tryGc(int times) {
    if ((ourMode & RUN_GC) == 0) return;

    for (int i = 1; i < times; i++) {
      try {
        Thread.sleep(i * 1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }

      System.gc();

      long mem = Runtime.getRuntime().totalMemory();
      log("Runtime.getRuntime().totalMemory() = " + mem);
    }
  }

  private static void log(String message) {
    TeamCityLogger.info(message);
  }

  @SuppressWarnings({"JUnitTestCaseWithNoTests", "JUnitTestClassNamingConvention", "JUnitTestCaseWithNonTrivialConstructors"})
  private static class ExplodedBomb extends TestCase {
    private final Bombed myBombed;

    public ExplodedBomb(@NotNull String testName, @NotNull Bombed bombed) {
      super(testName);
      myBombed = bombed;
    }

    @Override
    protected void runTest() throws Throwable {
      String description = myBombed.description().isEmpty() ? "" : " (" + myBombed.description() + ")";
      fail("Bomb created by " + myBombed.user() + description + " now explodes!");
    }
  }
}
