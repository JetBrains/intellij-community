// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.vcs.changes.Change

data class VcsFileStatusInfo(val typeByte: Byte, val first: CharSequence, val second: CharSequence?) {
  override fun toString(): String {
    var s = "$type $first"
    if (second != null) {
      s += " -> $second"
    }
    return s
  }

  // for plugin compatibility
  constructor(type: Change.Type, firstPath: String, secondPath: String?) : this(type, firstPath as CharSequence, secondPath)

  // for convenience
  constructor(type: Change.Type, firstPath: CharSequence, secondPath: CharSequence?) : this(type.ordinal.toByte(), firstPath, secondPath)

  val firstPath: String get() = first.toString()
  val secondPath: String? get() = second?.toString()
  val type: Change.Type get() = Change.Type.entries[typeByte.toInt()]
}

// NB watch out for the `CharSequence#toString` performance for the FS-interned paths created by InternedGitLogRecordBuilder
val VcsFileStatusInfo.isRenamed: Boolean
  get() {
    if (type != Change.Type.MOVED || second == null) return false
    return firstPath != secondPath
  }