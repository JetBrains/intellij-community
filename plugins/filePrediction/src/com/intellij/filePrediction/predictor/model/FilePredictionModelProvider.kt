// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor.model

import com.intellij.internal.ml.DecisionFunction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FilePredictionModelProvider {
  fun getModel(): DecisionFunction
}