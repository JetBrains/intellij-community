// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FileFeaturesComputationResult

class FilePredictionCandidate(val features: FileFeaturesComputationResult, val path: String, val probability: Double? = null)