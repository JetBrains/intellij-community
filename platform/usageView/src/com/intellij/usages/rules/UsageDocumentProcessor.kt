// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.rules

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Segment
import com.intellij.usages.UsageInfoAdapter
import com.intellij.util.Processor

interface UsageDocumentProcessor: UsageInfoAdapter {
  fun getDocument(): Document?

  // must iterate in start offset order
  fun processRangeMarkers(processor: Processor<in Segment>): Boolean {
    for (usageInfo in getMergedInfos()) {
      val segment: Segment? = usageInfo.getSegment()
      if (segment != null && !processor.process(segment)) {
        return false
      }
    }
    return true
  }
}