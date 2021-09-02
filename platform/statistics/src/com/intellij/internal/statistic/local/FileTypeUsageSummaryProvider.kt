// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

interface FileTypeUsageSummaryProvider {
  fun getFileTypeStats(): Map<String, FileTypeUsageSummary>
  fun updateFileTypeSummary(fileTypeName: String)
}

@Tag("summary")
class FileTypeUsageSummary(@Attribute("usageCount") @JvmField var usageCount: Int,
                           @Attribute("lastUsed") @JvmField var lastUsed: Long) {

  constructor() : this(0, 0)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FileTypeUsageSummary
    return usageCount == other.usageCount && lastUsed == other.lastUsed
  }

  override fun hashCode() = (31 * usageCount) + lastUsed.hashCode()
}