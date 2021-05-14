// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramFeatures
import com.intellij.filePrediction.references.ExternalReferencesResult

class FilePredictionFeaturesCache(val refs: ExternalReferencesResult, val nGrams: FilePredictionNGramFeatures)