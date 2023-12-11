// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.NoopTelemetryManager;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.computeWithSpanAttribute;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.computeWithSpanAttributes;

public class PerformanceTestInfo {
  private record IterationStatus(@NotNull IterationResult iterationResult,
                                 boolean passed,
                                 @NotNull String logMessage) {
  }

  private enum IterationMode {
    WARMUP,
    MEASURE
  }

  private final ThrowableComputable<Integer, ?> test; // runnable to measure; returns actual input size
  private final int expectedMs;           // millis the test is expected to run
  private final int expectedInputSize;    // size of input the test is expected to process;
  private ThrowableRunnable<?> setup;      // to run before each test
  private int usedReferenceCpuCores = 1;
  private int maxMeasurementAttempts = 3;             // number of retries
  private final String launchName;         // to print on fail
  private boolean adjustForIO;// true if test uses IO, timings need to be re-calibrated according to this agent disk performance
  private boolean adjustForCPU = true;  // true if test uses CPU, timings need to be re-calibrated according to this agent CPU speed
  private boolean useLegacyScaling;
  private int warmupIterations = 1; // default warmup iterations should be positive
  @NotNull
  private final IJTracer tracer;

  private static final CoroutineScope coroutineScope = CoroutineScopeKt.CoroutineScope(
    SupervisorKt.SupervisorJob(null).plus(Dispatchers.getIO())
  );

  static {
    // to use JobSchedulerImpl.getJobPoolParallelism() in tests which don't init application
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
  }

  /** In case if perf tests don't use Test Application we need to initialize OpenTelemetry without Application */
  private static void initOpenTelemetryIfNeeded() {
    // Open Telemetry file will be located at ../system/test/log/opentelemetry.json (alongside with open-telemetry-metrics.*.csv)
    System.setProperty("idea.diagnostic.opentelemetry.file",
                       PathManager.getLogDir().resolve("opentelemetry.json").toAbsolutePath().toString());

    var telemetryInstance = TelemetryManager.getInstance();

    var isNoop = telemetryInstance instanceof NoopTelemetryManager;
    // looks like telemetry manager is properly initialized
    if (!isNoop) return;

    try {
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

  PerformanceTestInfo(@NotNull ThrowableComputable<Integer, ?> test, int expectedMs, int expectedInputSize, @NotNull String launchName) {
    initOpenTelemetryIfNeeded();

    this.test = test;
    this.expectedMs = expectedMs;
    this.expectedInputSize = expectedInputSize;
    assert expectedMs > 0 : "Expected must be > 0. Was: " + expectedMs;
    assert expectedInputSize > 0 : "Expected input size must be > 0. Was: " + expectedInputSize;
    this.launchName = launchName;
    this.tracer = TelemetryManager.getInstance().getTracer(new Scope("performanceUnitTests", null));
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo setup(@NotNull ThrowableRunnable<?> setup) {
    assert this.setup == null;
    this.setup = setup;
    return this;
  }

  /**
   * Invoke this method if and only if the code under performance tests is using all CPU cores.
   * The "standard" expected time then should be given for a machine which has 8 CPU cores.
   * Actual test expected time will be adjusted according to the number of cores the actual computer has.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo usesAllCPUCores() { return usesMultipleCPUCores(8); }

  /**
   * Invoke this method if and only if the code under performance tests is using {@code maxCores} CPU cores (or fewer if the computer has less than {@code maxCores} cores).
   * The "standard" expected time then should be given for a machine which has {@code maxCores} CPU cores.
   * Actual test expected time will be adjusted according to the number of cores the actual computer has.
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo usesMultipleCPUCores(int maxCores) {
    assert adjustForCPU : "This test configured to be io-bound, it cannot use all cores";
    usedReferenceCpuCores = maxCores;
    return this;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo ioBound() {
    adjustForIO = true;
    adjustForCPU = false;
    return this;
  }

  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo attempts(int attempts) {
    this.maxMeasurementAttempts = attempts;
    return this;
  }

  /**
   * Runs the payload {@code iterations} times before starting measuring the time.
   * By default, iterations == 0 (in which case we don't run warmup passes at all)
   */
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo warmupIterations(int iterations) {
    warmupIterations = iterations;
    return this;
  }

  /**
   * @deprecated Enables procedure for nonlinear scaling of results between different machines. This was historically enabled, but doesn't
   * seem to be meaningful, and is known to make results worse in some cases. Consider migration off this setting, recalibrating
   * expected execution time accordingly.
   */
  @Deprecated
  @Contract(pure = true) // to warn about not calling .assertTiming() in the end
  public PerformanceTestInfo useLegacyScaling() {
    useLegacyScaling = true;
    return this;
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
          "Couldn't manage to detect the calling test method. Please use one of the overloads of com.intellij.testFramework.PerformanceTestInfo.assertTiming"
        );
      }
    }

    return callingTestMethod;
  }

