// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.testFramework.diagnostic.TelemetryMeterCollector
import com.intellij.util.ThrowableRunnable
import kotlin.reflect.KFunction

class NoOpPerformanceTestInfo(): PerformanceTestInfo {

  override fun setup(setup: ThrowableRunnable<*>): PerformanceTestInfo? {
    return this
  }

  override fun attempts(attempts: Int): PerformanceTestInfo? {
    return this
  }

  override fun withTelemetryMeters(meterCollector: TelemetryMeterCollector?): PerformanceTestInfo? {
    return this
  }

  override fun warmupIterations(iterations: Int): PerformanceTestInfo? {
    return this
  }

  override fun getUniqueTestName(): String? = ""

  override fun start() {}

  override fun startAsSubtest() {}

  override fun startAsSubtest(subTestName: String?) {}

  override fun start(fullQualifiedTestMethodName: String?){}

  override fun start(kotlinTestMethod: KFunction<*>) {}

  override fun getLaunchName(): String? = ""

  override fun initialize(test: ThrowableComputable<Int?, *>, expectedInputSize: Int, launchName: String) = this
}