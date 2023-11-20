// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.annotations.Param
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.paramgen.IntGen
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.Test

class ConcurrentBitSetTest {
  private val bitSet: ConcurrentBitSet = ConcurrentBitSet.create()

  // min size of ConcurrentBitSet array is 32, so it fits 32*32=1024 until resize is triggered
  // we need to test more than 1024 to trigger resize
  @Operation
  fun set(@Param(gen = IntGen::class, conf = "0:2047") index: Int, value: Boolean) {
    bitSet.set(index, value)
  }

  @Operation
  fun get(@Param(gen = IntGen::class, conf = "0:2047") index: Int): Boolean {
    return bitSet.get(index)
  }

  // cardinality operation fails modelCheckingTest because it's not linearizable
  //@Operation
  //fun cardinality() = bitSet.cardinality()

  @Test
  fun stressTest() = StressOptions().check(this::class)

  @Test
  fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}