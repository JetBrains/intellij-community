// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages

import com.intellij.usageView.UsageInfo
import com.intellij.usages.rules.MergeableUsage
import org.jetbrains.concurrency.Promise


interface UsageInfoAdapter : Usage, MergeableUsage {
  val path: String
  val line: Int
  val navigationOffset: Int
  fun getMergedInfos(): Array<UsageInfo>
  fun getMergedInfosAsync(): Promise<Array<UsageInfo>>
}