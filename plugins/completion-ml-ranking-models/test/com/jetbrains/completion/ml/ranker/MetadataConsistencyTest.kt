// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.completion.ml.ranker

import org.junit.Test

class MetadataConsistencyTest {
  @Test
  fun testKotlinMetadata() = ExperimentKotlinMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testScalaMetadata() = ExperimentScalaMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJavaMetadata() = ExperimentJavaMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJavaWithRecommendersMetadata() = ExperimentJavaRecommendersMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testRustMetadata() = ExperimentRustMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testPythonMetadata() = ExperimentPythonMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testPHPMetadata() = ExperimentPhpMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testRubyMetadata() = ExperimentRubyMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testGoMetadata() = ExperimentGoMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJSMetadata() = ExperimentJSMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testTypeScriptMetadata() = ExperimentTypeScriptMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testDartMetadata() = ExperimentDartMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testSwiftMetadata() = ExperimentSwiftMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testCidrMetadata() = ExperimentCidrMLRankingProvider().assertModelMetadataConsistent()
}
