// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatus
import com.intellij.util.containers.HashingStrategy
import org.jetbrains.annotations.NonNls

class ChangeListChange(
  beforeRevision: ContentRevision?,
  afterRevision: ContentRevision?,
  fileStatus: FileStatus,
  val change: Change,
  val changeListName: @NlsSafe String,
  val changeListId: @NonNls String
) : Change(beforeRevision, afterRevision, fileStatus) {

  init {
    // In practice, fields are only used by SVN, and SvnVcs.arePartialChangelistsSupported == false.
    copyFieldsFrom(change)
  }

  constructor(change: Change, changeListName: @NlsSafe String, changeListId: @NonNls String)
    : this(change.beforeRevision, change.afterRevision, change.fileStatus,
           change, changeListName, changeListId)

  companion object {
    @JvmField
    val HASHING_STRATEGY: HashingStrategy<Any> = object : HashingStrategy<Any> {
      override fun hashCode(o: Any?): Int = o?.hashCode() ?: 0

      override fun equals(o1: Any?, o2: Any?): Boolean {
        return when {
          o1 is ChangeListChange && o2 is ChangeListChange -> o1 == o2 && o1.changeListId == o2.changeListId
          o1 is ChangeListChange || o2 is ChangeListChange -> false
          else -> o1 == o2
        }
      }
    }

    fun replaceChangeContents(change: Change, bRev: ContentRevision?, aRev: ContentRevision?): Change {
      // do not rely on poorly supported 'ContentRevision.equals'
      if (change.beforeRevision === bRev && change.afterRevision === aRev) {
        return change
      }

      if (change is ChangeListChange) {
        return ChangeListChange(bRev, aRev,
                                change.fileStatus,
                                change,
                                change.changeListName,
                                change.changeListId)
      }
      else {
        return Change(bRev, aRev, change.fileStatus, change)
      }
    }
  }
}
