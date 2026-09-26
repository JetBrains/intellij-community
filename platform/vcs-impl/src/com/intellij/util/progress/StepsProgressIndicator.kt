// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator

class StepsProgressIndicator(private val indicator: ProgressIndicator, private val totalSteps: Int) :
  DelegatingProgressIndicator(indicator) {

  private var finishedTasks = 0

  fun nextStep() {
    finishedTasks++
    fraction = 0.0
  }

  override fun setFraction(fraction: Double) {
    indicator.fraction = (finishedTasks + fraction) / totalSteps.toDouble()
  }
}