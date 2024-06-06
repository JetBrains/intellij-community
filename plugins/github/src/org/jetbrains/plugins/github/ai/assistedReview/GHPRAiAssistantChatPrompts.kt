// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

fun prepareMergeRequestData(data: List<ReviewFileAiData>): String =
  data.joinToString(separator = "\n\n") {
    it.rawLocalPath + "\n" +
    "BEFORE changes:\n" + populateLineNumbers(it.contentBefore) + "\n\n" +
    "AFTER changes:\n" + populateLineNumbers(it.contentAfter)
  }