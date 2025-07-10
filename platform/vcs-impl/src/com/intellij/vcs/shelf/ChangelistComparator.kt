// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import java.util.Comparator

internal object ChangelistComparator : Comparator<ShelvedChangeList> {
  override fun compare(o1: ShelvedChangeList, o2: ShelvedChangeList): Int {
    return o2.date.compareTo(o1.date)
  }
}
