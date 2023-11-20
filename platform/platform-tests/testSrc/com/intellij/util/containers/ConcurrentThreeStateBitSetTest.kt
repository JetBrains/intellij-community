// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.Test

class ConcurrentThreeStateBitSetTest {
  private val bitSet: ConcurrentThreeStateBitSet = ConcurrentThreeStateBitSet.create()

  // default ConcurrentThreeBitSet can encode 1024 values, so we need bigger range to trigger underlying array resize
  @Operation
  fun set(@Param(gen = IntGen::class, conf = "0:2047") index: Int, value: Boolean?) = bitSet.set(index, value)

  @Operation
  fun get(@Param(gen = IntGen::class, conf = "0:2047") index: Int) = bitSet[index]

  @Operation
  fun compareAndSet(@Param(gen = IntGen::class, conf = "0:2047") index: Int, expected: Boolean?, new: Boolean?): Boolean {
    return bitSet.compareAndSet(index, expected, new)
  }

  @Test
  fun stressTest() = StressOptions().check(this::class)

  @Test
  fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}