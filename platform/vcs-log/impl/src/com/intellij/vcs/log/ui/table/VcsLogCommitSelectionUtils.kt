// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogCommitSelectionUtils")

package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.VcsLogCommitSelection

/**
 * Selection size.
 */
val VcsLogCommitSelection.size: Int get() = rows.size

fun VcsLogCommitSelection.isEmpty() = size == 0
fun VcsLogCommitSelection.isNotEmpty() = size != 0

/**
 * Returns a lazy list containing the results of applying the given transform function to each commit id.
 *
 * @param transform function which gets commit details by commit id.
 */
fun <T> VcsLogCommitSelection.lazyMap(transform: (Int) -> T): List<T> {
  return ids.lazyMap(transform)
}

private fun <T> List<Int>.lazyMap(transform: (Int) -> T): List<T> {
  return object : AbstractList<T>() {
    override fun get(index: Int): T = transform(this@lazyMap[index])
    override val size get() = this@lazyMap.size
  }
}