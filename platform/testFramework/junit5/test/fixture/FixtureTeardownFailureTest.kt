// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.engine.Constants
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import java.io.PrintWriter
import java.io.StringWriter

private const val SCENARIO_DISABLED_REASON = "Run by FixtureTeardownFailureTest"
private const val CLASS_TEARDOWN_FAILURE = "the teardown of a class fixture fails"
private const val TEST_TEARDOWN_FAILURE = "the teardown of a test fixture fails"

/**
 * A fixture whose teardown fails fails the class or the test that owns the fixture, and no other one.
 *
 * Runs a nested engine, so that the scenario classes run in a known order in one engine run, as the classes of a
 * Bazel shard do.
 */
class FixtureTeardownFailureTest {
  @Test
  fun `a failed teardown of a class fixture fails its class and not the next class`() {
    val summary = run(LeakingClassScenario::class.java, CleanClassScenario::class.java)

    val failures = summary.failures
    assertThat(failures).describedAs(report(summary)).hasSize(1)
    assertThat(failures.single().testIdentifier.displayName).endsWith(LeakingClassScenario::class.java.simpleName)
    assertThat(failures.single().exception).hasMessage(CLASS_TEARDOWN_FAILURE)
    assertThat(summary.testsSucceededCount).describedAs(report(summary)).isEqualTo(2)
  }

  @Test
  fun `a failed teardown of a test fixture fails its test and not the next test`() {
    val summary = run(LeakingTestScenario::class.java)

    val failures = summary.failures
    assertThat(failures).describedAs(report(summary)).hasSize(1)
    assertThat(failures.single().testIdentifier.displayName).isEqualTo("first()")
    assertThat(failures.single().exception).hasMessage(TEST_TEARDOWN_FAILURE)
    assertThat(summary.testsSucceededCount).describedAs(report(summary)).isEqualTo(1)
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  @TestFixtures
  @Order(1)
  class LeakingClassScenario {
    companion object {
      @Suppress("unused")
      private val failingTeardown = testFixture { initialized(Unit) { throw IllegalStateException(CLASS_TEARDOWN_FAILURE) } }
    }

    @Test
    fun passes() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  @TestFixtures
  @Order(2)
  class CleanClassScenario {
    @Test
    fun passesAfterTheLeakingClass() {
    }
  }

  @Disabled(SCENARIO_DISABLED_REASON)
  @TestFixtures
  @TestMethodOrder(MethodOrderer.MethodName::class)
  class LeakingTestScenario {
    @Suppress("unused")
    private val failingTeardown = testFixture { initialized(Unit) { if (++teardowns == 1) throw IllegalStateException(TEST_TEARDOWN_FAILURE) } }

    @Test
    fun first() {
    }

    @Test
    fun second() {
    }

    companion object {
      private var teardowns = 0
    }
  }
}

private fun run(vararg scenarioClasses: Class<*>): TestExecutionSummary {
  // The auto-registered session listeners of the outer run would enable the IDE-wide extensions a second time. The
  // nested run needs only the extensions that META-INF/services declares.
  val launcher = LauncherFactory.create(LauncherConfig.builder().enableLauncherSessionListenerAutoRegistration(false).build())
  val request = LauncherDiscoveryRequestBuilder.request()
    .selectors(scenarioClasses.map { selectClass(it) })
    .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
    .configurationParameter(Constants.EXTENSIONS_AUTODETECTION_ENABLED_PROPERTY_NAME, "true")
    .configurationParameter(Constants.DEFAULT_TEST_CLASS_ORDER_PROPERTY_NAME, ClassOrderer.OrderAnnotation::class.java.name)
    .build()
  val listener = SummaryGeneratingListener()
  launcher.execute(request, listener)
  return listener.summary
}

private fun report(summary: TestExecutionSummary): String {
  val report = StringWriter()
  PrintWriter(report).use { writer -> summary.printFailuresTo(writer, 30) }
  return report.toString()
}
