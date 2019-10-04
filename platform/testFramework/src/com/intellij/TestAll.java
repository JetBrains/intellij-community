// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.idea.Bombed;
import com.intellij.idea.RecordExecution;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.tests.ExternalClasspathClassLoader;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashSet;
import junit.framework.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Parameterized;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.TestCaseLoader.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class TestAll implements Test {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final int MAX_FAILURE_TEST_COUNT = 150;

  private static final Filter PERFORMANCE_ONLY = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      String className = description.getClassName();
      String methodName = description.getMethodName();
      return TestFrameworkUtil.isPerformanceTest(methodName, className);
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

  public static final Filter NOT_BOMBED = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      return !isBombed(description);
    }

    @Override
    public String describe() {
      return "Not @Bombed";
    }

    private boolean isBombed(Description description) {
      Bombed bombed = description.getAnnotation(Bombed.class);
      return bombed != null && !TestFrameworkUtil.bombExplodes(bombed);
    }
  };

  private final TestCaseLoader myTestCaseLoader;
  private int myRunTests = -1;
  private TestRecorder myTestRecorder;

  private static final List<Throwable> outClassLoadingProblems = new ArrayList<>();
  private static JUnit4TestAdapterCache ourUnit4TestAdapterCache;

  public TestAll(String rootPackage) throws Throwable {
    this(rootPackage, getClassRoots());
  }

  public TestAll(String rootPackage, List<? extends File> classesRoots) throws ClassNotFoundException {
    String classFilterName = "tests/testGroups.properties";
    myTestCaseLoader = new TestCaseLoader(classFilterName);
    myTestCaseLoader.addFirstTest(Class.forName("_FirstInSuiteTest"));
    myTestCaseLoader.addLastTest(Class.forName("_LastInSuiteTest"));
    myTestCaseLoader.fillTestCases(rootPackage, classesRoots);

    outClassLoadingProblems.addAll(myTestCaseLoader.getClassLoadingErrors());
  }

  public static List<Throwable> getLoadingClassProblems() {
    return outClassLoadingProblems;
  }

  public static List<File> getClassRoots() {
    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots property: " + testRoots);
      return ContainerUtil.map(testRoots.split(";"), File::new);
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
      ClassLoader loader = TestAll.class.getClassLoader();
      if (loader instanceof URLClassLoader) {
        return getClassRoots(((URLClassLoader)loader).getURLs());
      }
      if (loader instanceof UrlClassLoader) {
        List<URL> urls = ((UrlClassLoader)loader).getBaseUrls();
        return getClassRoots(urls.toArray(new URL[0]));
      }
      return ContainerUtil.map(System.getProperty("java.class.path").split(File.pathSeparator), File::new);
    }
  }

  private static List<File> getClassRoots(URL[] urls) {
    final List<File> classLoaderRoots = ContainerUtil.map(urls, url -> new File(VfsUtilCore.urlToPath(VfsUtilCore.convertFromUrl(url))));
    System.out.println("Collecting tests from " + classLoaderRoots);
    return classLoaderRoots;
  }

  @Override
  public int countTestCases() {
    // counting test cases involves parallel directory scan now
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    int count = 0;
    for (Object aClass : myTestCaseLoader.getClasses()) {
      Test test = getTest((Class)aClass);
      if (test != null) count += test.countTestCases();
    }
    return count;
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

    // to make it easier to reproduce order-dependent failures locally
    System.out.println("------");
    System.out.println("Running tests:");
    for (Class aClass : classes) {
      System.out.println(aClass.getName());
    }
    System.out.println("------");

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
  }

  private static TestListener loadDiscoveryListener() {
    // com.intellij.InternalTestDiscoveryListener
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

    if (testResult.errorCount() + testResult.failureCount() > MAX_FAILURE_TEST_COUNT) {
      addErrorMessage(testResult, "Too many errors. Executed: " + myRunTests + " of " + totalTests);
      testResult.stop();
      return;
    }

    log("\nRunning " + testCaseClass.getName());
    Test test = getTest(testCaseClass);
    if (test == null) return;

    try {
      test.run(testResult);
    }
    catch (Throwable t) {
      testResult.addError(test, t);
    }
  }

  @Nullable
  private Test getTest(@NotNull final Class<?> testCaseClass) {
    try {
      if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) {
        return null;
      }
      Bombed classBomb = testCaseClass.getAnnotation(Bombed.class);
      if (classBomb != null && TestFrameworkUtil.bombExplodes(classBomb)) {
        return new ExplodedBomb(testCaseClass.getName(), classBomb);
      }

      Method suiteMethod = safeFindMethod(testCaseClass, "suite");
      if (suiteMethod != null && !isPerformanceTestsRun()) {
        return (Test)suiteMethod.invoke(null, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
      }

      if (TestFrameworkUtil.isJUnit4TestClass(testCaseClass, false)) {
        boolean isPerformanceTest = isPerformanceTest(null, testCaseClass);
        boolean runEverything = isIncludingPerformanceTestsRun() || isPerformanceTest && isPerformanceTestsRun();
        if (runEverything) return createJUnit4Adapter(testCaseClass);

        final RunWith runWithAnnotation = testCaseClass.getAnnotation(RunWith.class);
        if (runWithAnnotation != null && Parameterized.class.isAssignableFrom(runWithAnnotation.value())) {
          if (isPerformanceTestsRun() != isPerformanceTest) {
            // do not create JUnit4TestAdapter for @Parameterized tests to avoid @Parameters computation - just skip the test
            return null;
          }
          else {
            return createJUnit4Adapter(testCaseClass);
          }
        }

        JUnit4TestAdapter adapter = createJUnit4Adapter(testCaseClass);
        try {
          adapter.filter(NOT_BOMBED.intersect(isPerformanceTestsRun() ? PERFORMANCE_ONLY : NO_PERFORMANCE));
        }
        catch (NoTestsRemainException ignored) {
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
            if (!isIncludingPerformanceTestsRun() && (isPerformanceTestsRun() ^ isPerformanceTest(name, testCaseClass))) {
              return;
            }

            Method method = findTestMethod((TestCase)test);
            Bombed methodBomb = method == null ? null : method.getAnnotation(Bombed.class);
            if (methodBomb == null) {
              doAddTest(test);
            }
            else if (TestFrameworkUtil.bombExplodes(methodBomb)) {
              doAddTest(new ExplodedBomb(method.getDeclaringClass().getName() + "." + method.getName(), methodBomb));
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

  @NotNull
  protected JUnit4TestAdapter createJUnit4Adapter(@NotNull Class<?> testCaseClass) {
    return new JUnit4TestAdapter(testCaseClass, getJUnit4TestAdapterCache());
  }

  private static JUnit4TestAdapterCache getJUnit4TestAdapterCache() {
    if (ourUnit4TestAdapterCache == null) {
      try {
        //noinspection SpellCheckingInspection
        ourUnit4TestAdapterCache = (JUnit4TestAdapterCache)
          Class.forName("org.apache.tools.ant.taskdefs.optional.junit.CustomJUnit4TestAdapterCache")
            .getMethod("getInstance")
            .invoke(null);
      }
      catch (Exception e) {
        System.out.println("Failed to create CustomJUnit4TestAdapterCache, the default JUnit4TestAdapterCache will be used" +
                           " and ignored tests won't be properly reported: " + e.toString());
        ourUnit4TestAdapterCache = JUnit4TestAdapterCache.getDefault();
      }
    }
    return ourUnit4TestAdapterCache;
  }

  @Nullable
  private static Method safeFindMethod(Class<?> klass, String name) {
    return ReflectionUtil.getMethod(klass, name);
  }

  private static void log(String message) {
    TeamCityLogger.info(message);
  }

  @SuppressWarnings({"JUnitTestCaseWithNoTests", "JUnitTestClassNamingConvention", "JUnitTestCaseWithNonTrivialConstructors",
    "UnconstructableJUnitTestCase"})
  private static class ExplodedBomb extends TestCase {
    private final Bombed myBombed;

    ExplodedBomb(@NotNull String testName, @NotNull Bombed bombed) {
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