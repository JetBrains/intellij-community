// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.util.ArrayUtilRt

class ChangeListRemoteState(size: Int) {
  // TODO BitSet suits here
  private val isUpToDateState = if (size == 0) ArrayUtilRt.EMPTY_BOOLEAN_ARRAY else BooleanArray(size) { true }

  fun report(index: Int, isUpToDate: Boolean) {
    isUpToDateState[index] = isUpToDate
  }

  fun allUpToDate(): Boolean = isUpToDateState.all { it }
}
