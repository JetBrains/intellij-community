// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import gnu.trove.TObjectHashingStrategy
import java.util.*

class ChangeListChange(
  val change: Change,
  val changeListName: String,
  val changeListId: String
) : Change(change) {

  companion object {
    @JvmField
    val HASHING_STRATEGY: TObjectHashingStrategy<Any> = object : TObjectHashingStrategy<Any> {
      override fun computeHashCode(o: Any): Int = Objects.hashCode(o)

      override fun equals(o1: Any, o2: Any): Boolean = when {
        o1 is ChangeListChange && o2 is ChangeListChange -> o1 == o2 && o1.changeListId == o2.changeListId
        o1 is ChangeListChange || o2 is ChangeListChange -> false
        else -> o1 == o2
      }
    }
  }
}
