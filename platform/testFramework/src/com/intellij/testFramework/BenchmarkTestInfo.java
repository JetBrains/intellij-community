// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import kotlin.reflect.KFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public interface BenchmarkTestInfo {
  // to warn about not calling .start() in the end
  @Contract(pure = true)
  BenchmarkTestInfo setup(@NotNull ThrowableRunnable<?> setup);

  // to warn about not calling .start() in the end
  @Contract(pure = true)
  BenchmarkTestInfo attempts(int attempts);

  /**
   * Runs the perf test {@code iterations} times before starting the final measuring.
   */
  // to warn about not calling .start() in the end
  @Contract(pure = true)
  BenchmarkTestInfo warmupIterations(int iterations);

  /**
   * Sets {@link com.intellij.openapi.application.ex.ApplicationManagerEx#setInStressTest(boolean)} to true before the test,
   * restores original value after.
   * inStressTest disables many debug-level checks, and rises logLevel from DEBUG to INFO, which makes benchmark run much
   * closer to production run, thus making benchmark results more representative.
   */
  // to warn about not calling .start() in the end
  @Contract(pure = true)
  BenchmarkTestInfo runAsStressTest();

  String getUniqueTestName();

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
   * Last but not least: if metrics aren't published or even not collected - probably TelemetryManager instance isn't initialized correctly
   * or dependency on module intellij.tools.ide.metrics.benchmark isn't set.
   *
   * @see #start(String)
   * @see #start(Method)
   * @see #start(kotlin.reflect.KFunction)
   * @see #startAsSubtest(String)
   **/
  void start();

  /**
   * Use it if you need to run many subsequent performance tests in your JUnit test.<br/>
   * Each subtest should have unique name, otherwise published results would be overwritten.<br/>
   * <br/>
   * By default passed test launch name will be used as the subtest name.<br/>
   *
   * @see PerformanceTestInfoImpl#startAsSubtest(String)
   */
  void startAsSubtest();

  void startAsSubtest(@Nullable String subTestName);

  /**
   * Start execution of the performance test.
   *
   * @param fullQualifiedTestMethodName String representation of full method name.
   *                                    For Java you can use {@link com.intellij.testFramework.UsefulTestCase#getQualifiedTestMethodName()}
   *                                    OR
   *                                    {@link com.intellij.testFramework.fixtures.BareTestFixtureTestCase#getQualifiedTestMethodName()}
   * @see PerformanceTestInfoImpl#start()
   */
  void start(String fullQualifiedTestMethodName);

  /**
   * Start the perf test where test artifact path will have a name inferred from test method.
   * Useful in parametrized tests.
   * <br/>
   * Eg: <code>start(GradleHighlightingPerformanceTest::testCompletionPerformance)</code>
   *
   * @see PerformanceTestInfoImpl#start(Method)
   * @see PerformanceTestInfoImpl#start(String)
   */
  void start(@NotNull KFunction<?> kotlinTestMethod);

  String getLaunchName();

  /**
   * The method should be invoked right after constructor to provide required data.
   * It can be part of the constructor since instances are created via ServiceLoader.
   */
  BenchmarkTestInfo initialize(@NotNull ThrowableComputable<Integer, ?> test, int expectedInputSize, @NotNull String launchName);
}
