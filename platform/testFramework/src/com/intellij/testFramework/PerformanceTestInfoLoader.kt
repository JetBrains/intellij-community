// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.util.ServiceLoader

class PerformanceTestInfoLoader {
  companion object {
    private val instance: SynchronizedClearableLazy<PerformanceTestInfo> = SynchronizedClearableLazy {
      val log = logger<PerformanceTestInfo>()

      val instance = try {
        val aClass = PerformanceTestInfo::class.java
        val implementations = ServiceLoader.load(aClass, aClass.classLoader).toList()
        if (implementations.isEmpty()) {
          log.info("No implementation found for MetricsPublisher - NOOP implementation will be used")
          NoOpPerformanceTestInfo()
        }
        else if (implementations.size > 1) {
          log.error("More than one implementation for ${aClass.simpleName} found: ${implementations.map { it::class.qualifiedName }}")
          NoOpPerformanceTestInfo()
        }
        else {
          implementations.single()
        }
      }
      catch (e: Throwable) {
        log.info("Cannot create MetricsPublisher, falling back to NOOP implementation", e)
        NoOpPerformanceTestInfo()
      }

      log.info("Loaded metrics publisher implementation ${instance::class.java.name}")
      instance
    }

    fun getInstance(): PerformanceTestInfo = instance.value
  }

}

