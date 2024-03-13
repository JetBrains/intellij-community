// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.testFramework.diagnostic.TelemetryMeterCollector;
import com.intellij.platform.testFramework.diagnostic.MetricsPublisher;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StorageLockContext;
import kotlin.reflect.KFunction;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.SupervisorKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.computeWithSpanAttribute;

public class PerformanceTestInfo {
  private enum IterationMode {
    WARMUP,
    MEASURE
  }

  private final ThrowableComputable<Integer, ?> test;   // runnable to measure; returns actual input size
  private final int expectedInputSize;                  // size of input the test is expected to process;
  private ThrowableRunnable<?> setup;                   // to run before each test
  private int maxMeasurementAttempts = 3;               // number of retries
  public final String launchName;                      // to print on fail
  private int warmupIterations = 1;                      // default warmup iterations should be positive
  private String uniqueTestName;                        // at least full qualified test name (plus other identifiers, optionally)
  @NotNull
  private final IJTracer tracer;
  private TelemetryMeterCollector meterCollector = null;

  private static final CoroutineScope coroutineScope = CoroutineScopeKt.CoroutineScope(
    SupervisorKt.SupervisorJob(null).plus(Dispatchers.getIO())
  );

  static {
    // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  private static void initOpenTelemetry() {
    // Open Telemetry file will be located at ../system/test/log/opentelemetry.json (alongside with open-telemetry-metrics.* files)
    System.setProperty("idea.diagnostic.opentelemetry.file",
                       PathManager.getLogDir().resolve("opentelemetry.json").toAbsolutePath().toString());

    var telemetryInstance = TelemetryManager.getInstance();

    // looks like telemetry manager is properly initialized
    if (telemetryInstance.hasSpanExporters()) return;

    System.err.printf(
      "%nTelemetry instance will be overriden since span exporters aren't registered. " +
      "This means your metrics (meters or spans), configured before any test execution will not be reported. " +
      "Consider using TestApplication that will setup proper instance of telemetry.%n");

    try {
      TelemetryManager.Companion.resetGlobalSdk();
      var telemetryClazz = Class.forName("com.intellij.platform.diagnostic.telemetry.impl.TelemetryManagerImpl");
      var instance = Arrays.stream(telemetryClazz.getDeclaredConstructors())
        .filter((it) -> it.getParameterCount() > 0).findFirst()
        .get()
        .newInstance(coroutineScope, true);

      TelemetryManager.Companion.forceSetTelemetryManager((TelemetryManager)instance);
    }
    catch (Throwable e) {
      System.err.println(
        "Couldn't setup TelemetryManager without TestApplication. Either test should use TestApplication or somewhere is a bug");
      e.printStackTrace();
    }
  }

  private static void cleanupOutdatedMeters() {
    try {
      // force spans and meters to be written to disk before any test starts
      // it's at least what we can do to minimize interference of the same meter on different tests
      TelemetryManager.getInstance().forceFlushMetricsBlocking();

      // remove content of the previous tests from the idea.log
      MetricsPublisher.Companion.truncateTestLog();

      var filesWithMetrics = Files.list(PathManager.getLogDir()).filter((it) ->
                                                                          it.toString().contains("-metrics") ||
                                                                          it.toString().contains("-meters")).toList();
      for (Path file : filesWithMetrics) {
        Files.deleteIfExists(file);
      }
    }
    catch (Exception e) {
      System.err.println(
        "Error during removing Telemetry files with meters before start of perf test. This might affect collected metrics value.");
      e.printStackTrace();
    }
  }

  PerformanceTestInfo(@NotNull ThrowableComputable<Integer, ?> test, int expectedInputSize, @NotNull String launchName) {
    initOpenTelemetry();
    cleanupOutdatedMeters();

    this.test = test;
    this.expectedInputSize = expectedInputSize;
    assert expectedInputSize > 0 : "Expected input size must be > 0. Was: " + expectedInputSize;
    this.launchName = launchName;
    this.tracer = TelemetryManager.getInstance().getTracer(new Scope("performanceUnitTests", null));
  }

