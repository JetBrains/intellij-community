// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import java.util.*


internal sealed class PredictionData(val predictionToDisplay: Collection<VirtualFile>) {
  class Prediction(val requestedFiles: Collection<FilePath>, prediction: Collection<VirtualFile>) : PredictionData(prediction)
  class EmptyPrediction(val reason: EmptyPredictionReason) : PredictionData(listOf())

  enum class EmptyPredictionReason {
    SERVICE_INIT,
    TOO_MANY_FILES,
    TRAVERSER_INVALID,
    REQUIREMENTS_NOT_MET,
    GRAPH_CHANGED,
    EXCEPTION_THROWN,
    CALCULATION_CANCELED,
    UNEXPECTED_REASON;

    override fun toString() = name.toLowerCase(Locale.ENGLISH)
  }
}