  /** @see PerformanceTestInfo#assertTiming(String) */
  public void assertTiming() {
    assertTiming(getCallingTestMethod());
  }

  public void assertTiming(@NotNull Method javaTestMethod) {
    assertTiming(javaTestMethod, "");
  }

  public void assertTiming(@NotNull Method javaTestMethod, String subTestName) {
    var fullTestName = String.format("%s.%s", javaTestMethod.getDeclaringClass().getName(), javaTestMethod.getName());
    if (subTestName != null && !subTestName.isEmpty()) {
      fullTestName += " - " + subTestName;
    }
    assertTiming(fullTestName);
  }

  /**
   * {@link PerformanceTestInfo#assertTiming(String)}
   * <br/>
   * Eg: <code>assertTiming(GradleHighlightingPerformanceTest::testCompletionPerformance)</code>
   */
  public void assertTiming(@NotNull KFunction<?> kotlinTestMethod) {
    assertTiming(String.format("%s.%s", kotlinTestMethod.getClass().getName(), kotlinTestMethod.getName()));
  }

  /**
   * By default passed test launch name will be used as the subtest name.
   *
   * @see PerformanceTestInfo#assertTimingAsSubtest(String)
   */
  public void assertTimingAsSubtest() {
    assertTimingAsSubtest(launchName);
  }

  /**
   * In case if you want to run many subsequent performance measurements in your JUnit test.
   *
   * @see PerformanceTestInfo#assertTiming(String)
   */
  public void assertTimingAsSubtest(@Nullable String subTestName) {
    assertTiming(getCallingTestMethod(), subTestName);
  }

  /**
   * Asserts expected timing.
   *
   * @param fullQualifiedTestMethodName String representation of full method name.
   *                                    For Java you can use {@link com.intellij.testFramework.UsefulTestCase#getQualifiedTestMethodName()}
   *                                    OR
   *                                    {@link com.intellij.testFramework.fixtures.BareTestFixtureTestCase#getQualifiedTestMethodName()}
   */
  public void assertTiming(String fullQualifiedTestMethodName) {
    assertTiming(IterationMode.WARMUP, fullQualifiedTestMethodName);
    assertTiming(IterationMode.MEASURE, fullQualifiedTestMethodName);
  }

