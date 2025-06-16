// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

open class MergeConflictType @JvmOverloads constructor(
  open val type: Type,
  private val myLeftChange: Boolean,
  private val myRightChange: Boolean,
  open var resolutionStrategy: MergeConflictResolutionStrategy? = MergeConflictResolutionStrategy.DEFAULT
) {
  constructor(type: Type, leftChange: Boolean, rightChange: Boolean, canBeResolved: Boolean) :
    this(type, leftChange, rightChange, if (canBeResolved) MergeConflictResolutionStrategy.DEFAULT else null)

  open fun canBeResolved(): Boolean {
    return resolutionStrategy != null
  }

  open fun isChange(side: Side): Boolean {
    return if (side.isLeft) myLeftChange else myRightChange
  }

  open fun isChange(side: ThreeSide): Boolean {
    when (side) {
      ThreeSide.LEFT -> return myLeftChange
      ThreeSide.BASE -> return true
      ThreeSide.RIGHT -> return myRightChange
    }
  }

  enum class Type {
    INSERTED, DELETED, MODIFIED, CONFLICT
  }

  // for preservation of source compatibility with Kotlin code after j2k

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("getTypeDoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #type instead", ReplaceWith("type"))
  fun getType(): Type = type
}
