// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

class CommitCountStage private constructor(private val index: Int, private val stages: List<Int>) {
  val count: Int get() = stages[index]

  constructor(vararg stages: Int): this(0, stages.toList())

  val isInitial: Boolean get() = (index == 0)
  private val isLast: Boolean get() = (index == stages.size - 1)

  fun next(): CommitCountStage = if (isLast) this else CommitCountStage(index + 1, stages)
  fun last(): CommitCountStage = CommitCountStage(stages.size - 1, stages)

  override fun toString(): String = if (isAll()) "ALL" else count.toString()

  companion object {
    @JvmField
    val INITIAL = CommitCountStage(5, 100, 2000, Int.MAX_VALUE)
    @JvmField
    val ALL = CommitCountStage(Int.MAX_VALUE)
  }
}

fun isAll(count: Int) = count < 0 || count == Int.MAX_VALUE
fun CommitCountStage.isAll() = isAll(count)