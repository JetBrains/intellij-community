// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric.util

import kotlin.random.Random

internal object Bootstrap {
  fun <T> computeInterval(sample: List<T>, callback: (List<T>) -> Double): Pair<Double, Double> {
    val bootstrapSample = mutableListOf<Double>()
    for (i in 0 until SAMPLE_SIZE) {
      val newSample = mutableListOf<T>()
      for (j in sample.indices) {
        newSample.add(sample[Random.nextInt(sample.size)])
      }
      bootstrapSample.add(callback(newSample))
    }
    bootstrapSample.sort()
    return bootstrapSample[(SAMPLE_SIZE * 0.025).toInt()] to bootstrapSample[(SAMPLE_SIZE * 0.975).toInt()]
  }

  private const val SAMPLE_SIZE = 10000
}