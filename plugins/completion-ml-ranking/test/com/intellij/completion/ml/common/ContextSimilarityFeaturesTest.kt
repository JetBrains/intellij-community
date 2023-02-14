// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.textMatching.SimilarityScorer
import org.junit.Assert
import org.junit.Test


class ContextSimilarityFeaturesTest : LightJavaCodeInsightTestCase() {
  @Test
  fun `test line similarity features`() {
    val scorer = ContextSimilarityUtil.createLineSimilarityScorer("int filesCount = ")
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
    val scorer = ContextSimilarityUtil.createParentSimilarityScorer(element)
    scorer.checkSimilarity("tests", 1.0, 1.0)
    scorer.checkSimilarity("TestData", 1.0, 0.75)
    scorer.checkSimilarity("TestDataUtil", 0.666, 0.5)
  }

  private fun SimilarityScorer.checkSimilarity(lookupString: String, expectedMax: Double, expectedMean: Double) {
    val delta = 0.001
    val scores = score(lookupString)
    Assert.assertEquals(expectedMax, scores.maxOrNull()!!, delta)
    Assert.assertEquals(expectedMean, scores.average(), delta)
  }
}