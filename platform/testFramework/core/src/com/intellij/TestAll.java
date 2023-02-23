// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.idea.Bombed;
import com.intellij.idea.RecordExecution;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TeamCityLogger;
import com.intellij.testFramework.TestFrameworkUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.tests.ExternalClasspathClassLoader;
import com.intellij.tests.IgnoreException;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.UrlClassLoader;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.TestCaseLoader.*;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class TestAll implements Test {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final String MAX_FAILURE_TEST_COUNT_FLAG = "idea.max.failure.test.count";

  private static final int MAX_FAILURE_TEST_COUNT = Integer.parseInt(Objects.requireNonNullElse(
    System.getProperty(MAX_FAILURE_TEST_COUNT_FLAG),
    "150"
  ));

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
  private int myIgnoredTests;
  private TestRecorder myTestRecorder;

  private static final List<Throwable> ourClassLoadingProblems = new ArrayList<>();
  private static JUnit4TestAdapterCache ourUnit4TestAdapterCache;

  public TestAll(String rootPackage) throws Throwable {
    this(rootPackage, getClassRoots());
  }

  public TestAll(String rootPackage, List<? extends Path> classesRoots) throws ClassNotFoundException {
    String classFilterName = "tests/testGroups.properties";
    myTestCaseLoader = new TestCaseLoader(classFilterName);
    if (shouldAddFirstAndLastTests()) {
      myTestCaseLoader.addFirstTest(Class.forName("_FirstInSuiteTest"));
      myTestCaseLoader.addLastTest(Class.forName("_LastInSuiteTest"));
    }
    myTestCaseLoader.fillTestCases(rootPackage, classesRoots);

    ourClassLoadingProblems.addAll(myTestCaseLoader.getClassLoadingErrors());
  }

  public static List<Throwable> getLoadingClassProblems() {
    return ourClassLoadingProblems;
  }

  public static List<Path> getClassRoots() {
    return TeamCityLogger.block("Collecting tests from ...", () -> {
      return doGetClassRoots();
    });
  }

  private static List<Path> doGetClassRoots() {
    String jarsToRunTestsFrom = System.getProperty("jar.dependencies.to.tests");
    if (jarsToRunTestsFrom != null) {
      String[] jars = jarsToRunTestsFrom.split(";");
      List<Path> classpath = Objects.requireNonNull(ExternalClasspathClassLoader.getRoots());
      List<Path> testPaths = Arrays.stream(jars)
        .map(jarName -> {
               List<Path> resultJars = ContainerUtil.filter(classpath, path -> path.getFileName().toString().startsWith(jarName));
               if (resultJars.size() != 1) {
                 String classpathPretty = classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
                 throw new IllegalStateException(
                   (resultJars.isEmpty() ? "Cannot find " : "More than one ") + jarName + " in " + classpathPretty
                 );
               }

               return resultJars.get(0);
             }
        )
        .map(Path::normalize)
        .map(jar -> {
          try {
            if (!Files.exists(jar)) {
              throw new IllegalStateException(jar + " doesn't exist");
            }

            String jarNameWithoutExtension = StringUtil.substringBefore(jar.getFileName().toString(), ".");
            Path out = Paths.get(PathManager.getHomePath(), "out", "jar-dependencies-to-test", jarNameWithoutExtension);
            new Decompressor.Zip(jar).extract(out);
            return out;
          }
          catch (IOException e) {
            throw new IllegalStateException(e);
          }
        })
        .collect(Collectors.toList());

      System.out.println("Collecting tests from roots specified by jar.dependencies.to.tests property: " + testPaths);
      return testPaths;
    }

    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots property: " + testRoots);
      return ContainerUtil.map(testRoots.split(";"), Paths::get);
    }
    List<Path> roots = ExternalClasspathClassLoader.getRoots();
    if (roots != null) {
      List<Path> excludeRoots = ExternalClasspathClassLoader.getExcludeRoots();
      if (excludeRoots != null) {
        System.out.println("Skipping tests from " + excludeRoots.size() + " roots");
        roots = new ArrayList<>(roots);
        roots.removeAll(FileCollectionFactory.createCanonicalPathSet(excludeRoots));
      }

      System.out.println("Collecting tests from roots specified by classpath.file property: " + roots);
      return roots;
    }
    else {
      ClassLoader loader = TestAll.class.getClassLoader();
      if (loader instanceof URLClassLoader) {
        return ContainerUtil.map(getClassRoots(((URLClassLoader)loader).getURLs()), url -> Paths.get(url.toUri()));
      }
      if (loader instanceof UrlClassLoader) {
        List<Path> urls = ((UrlClassLoader)loader).getBaseUrls();
        System.out.println("Collecting tests from " + urls);
        return urls;
      }
      return ContainerUtil.map(System.getProperty("java.class.path").split(File.pathSeparator), Paths::get);
    }
  }

  private static List<Path> getClassRoots(URL[] urls) {
    List<Path> classLoaderRoots = ContainerUtil.map(urls, url -> {
      try {
        return Paths.get(url.toURI());
      }
      catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    });
    System.out.println("Collecting tests from " + classLoaderRoots);
    return classLoaderRoots;
  }

  @Override
  public int countTestCases() {
    // counting test cases involves parallel directory scan now
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    int count = 0;
    for (Class<?> aClass : myTestCaseLoader.getClasses()) {
      Test test = getTest(aClass);
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
  public void run(TestResult testResult) {
    loadTestRecorder();

    final TestListener testListener = loadDiscoveryListener();
    if (testListener != null) {
      testResult.addListener(testListener);
    }

    testResult = RetriesImpl.maybeEnable(testResult);

    List<Class<?>> classes = myTestCaseLoader.getClasses();

    // to make it easier to reproduce order-dependent failures locally
    System.out.println("------");
    System.out.println("Running tests classes:");
    for (Class<?> aClass : classes) {
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

    TestCaseLoader.sendTestRunResultsToNastradamus();
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

  private static boolean shouldAddFirstAndLastTests() {
    return !"true".equals(System.getProperty("intellij.build.test.ignoreFirstAndLastTests"));
  }

  private void loadTestRecorder() {
    String recorderClassName = System.getProperty("test.recorder.class");
    if (recorderClassName != null) {
      try {
        Class<?> recorderClass = Class.forName(recorderClassName);
        myTestRecorder = (TestRecorder)recorderClass.newInstance();
      }
      catch (Exception e) {
        System.out.println("Error loading test recorder class '" + recorderClassName + "': " + e);
      }
    }
  }

  private void runNextTest(final TestResult testResult, int totalTests, Class<?> testCaseClass) {
    myRunTests++;

    int errorCount = testResult.errorCount();
    int count = errorCount + testResult.failureCount() - myIgnoredTests;
    if (count > MAX_FAILURE_TEST_COUNT && MAX_FAILURE_TEST_COUNT >= 0) {
      addErrorMessage(testResult, "Too many errors (" + count + ", MAX_FAILURE_TEST_COUNT = " + MAX_FAILURE_TEST_COUNT +
                                  "). Executed: " + myRunTests + " of " + totalTests);
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

    if (testResult.errorCount() > errorCount) {
      Enumeration<TestFailure> errors = testResult.errors();
      while (errors.hasMoreElements()) {
        TestFailure failure = errors.nextElement();
        if (errorCount-- > 0) continue;
        if (IgnoreException.isIgnoringThrowable(failure.thrownException())) {
          myIgnoredTests++;
        }
      }
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
        boolean isPerformanceTest = isPerformanceTest(null, testCaseClass.getSimpleName());
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
            if (!isIncludingPerformanceTestsRun() && (isPerformanceTestsRun() ^ isPerformanceTest(name, testCaseClass.getSimpleName()))) {
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
        private static Method findTestMethod(final TestCase testCase) {
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
      JUnit4TestAdapterCache cache;
      if ("junit5".equals(System.getProperty("intellij.build.test.runner"))) {
        try {
          cache = (JUnit4TestAdapterCache)Class.forName("com.intellij.tests.JUnit5TeamCityRunnerForTestAllSuite")
            .getMethod("createJUnit4TestAdapterCache")
            .invoke(null);
        }
        catch (Throwable e) {
          cache = JUnit4TestAdapterCache.getDefault();
        }
      }
      else {
        try {
          //noinspection SpellCheckingInspection
          cache = (JUnit4TestAdapterCache)
            Class.forName("org.apache.tools.ant.taskdefs.optional.junit.CustomJUnit4TestAdapterCache")
              .getMethod("getInstance")
              .invoke(null);
        }
        catch (Exception e) {
          System.out.println("Failed to create CustomJUnit4TestAdapterCache, the default JUnit4TestAdapterCache will be used" +
                             " and ignored tests won't be properly reported: " + e);
          cache = JUnit4TestAdapterCache.getDefault();
        }
      }
      ourUnit4TestAdapterCache = RetriesImpl.maybeEnable(cache);
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

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}