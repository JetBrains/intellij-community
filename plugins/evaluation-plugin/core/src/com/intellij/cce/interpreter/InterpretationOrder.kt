// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

import com.intellij.cce.actions.Action
import com.intellij.cce.interpreter.InterpretationOrder.LINEAR
import com.intellij.cce.interpreter.InterpretationOrder.RANDOM
import com.intellij.cce.interpreter.InterpretationOrder.REVERSED
import kotlin.random.Random

enum class InterpretationOrder {
  LINEAR,
  REVERSED,
  RANDOM
}

private val ORDER_RANDOM = Random(42)

fun <T> List<T>.naiveReorder(order: InterpretationOrder): List<T> = when (order) {
  LINEAR -> this
  REVERSED -> this.reversed()
  RANDOM -> this.shuffled(ORDER_RANDOM)
}

fun <T : Action> List<T>.reorder(order: InterpretationOrder): List<T> =
  groupBy { it.sessionId }.values.toList<List<T>>().naiveReorder<List<T>>(order).flatten()