  @Contract(pure = true) // to warn about not calling .start() in the end
  public PerformanceTestInfo setup(@NotNull ThrowableRunnable<?> setup) {
    assert this.setup == null;
    this.setup = setup;
    return this;
  }

  @Contract(pure = true) // to warn about not calling .start() in the end
  public PerformanceTestInfo attempts(int attempts) {
    this.maxMeasurementAttempts = attempts;
    return this;
  }

  /**
   * Instruct to publish Telemetry meters (that is stored in .csv files)
   * Eg:
   * <pre>
   *   {@code
   *     val counter: AtomicLong = AtomicLong()
   *     val counterMeter = TelemetryManager.getMeter(MY_SCOPE)
   *       .counterBuilder("custom.counter")
   *       .buildWithCallback { it.record(counter.get()) }
   *
   *     val meterCollector = TelemetryMeterCollector(MetricsAggregation.SUM) { it.key.contains("custom") }
   *
   *     PlatformTestUtil.newPerformanceTest("my perf test") {
   *       counter.incrementAndGet()
   *     }
   *       .withTelemetryMeters(meterCollector)
   *       .start()}
   * </pre>
   */
  @Contract(pure = true) // to warn about not calling .start() in the end
  public PerformanceTestInfo withTelemetryMeters(TelemetryMeterCollector meterCollector) {
    this.meterCollector = meterCollector;
    return this;
  }

  /**
   * Runs the perf test {@code iterations} times before starting the final measuring.
   */
  @Contract(pure = true) // to warn about not calling .start() in the end
  public PerformanceTestInfo warmupIterations(int iterations) {
    warmupIterations = iterations;
    return this;
  }

  public String getUniqueTestName() {
    return uniqueTestName;
  }

  private static Method filterMethodFromStackTrace(Function<Method, Boolean> methodFilter) {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

    for (StackTraceElement element : stackTraceElements) {
      try {
        Method foundMethod = ContainerUtil.find(
          Class.forName(element.getClassName()).getDeclaredMethods(),
          method -> method.getName().equals(element.getMethodName()) && methodFilter.apply(method)
        );
        if (foundMethod != null) return foundMethod;
      }
      catch (ClassNotFoundException e) {
        // do nothing, continue
      }
    }
    return null;
  }

  private static Method tryToFindCallingTestMethodByJUnitAnnotation() {
    return filterMethodFromStackTrace(
      method -> ContainerUtil.exists(method.getDeclaredAnnotations(), annotation -> annotation.annotationType().getName().contains("junit"))
    );
  }

  private static Method tryToFindCallingTestMethodByNamePattern() {
    return filterMethodFromStackTrace(method -> method.getName().toLowerCase(Locale.ROOT).startsWith("test"));
  }

  private static Method getCallingTestMethod() {
    Method callingTestMethod = tryToFindCallingTestMethodByJUnitAnnotation();

    if (callingTestMethod == null) {
      callingTestMethod = tryToFindCallingTestMethodByNamePattern();
      if (callingTestMethod == null) {
        throw new AssertionError(
          "Couldn't manage to detect the calling test method. Please use one of the overloads of com.intellij.testFramework.PerformanceTestInfo.start"
        );
      }
    }

    return callingTestMethod;
  }

