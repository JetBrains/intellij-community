// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import java.util.BitSet

class ChangeListRemoteState {
  private val notUpToDate = BitSet()

  fun report(index: Int, isUpToDate: Boolean) {
    notUpToDate.set(index, !isUpToDate)
  }

  fun allUpToDate(): Boolean = notUpToDate.isEmpty
}
