// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.completion.ml.common.ContextSimilarityUtil.ContextSimilarityScoringFunction
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import org.junit.Assert
import org.junit.Test


class ContextSimilarityFeaturesTest : LightJavaCodeInsightTestCase() {
  @Test
  fun `test line similarity features`() {
    val scorer = ContextSimilarityUtil.createLineSimilarityScoringFunction("int filesCount = ")
    scorer.checkSimilarity("FileCountUtil", 0.666, 0.333)
    scorer.checkSimilarity("FileCount", 1.0, 0.5)
    scorer.checkSimilarity("new", 0.0, 0.0)
  }

  @Test
  fun `test parent similarity features`() {
    val text =
      """|class Test {
         |  private String tests = "";
         |  
         |  String getTestData() {
         |    <caret>;
         |  }
         |}
      """.trimMargin()
    configureFromFileText("Test.java", text)

    val element = file.findElementAt(editor.caretModel.offset)
    val scorer = ContextSimilarityUtil.createParentSimilarityScoringFunction(element)
    scorer.checkSimilarity("tests", 1.0, 1.0)
    scorer.checkSimilarity("TestData", 1.0, 0.75)
    scorer.checkSimilarity("TestDataUtil", 0.666, 0.5)
  }

  private fun ContextSimilarityScoringFunction.checkSimilarity(lookupString: String, expectedMax: Double, expectedMean: Double) {
    val delta = 0.001
    val similarity = scoreStemmedSimilarity(lookupString)
    Assert.assertEquals(expectedMax, similarity.maxSimilarity(), delta)
    Assert.assertEquals(expectedMean, similarity.meanSimilarity(), delta)
  }
}