  /**
   * Start execution of the performance test.
   * <br/>
   * Default pipeline:
   * <ul>
   *     <li>executes warmup phase - run the perf test {@link #warmupIterations} times before final measurements</li>
   *     <li>executes perf test with final measurements {@link #maxMeasurementAttempts} times</li>
   *     <li>publish metrics and artifacts(logs, etc.) in {@code ./system/teamcity-artifacts-for-publish/}</li>
   * </ul>
   * <br/>
   * By default only OpenTelemetry spans will be published. (from the {@code ./system/test/log/opentelemtry.json} file).<br/>
   * To enable publishing of meters (from the {@code ./system/test/log/open-telemetry-metrics.*.csv}) use {@link #withTelemetryMeters(OpenTelemetryMeterCollector)}. <br/>
   * <p/>
   * Considering metrics: better to have a test that produces metrics in seconds, rather milliseconds.<br/>
   * This way degradation will be easier to detect and metric deviation from the baseline will be easier to notice.
   * <p/>
   * On TeamCity all unit performance tests run as
   * <a href="https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_Idea_Tests_PerformanceTests?branch=&buildTypeTab=overview&mode=builds">the composite build</a>
   * <br/>
   * Raw metrics are reported as TC artifacts and can be found on Artifacts tqb in dependency builds.<br/>
   * Human friendly metrics representation can be viewed in <a href="https://ij-perf.labs.jb.gg/perfUnit/tests?machine=linux-blade-hetzner&branch=master">IJ Perf</a><br/>
   * Last but not least: if metrics arent published or even not collected - probably TelemetryManager isntance isn't initialized correctly
   * or dependency on module intellij.tools.ide.metrics.benchmark isn't set.
   *
   * @see #start(String)
   * @see #start(Method)
   * @see #start(kotlin.reflect.KFunction)
   * @see #startAsSubtest(String)
   **/
  public void start() {
    start(getCallingTestMethod(), launchName);
  }

  /**
   * Start the perf test where the test's artifact path will have a name inferred from test method + subtest name.
   *
   * @see PerformanceTestInfo#start()
   * @see PerformanceTestInfo#startAsSubtest(String)
   * @see PerformanceTestInfo#start(kotlin.reflect.KFunction)
   **/
  public void start(@NotNull Method javaTestMethod, String subTestName) {
    var fullTestName = String.format("%s.%s", javaTestMethod.getDeclaringClass().getName(), javaTestMethod.getName());
    if (subTestName != null && !subTestName.isEmpty()) {
      fullTestName += " - " + subTestName;
    }
    start(fullTestName);
  }

  /**
   * Start the perf test where test artifact path will have a name inferred from test method.
   * Useful in parametrized tests.
   * <br/>
   * Eg: <code>start(GradleHighlightingPerformanceTest::testCompletionPerformance)</code>
   *
   * @see PerformanceTestInfo#start(Method)
   * @see PerformanceTestInfo#start(String)
   */
  public void start(@NotNull KFunction<?> kotlinTestMethod) {
    start(String.format("%s.%s", kotlinTestMethod.getClass().getName(), kotlinTestMethod.getName()));
  }

  /**
   * Use it if you need to run many subsequent performance tests in your JUnit test.<br/>
   * Each subtest should have unique name, otherwise published results would be overwritten.<br/>
   * <br/>
   * By default passed test launch name will be used as the subtest name.<br/>
   *
   * @see PerformanceTestInfo#startAsSubtest(String)
   */
  public void startAsSubtest() {
    startAsSubtest(launchName);
  }

  /**
   * The same as {@link #startAsSubtest()} but with the option to specify subtest name.
   */
  public void startAsSubtest(@Nullable String subTestName) {
    start(getCallingTestMethod(), subTestName);
  }

  /**
   * Start execution of the performance test.
   *
   * @param fullQualifiedTestMethodName String representation of full method name.
   *                                    For Java you can use {@link com.intellij.testFramework.UsefulTestCase#getQualifiedTestMethodName()}
   *                                    OR
   *                                    {@link com.intellij.testFramework.fixtures.BareTestFixtureTestCase#getQualifiedTestMethodName()}
   * @see PerformanceTestInfo#start()
   */
  public void start(String fullQualifiedTestMethodName) {
    start(IterationMode.WARMUP, fullQualifiedTestMethodName);
    start(IterationMode.MEASURE, fullQualifiedTestMethodName);
  }

