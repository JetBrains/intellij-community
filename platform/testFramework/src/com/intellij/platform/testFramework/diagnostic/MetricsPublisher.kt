// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.diagnostic

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.util.*

interface MetricsPublisher {
  fun publish(vararg metricName: String): Unit

  companion object {
    @JvmStatic
    fun getInstance(): MetricsPublisher = instance.value
  }
}

/** Dummy that always "works successfully" */
class NoopMetricsPublisher : MetricsPublisher {
  override fun publish(vararg metricName: String) {}
}

private val instance: SynchronizedClearableLazy<MetricsPublisher> = SynchronizedClearableLazy {
  val log = logger<MetricsPublisher>()

  val instance = try {
    val aClass = MetricsPublisher::class.java
    val implementations = ServiceLoader.load(aClass, aClass.classLoader).toList()
    if (implementations.isEmpty()) {
      log.info("No implementation found for MetricsPublisher - NOOP implementation will be used")
      NoopMetricsPublisher()
    }
    else if (implementations.size > 1) {
      log.error("More than one implementation for ${aClass.simpleName} found: ${implementations.map { it::class.qualifiedName }}")
      NoopMetricsPublisher()
    }
    else {
      implementations.single()
    }
  }
  catch (e: Throwable) {
    log.info("Cannot create MetricsPublisher, falling back to NOOP implementation", e)
    NoopMetricsPublisher()
  }

  log.info("Loaded metrics publisher implementation ${instance::class.java.name}")
  instance
}