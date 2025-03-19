// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.idea.IJIgnore;
import com.intellij.idea.IgnoreJUnit3;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
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
import org.jetbrains.annotations.Unmodifiable;
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

  private static final Filter NOT_IGNORED = new Filter() {
    @Override
    public boolean shouldRun(Description description) {
      return description.getAnnotation(IgnoreJUnit3.class) == null && description.getAnnotation(IJIgnore.class) == null;
    }

    @Override
    public String describe() {
      return "Not @IgnoreJUnit3";
    }
  };

  private final TestCaseLoader myTestCaseLoader;
  private int myRunTests = -1;
  private int myIgnoredTests;

  private static final List<Throwable> ourClassLoadingProblems = new ArrayList<>();
  private static JUnit4TestAdapterCache ourUnit4TestAdapterCache;
  private static final String ourCollectTestsFile = System.getProperty("intellij.build.test.list.classes", null);

  public TestAll(String rootPackage) throws Throwable {
    this(rootPackage, getClassRoots());
  }

  public TestAll(String rootPackage, List<? extends Path> classesRoots) throws ClassNotFoundException {
    myTestCaseLoader = Builder.fromDefaults().build();
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

  public static @Unmodifiable List<Path> getClassRoots() {
    return TeamCityLogger.block("Collecting tests from ...", () -> {
      return doGetClassRoots();
    });
  }

  private static @Unmodifiable List<Path> doGetClassRoots() {
    String jarsToRunTestsFrom = System.getProperty("jar.dependencies.to.tests");
    if (jarsToRunTestsFrom != null) {
      String[] jars = jarsToRunTestsFrom.split(";");
      List<Path> classpath = Objects.requireNonNull(ExternalClasspathClassLoader.getRoots());
      List<Path> testPaths = Arrays.stream(jars)
        .map(jarName -> {
               List<? extends Path> resultJars = ContainerUtil.filter(classpath, path -> path.getFileName().toString().startsWith(jarName));
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
    if (!hasRealTests()) {
      return 0;
    }
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
    if (!hasRealTests()) {
      return;
    }
    final TestListener testListener = loadDiscoveryListener();
    if (testListener != null) {
      testResult.addListener(testListener);
    }
    final OutOfProcessRetries.OutOfProcessRetryListener outOfProcessRetryListener = OutOfProcessRetries.getListenerForOutOfProcessRetry();
    if (outOfProcessRetryListener != null) {
      testResult.addListener(outOfProcessRetryListener);
    }

    testResult = RetriesImpl.maybeEnable(testResult);

    List<Class<?>> classes = myTestCaseLoader.getClasses();

    // to make it easier to reproduce order-dependent failures locally
    final List<Class<?>> testsToRun = new ArrayList<>(classes.size());
    for (Class<?> aClass : classes) {
      // Eagerly tests initialization may create problems, so we use a simplified version of `getTest`.
      if (isPotentiallyATest(aClass)) {
        testsToRun.add(aClass);
      }
    }
    System.out.println("------");
    System.out.println("Running tests classes (list may contain classes which will not be actually run):");
    for (Class<?> aClass : testsToRun) {
      System.out.println(aClass.getName());
    }
    System.out.println("------");
    dumpSuite(testsToRun);
    System.out.println("------");

    int totalTests = classes.size();
    final List<String> collectedTests = ourCollectTestsFile != null ? new ArrayList<>(totalTests) : null;
    for (Class<?> aClass : testsToRun) {
      runOrCollectNextTest(testResult, totalTests, aClass, collectedTests);
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
    if (outOfProcessRetryListener != null) {
      try {
        outOfProcessRetryListener.save();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (collectedTests != null) {
      Path path = Path.of(ourCollectTestsFile);
      try {
        collectedTests.remove("_FirstInSuiteTest");
        collectedTests.remove("_LastInSuiteTest");
        Files.createDirectories(path.getParent());
        Files.write(path, collectedTests);
      }
      catch (IOException e) {
        System.err.printf("Cannot save list of test classes to '%s': %s%n", path.toAbsolutePath(), e);
        e.printStackTrace();
      }
    }

    TestCaseLoader.sendTestRunResultsToNastradamus();
  }

  private static void dumpSuite(List<Class<?>> testsToRun) {
    try {
      File suite = FileUtil.createTempFile("TestAllSuite", ".java");
      String suiteName = FileUtil.getNameWithoutExtension(suite);
      StringBuilder sb = new StringBuilder();
      sb.append("import org.junit.runner.RunWith;\n");
      sb.append("import org.junit.runners.Suite;\n");
      sb.append("@RunWith(Suite.class)\n");
      sb.append("@Suite.SuiteClasses({\n");
      for (Class<?> aClass : testsToRun) {
        String name = aClass.getName();
        sb.append("  ").append(name.replace('$', '.')).append(".class,\n");
      }
      sb.append("})\n");
      sb.append("public class ").append(suiteName).append(" {}\n");
      FileUtil.writeToFile(suite, sb.toString());
      if (TeamCityLogger.isUnderTC) {
        System.out.println("Generated suite file: '" + suite.getName() + "'. Could be found in 'suites' artifacts directory");
        System.out.println("##teamcity[publishArtifacts '" + suite.getAbsolutePath() + "=>suites/']");
      }
      else {
        System.out.println("Generated suite file: " + suite.getAbsolutePath());
      }
      System.out.println("Place it in `tests/integration/testSrc/` or similar directory");
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot dump test suite for reproducibility", e);
    }
  }

  private boolean hasRealTests() {
    return ContainerUtil.exists(myTestCaseLoader.getClasses(false), aClass -> getTest(aClass) != null);
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

  private static boolean shouldAddFirstAndLastTests() {
    return !"true".equals(System.getProperty("intellij.build.test.ignoreFirstAndLastTests"));
  }

  private void runOrCollectNextTest(final @NotNull TestResult testResult,
                                    int totalTests,
                                    @NotNull Class<?> testCaseClass,
                                    @Nullable List<String> collectedTests) {
    myRunTests++;

    int errorCount = testResult.errorCount();
    int count = errorCount + testResult.failureCount() - myIgnoredTests;
    if (count > MAX_FAILURE_TEST_COUNT && MAX_FAILURE_TEST_COUNT >= 0) {
      addErrorMessage(testResult, "Too many errors (" + count + ", MAX_FAILURE_TEST_COUNT = " + MAX_FAILURE_TEST_COUNT +
                                  "). Executed: " + myRunTests + " of " + totalTests);
      testResult.stop();
      return;
    }

    String caseClassName = testCaseClass.getName();
    Test test = getTest(testCaseClass);
    if (test == null) {
      log("\nSkipping " + caseClassName + ": no Test detected");
      return;
    }
    log("\nRunning " + caseClassName);

    if (collectedTests != null) {
      collectedTests.add(caseClassName);
      return;
    }

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

  private @Nullable Test getTest(final @NotNull Class<?> testCaseClass) {
    try {
      if (!Modifier.isPublic(testCaseClass.getModifiers())) {
        return null;
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
          adapter.filter(NOT_IGNORED.intersect(isPerformanceTestsRun() ? PERFORMANCE_ONLY : NO_PERFORMANCE));
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

            if (method != null && (method.getAnnotation(IgnoreJUnit3.class) != null || method.getAnnotation(IJIgnore.class) != null)) {
              return;
            }
            doAddTest(test);
          }
        }

        private void doAddTest(Test test) {
          testsCount[0]++;
          super.addTest(test);
        }

        private static @Nullable Method findTestMethod(final TestCase testCase) {
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

  private static boolean isPotentiallyATest(final @NotNull Class<?> testCaseClass) {
    try {
      if (!Modifier.isPublic(testCaseClass.getModifiers())) {
        return false;
      }

      if (safeFindMethod(testCaseClass, "suite") != null && !isPerformanceTestsRun()) {
        return true;
      }

      // Maybe JUnit 4 test?
      if (TestFrameworkUtil.isJUnit4TestClass(testCaseClass, false)) {
        boolean isPerformanceTest = isPerformanceTest(null, testCaseClass.getSimpleName());
        boolean runEverything = isIncludingPerformanceTestsRun() || isPerformanceTest && isPerformanceTestsRun();
        if (runEverything) return true;

        final RunWith runWithAnnotation = testCaseClass.getAnnotation(RunWith.class);
        if (runWithAnnotation != null && Parameterized.class.isAssignableFrom(runWithAnnotation.value())) {
          if (isPerformanceTestsRun() != isPerformanceTest) {
            // do not create JUnit4TestAdapter for @Parameterized tests to avoid @Parameters computation - just skip the test
            return false;
          }
        }
        return true;
      }

      // Maybe JUnit 3 test?
      // Simplified version of `junit.framework.TestSuite.addTestsFromTestCase`
      try {
        if (TestSuite.getTestConstructor(testCaseClass) == null) return false;
      }
      catch (NoSuchMethodException e) {
        return false;
      }
      return Test.class.isAssignableFrom(testCaseClass);
    }
    catch (Throwable t) {
      System.err.println("Failed to load test: " + testCaseClass.getName());
      t.printStackTrace(System.err);
      return false;
    }
  }

  protected @NotNull JUnit4TestAdapter createJUnit4Adapter(@NotNull Class<?> testCaseClass) {
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

  private static @Nullable Method safeFindMethod(Class<?> klass, String name) {
    return ReflectionUtil.getMethod(klass, name);
  }

  private static void log(@NotNull String message) {
    TeamCityLogger.info(message);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}