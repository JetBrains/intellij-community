// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import java.util.Comparator

internal class ChangelistComparator : Comparator<ShelvedChangeList?> {
  override fun compare(o1: ShelvedChangeList, o2: ShelvedChangeList): Int {
    return o2.getDate().compareTo(o1.getDate())
  }

  companion object {
    private val ourInstance = ChangelistComparator()

    fun getInstance(): ChangelistComparator {
      return ourInstance
    }
  }
}
