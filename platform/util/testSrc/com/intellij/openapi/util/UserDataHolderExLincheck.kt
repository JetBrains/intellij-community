// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.jupiter.api.Test

@Suppress("unused")
class UserDataHolderExLincheck {

  companion object {
    private val keyA = Key<Int>("a")
    private val keyB = Key<Int>("b")
  }

  val holder = UserDataHolderBase()

  @Operation
  fun getOrCreateA(value: Int) = holder.getOrCreateUserData(keyA) { value }

  @Operation
  fun setKeyA(value: Int?) = holder.putUserData(keyA, value)

  @Operation
  fun updateKeyA(value: Int) = holder.updateUserData(keyA) { value }

  @Operation
  fun getAndUpdateKeyA(value: Int) = holder.getAndUpdateUserData(keyA) { value }

  @Operation
  fun incrementKeyA(value: Int) = holder.updateUserData(keyA) { (it ?: 0) + value }

  @Operation
  fun getOrCreateB(value: Int) = holder.getOrCreateUserData(keyB) { value }

  @Operation
  fun setKeyB(value: Int?) = holder.putUserData(keyB, value)

  @Operation
  fun updateKeyB(value: Int) = holder.updateUserData(keyB) { value }

  @Operation
  fun getAndUpdateKeyB(value: Int) = holder.getAndUpdateUserData(keyB) { value }

  @Operation
  fun incrementKeyB(value: Int) = holder.updateUserData(keyB) { (it ?: 0) + value }

  @Test
  fun stressTest() = StressOptions().check(this::class)
}
