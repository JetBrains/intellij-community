// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.Action
import com.intellij.cce.interpreter.InterpretationOrder.*
import kotlin.random.Random

enum class InterpretationOrder {
  LINEAR,
  REVERSED,
  RANDOM
}

private val ORDER_RANDOM = Random(42)

fun <T : Action> List<T>.reorder(order: InterpretationOrder): List<T> {
  val groups = groupBy { it.sessionId }.values
  return when (order) {
    LINEAR -> groups.flatten()
    REVERSED -> groups.reversed().flatten()
    RANDOM -> groups.shuffled(ORDER_RANDOM).flatten()
  }
}
