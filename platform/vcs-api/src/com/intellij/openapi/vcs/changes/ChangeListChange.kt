// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.NlsSafe
import it.unimi.dsi.fastutil.Hash
import org.jetbrains.annotations.NonNls

class ChangeListChange(
  val change: Change,
  val changeListName: @NlsSafe String,
  val changeListId: @NonNls String
) : Change(change) {

  companion object {
    @JvmField
    val HASHING_STRATEGY: Hash.Strategy<Any> = object : Hash.Strategy<Any> {
      override fun hashCode(o: Any?): Int = o?.hashCode() ?: 0

      override fun equals(o1: Any?, o2: Any?): Boolean {
        return when {
          o1 is ChangeListChange && o2 is ChangeListChange -> o1 == o2 && o1.changeListId == o2.changeListId
          o1 is ChangeListChange || o2 is ChangeListChange -> false
          else -> o1 == o2
        }
      }
    }
  }
}
