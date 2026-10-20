// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.completion.ml.ranker

import org.junit.jupiter.api.Test

class MetadataConsistencyTest {
  @Test
  fun testKotlinMetadata(): Unit = ExperimentKotlinMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testScalaMetadata(): Unit = ExperimentScalaMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJavaMetadata(): Unit = ExperimentJavaMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJavaWithRecommendersMetadata(): Unit = ExperimentJavaRecommendersMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testRustMetadata(): Unit = ExperimentRustMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testPythonMetadata(): Unit = ExperimentPythonMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testPHPMetadata(): Unit = ExperimentPhpMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testRubyMetadata(): Unit = ExperimentRubyMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testGoMetadata(): Unit = ExperimentGoMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testJSMetadata(): Unit = ExperimentJSMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testTypeScriptMetadata(): Unit = ExperimentTypeScriptMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testDartMetadata(): Unit = ExperimentDartMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testSwiftMetadata(): Unit = ExperimentSwiftMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testCidrMetadata(): Unit = ExperimentCidrMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testHTMLMetadata(): Unit = ExperimentalHTMLMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testCSSMetadata(): Unit = ExperimentalCSSMLRankingProvider().assertModelMetadataConsistent()

  @Test
  fun testShMetadata(): Unit = ExperimentalShMLRankingProvider().assertModelMetadataConsistent()
}
