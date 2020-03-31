// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

interface ModelMetadata {
  val binaryFeatures: List<BinaryFeature>

  val floatFeatures: List<FloatFeature>

  val categoricalFeatures: List<CategoricalFeature>

  val knownFeatures: Set<String>

  val featuresOrder: Array<FeatureMapper>

  val version: String?
}
