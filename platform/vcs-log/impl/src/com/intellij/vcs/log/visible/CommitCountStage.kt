// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

open class CommitCountStage(val count: Int) {
  open fun next(): CommitCountStage = ALL
  override fun toString(): String = if (isAll()) "ALL" else count.toString()

  companion object {
    @JvmField
    val ALL = CommitCountStage(Int.MAX_VALUE)
    @JvmField
    val SECOND_STEP = CommitCountStage(2000)
    @JvmField
    val FIRST_STEP = object : CommitCountStage(100) {
      override fun next() = SECOND_STEP
    }
    @JvmField
    val INITIAL = object : CommitCountStage(5) {
      override fun next() = FIRST_STEP
    }
  }
}

fun isAll(count: Int) = count < 0 || count == Int.MAX_VALUE
fun CommitCountStage.isAll() = isAll(count)