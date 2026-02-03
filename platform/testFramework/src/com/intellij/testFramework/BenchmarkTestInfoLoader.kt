// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.diagnostic.logger
import java.util.*

class BenchmarkTestInfoLoader {
  companion object {
    val instance: BenchmarkTestInfo
      get() {
        val log = logger<BenchmarkTestInfo>()

        val instance = run {
          val aClass = BenchmarkTestInfo::class.java
          val implementations = ServiceLoader.load(aClass, aClass.classLoader).toList()
          if (implementations.isEmpty()) {
            throw ServiceConfigurationError("No implementations of ${aClass.name} found. Make sure to include intellij.tools.ide.metrics.benchmark module" +
                                            " containing the implementation in the classpath.")
          }
          else if (implementations.size > 1) {
            throw ServiceConfigurationError("More than one implementation for ${aClass.simpleName} found: ${implementations.map { it::class.qualifiedName }}")
          }
          else {
            implementations.single()
          }
        }

        log.info("Loaded BenchmarkTestInfo implementation ${instance::class.java.name}")
        return instance
      }
  }
}