  /**
   * @param uniqueTestName - should be at least full qualified test method name.
   *                       Sometimes additional suffixes might be added like here {@link PerformanceTestInfo#startAsSubtest(String)}
   */
  private void start(IterationMode iterationType, String uniqueTestName) {
    this.uniqueTestName = uniqueTestName;

    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;
    System.out.printf("Starting performance test \"%s\" in mode: %s%n", uniqueTestName, iterationType);

    int maxIterationsNumber;
    if (iterationType.equals(IterationMode.WARMUP)) {
      maxIterationsNumber = warmupIterations;
    }
    else {
      maxIterationsNumber = maxMeasurementAttempts;
    }

    if (maxIterationsNumber == 1) {
      //noinspection CallToSystemGC
      System.gc();
    }

    try {
      computeWithSpanAttribute(tracer, uniqueTestName, "warmup", (st) -> String.valueOf(iterationType.equals(IterationMode.WARMUP)), () -> {
        try {
          PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();

          for (int attempt = 1; attempt <= maxIterationsNumber; attempt++) {
            AtomicInteger actualInputSize;

            if (setup != null) setup.run();
            actualInputSize = new AtomicInteger(expectedInputSize);

            Supplier<Object> perfTestWorkload = getPerfTestWorkloadSupplier(iterationType, attempt, actualInputSize);

            computeWithSpanAttribute(
              tracer, "Attempt: " + attempt,
              "warmup",
              (st) -> String.valueOf(iterationType.equals(IterationMode.WARMUP)),
              () -> perfTestWorkload.get()
            );

            if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
              // TODO: Print debug metrics here https://youtrack.jetbrains.com/issue/AT-726
            }
            //noinspection CallToSystemGC
            System.gc();
            StorageLockContext.forceDirectMemoryCache();
          }
        }
        catch (Throwable throwable) {
          ExceptionUtil.rethrowUnchecked(throwable);
          throw new RuntimeException(throwable);
        }

        return null;
      });
    }
    finally {
      try {
        // publish warmup and final measurements at once at the end of the runs
        if (iterationType.equals(IterationMode.MEASURE)) {
          var publisherInstance = MetricsPublisher.Companion.getInstance();
          if (meterCollector != null) {
            publisherInstance.publishSync(uniqueTestName, meterCollector);
          }
          else {
            publisherInstance.publishSync(uniqueTestName);
          }
        }
      }
      catch (Throwable t) {
        System.err.println("Something unexpected happened during publishing performance metrics");
        throw t;
      }
    }
  }

  private @NotNull Supplier<Object> getPerfTestWorkloadSupplier(IterationMode iterationType, int attempt, AtomicInteger actualInputSize) {
    return () -> {
      try {
        Profiler.startProfiling(iterationType.name() + attempt);
        actualInputSize.set(test.compute());
      }
      catch (Throwable e) {
        ExceptionUtil.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
      finally {
        Profiler.stopProfiling();
      }

      return null;
    };
  }

  private static final class Profiler {
    private static final ProfilerForTests profiler = getProfilerInstance();

    private static ProfilerForTests getProfilerInstance() {
      ServiceLoader<ProfilerForTests> loader = ServiceLoader.load(ProfilerForTests.class);
      for (ProfilerForTests service : loader) {
        if (service != null) {
          return service;
        }
      }
      System.out.println("No service com.intellij.testFramework.Profiler is found in class path");
      return null;
    }

    public static void stopProfiling() {
      if (profiler != null) {
        try {
          profiler.stopProfiling();
        }
        catch (IOException e) {
          System.out.println("Can't stop profiling");
        }
      }
    }

    public static void startProfiling(String fileName) {
      Path logDir = PathManager.getLogDir();
      if (profiler != null) {
        try {
          profiler.startProfiling(logDir, fileName);
        }
        catch (IOException e) {
          System.out.println("Can't start profiling");
        }
      }
    }
  }
}
