// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile


internal sealed class PredictionData(val predictionToDisplay: Collection<VirtualFile>) {
  class Prediction(val requestedFiles: Collection<FilePath>, prediction: Collection<VirtualFile>) : PredictionData(prediction)
  class EmptyPrediction(val reason: EmptyPredictionReason) : PredictionData(listOf())

  enum class EmptyPredictionReason {
    SERVICE_INIT,
    TOO_MANY_FILES,
    DATA_MANAGER_REMOVED,
    REQUIREMENTS_NOT_MET,
    DATA_PACK_IS_NOT_FULL,
    DATA_PACK_CHANGED,
    EXCEPTION_THROWN,
    CALCULATION_CANCELED,
    UNEXPECTED_REASON
  }
}