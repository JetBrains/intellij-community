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