// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.personalization.session

interface CompletionSelectionTracker {
  fun getTotalTimeInSelection(): Long
  fun getTimesInSelection(): Int
  fun getAverageTimeInSelection(): Double
  fun getMaxTimeInSelection(): Long?
  fun getMinTimeInSelection(): Long?
}