  private void assertTiming(IterationMode iterationType, String fullQualifiedTestMethodName) {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;
    System.out.printf("Starting performance test in mode: %s%n", iterationType);

    Timings.getStatistics(); // warm-up, measure
    updateJitUsage();

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
      computeWithSpanAttribute(tracer, launchName, "warmup", (st) -> String.valueOf(iterationType.equals(IterationMode.WARMUP)), () -> {
        try {
          for (int attempt = 1; attempt <= maxIterationsNumber; attempt++) {
            AtomicInteger actualInputSize;

            if (setup != null) setup.run();
            PlatformTestUtil.waitForAllBackgroundActivityToCalmDown();
            actualInputSize = new AtomicInteger(expectedInputSize);

            int iterationNumber = attempt;
            Supplier<IterationStatus> operation = () -> {
              CpuUsageData currentData;
              try {
                AsyncProfiler.startProfiling(PathManager.getLogDir().resolve(iterationType.name() + iterationNumber + ".jfr"));
                currentData = CpuUsageData.measureCpuUsage(() -> actualInputSize.set(test.compute()));
              }
              catch (Throwable e) {
                ExceptionUtil.rethrowUnchecked(e);
                throw new RuntimeException(e);
              }
              finally {
                AsyncProfiler.stopProfiling();
              }
              int actualUsedCpuCores = usedReferenceCpuCores < 8
                                       ? Math.min(JobSchedulerImpl.getJobPoolParallelism(), usedReferenceCpuCores)
                                       : JobSchedulerImpl.getJobPoolParallelism();
              int expectedOnMyMachine = getExpectedTimeOnThisMachine(actualInputSize.get(), actualUsedCpuCores);
              IterationResult iterationResult = currentData.getIterationResult(expectedOnMyMachine);

              boolean passed = iterationResult == IterationResult.ACCEPTABLE || iterationResult == IterationResult.BORDERLINE;
              String message =
                formatMessage(currentData, expectedOnMyMachine, actualInputSize.get(), actualUsedCpuCores, iterationResult);
              return new IterationStatus(iterationResult, passed, message);
            };

            IterationStatus iterationStatus = computeWithSpanAttributes(
              tracer, "Attempt: " + attempt,
              iterationStatusSupplier -> {
                var spanAttributes = new HashMap<String, String>();

                spanAttributes.put("Attempt status", String.valueOf(iterationStatusSupplier));
                if (iterationType.equals(IterationMode.WARMUP)) {
                  spanAttributes.put("warmup", "true");
                }

                return spanAttributes;
              },
              () -> operation.get());

            JitUsageResult jitUsage = updateJitUsage();
            String s =
              "  " + (maxIterationsNumber - attempt) + " " + StringUtil.pluralize("attempt", maxIterationsNumber - attempt) + " remain" +
              (jitUsage == JitUsageResult.UNCLEAR ? " (waiting for JITc; its usage was " + jitUsage + " in this iteration)" : "");
            TeamCityLogger.warning(s, null);
            if (UsefulTestCase.IS_UNDER_TEAMCITY) {
              System.out.println(s);
              System.out.println(iterationStatus);
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
        // publish warmup and clean measurements at once at the end of the runs
        if (iterationType.equals(IterationMode.MEASURE)) {
          MetricsPublisher.Companion.getInstance().publishSync(fullQualifiedTestMethodName, launchName);
        }
      }
      catch (Throwable t) {
        System.err.println("Something unexpected happened during publishing performance metrics");
        throw t;
      }
    }
  }

  private @NotNull String formatMessage(@NotNull CpuUsageData data,
                                        int expectedOnMyMachine,
                                        int actualInputSize,
                                        int actualUsedCpuCores,
                                        @NotNull IterationResult iterationResult) {
    long duration = data.durationMs;
    int percentage = (int)(100.0 * (duration - expectedOnMyMachine) / expectedOnMyMachine);
    String colorCode = iterationResult == IterationResult.ACCEPTABLE ? "32;1m" : // green
                       iterationResult == IterationResult.BORDERLINE ? "33;1m" : // yellow
                       "31;1m"; // red
    return
      launchName + " took \u001B[" + colorCode + Math.abs(percentage) + "% " + (percentage > 0 ? "more" : "less") + " time\u001B[0m than expected" +
      (iterationResult == IterationResult.DISTRACTED ? " (but JIT compilation took too long, will retry anyway)" : "") +
      "\n  Expected: " + expectedOnMyMachine + "ms" + (expectedOnMyMachine < 1000 ? "" : " (" + StringUtil.formatDuration(expectedOnMyMachine) + ")") +
      "\n  Actual:   " + duration + "ms" + (duration < 1000 ? "" : " (" + StringUtil.formatDuration(duration) + ")") +
      (expectedInputSize != actualInputSize ? "\n  (Expected time was adjusted accordingly to input size: expected " + expectedInputSize + ", actual " + actualInputSize + ".)": "") +
      (usedReferenceCpuCores != actualUsedCpuCores ? "\n  (Expected time was adjusted accordingly to number of available CPU cores: reference CPU has " + usedReferenceCpuCores + ", actual value is " + actualUsedCpuCores + ".)": "") +
      "\n  Timings:  " + Timings.getStatistics() +
      "\n  Threads:  " + data.getThreadStats() +
      "\n  GC stats: " + data.getGcStats() +
      "\n  Process:  " + data.getProcessCpuStats();
  }

  private long lastJitUsage;
  private long lastJitStamp = -1;

  private JitUsageResult updateJitUsage() {
    long timeNow = System.nanoTime();
    long jitNow = CpuUsageData.getTotalCompilationMillis();

    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(timeNow - lastJitStamp);
    if (lastJitStamp >= 0) {
      if (elapsedMillis >= 3_000) {
        if (jitNow - lastJitUsage <= elapsedMillis / 10) {
          return JitUsageResult.DEFINITELY_LOW;
        }
      }
      else {
        // don't update stamps too frequently,
        // because JIT times are quite discrete: they only change after a compilation is finished,
        // and some compilations take a second or even more
        return JitUsageResult.UNCLEAR;
      }
    }

    lastJitStamp = timeNow;
    lastJitUsage = jitNow;

    return JitUsageResult.UNCLEAR;
  }

  private enum JitUsageResult {DEFINITELY_LOW, UNCLEAR}

  enum IterationResult {
    ACCEPTABLE, // test was completed within the specified range
    BORDERLINE, // test barely managed to complete within the specified range
    SLOW,       // test was too slow
    DISTRACTED  // CPU was occupied by irrelevant computations for too long (e.g., JIT or GC)
  }

  private static final class AsyncProfiler {
    private static final Object asyncProfiler = getAsyncProfilerInstance();

    private static Object getAsyncProfilerInstance() {
      try {
        Class<?> asyncProfilerExtractorClass = Class.forName("com.intellij.profiler.ultimate.async.extractor.AsyncProfilerExtractor");
        Field instanceField = asyncProfilerExtractorClass.getField("INSTANCE");
        Object instance = instanceField.get(null);
        Method getInstanceMethod = asyncProfilerExtractorClass.getMethod("getAsyncProfilerInstance", String.class);
        return getInstanceMethod.invoke(instance, new Object[]{null});
      }
      catch (ClassNotFoundException e) {
        System.out.println("Async is not in class loader");
      }
      catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        e.printStackTrace();
      }
      return null;
    }

    public static void stopProfiling() {
      try {
        execute("stop");
      }
      catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
        System.out.println("Can't stop profiling");
      }
    }

    public static void startProfiling(Path file) {
      try {
        String command = String.format("start,interval=5ms,event=wall,jfr,file=%s.jfr", file.toAbsolutePath());
        execute(command);
      }
      catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
        System.out.println("Can't start profiling");
      }
    }

    private static void execute(String command) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
      if (asyncProfiler != null) {
        Method executeMethod = asyncProfiler.getClass().getMethod("execute", String.class);
        executeMethod.invoke(asyncProfiler, command);
      }
    }
  }

  private int getExpectedTimeOnThisMachine(int actualInputSize, int actualUsedCpuCores) {
    int expectedOnMyMachine = (int)(((long)expectedMs) * actualInputSize / expectedInputSize);
    if (adjustForCPU) {
      expectedOnMyMachine *= usedReferenceCpuCores;
      expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.CPU_TIMING, Timings.REFERENCE_CPU_TIMING, useLegacyScaling);
      expectedOnMyMachine /= actualUsedCpuCores;
    }
    if (adjustForIO) {
      expectedOnMyMachine = adjust(expectedOnMyMachine, Timings.IO_TIMING, Timings.REFERENCE_IO_TIMING, useLegacyScaling);
    }
    return expectedOnMyMachine;
  }

  private static int adjust(int expectedOnMyMachine, long thisTiming, long referenceTiming, boolean useLegacyScaling) {
    if (useLegacyScaling) {
      double speed = 1.0 * thisTiming / referenceTiming;
      double delta = speed < 1
                     ? 0.9 + Math.pow(speed - 0.7, 2)
                     : 0.45 + Math.pow(speed - 0.25, 2);
      expectedOnMyMachine *= delta;
      return expectedOnMyMachine;
    }
    else {
      return (int)(expectedOnMyMachine * thisTiming / referenceTiming);
    }
  }
}
