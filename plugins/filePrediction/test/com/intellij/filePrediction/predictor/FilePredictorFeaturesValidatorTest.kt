// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.logger.FilePredictionFeaturesValidator
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import junit.framework.TestCase

class FilePredictorFeaturesValidatorTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private fun newContext(features: String): EventContext {
    val candidate = hashMapOf(
      "file_path" to "abcd",
      "prob" to 0.2,
      "opened" to 1,
      "source" to 0,
      "features" to features
    )
    return EventContext.create(
      "calculated",
      hashMapOf(
        "session" to 1,
        "performance" to listOf(1, 2, 3, 4),
        "candidates" to listOf(candidate)
      )
    )
  }

  private fun doTestAccepted(features: String) {
    val validator = FilePredictionFeaturesValidator()
    TestCase.assertTrue(validator.validate(features, newContext(features)) == ValidationResultType.ACCEPTED)
  }

  private fun doTestRejected(features: String) {
    val validator = FilePredictionFeaturesValidator()
    TestCase.assertTrue(validator.validate(features, newContext(features)) == ValidationResultType.REJECTED)
  }

  fun `test empty list of features accepted`() {
    doTestAccepted("")
    doTestAccepted(",,,,")
    doTestAccepted(",,;,,")
    doTestAccepted(";;;;")
    doTestAccepted(";,;,;,;")
    doTestAccepted(",;,;,;,;,")
    doTestAccepted(",,;,,;,,;,,;,,")
  }

  fun `test list of numbers from one provider accepted`() {
    doTestAccepted("1,2,0")
    doTestAccepted(",1,2,,0")
  }

  fun `test list of doubles from one provider accepted`() {
    doTestAccepted("0.45,1.23,2.31,0.000012")
    doTestAccepted("0.45,,,,1.23,2.31,0.000012")
  }

  fun `test mixed list of numerical features from one provider accepted`() {
    doTestAccepted("1,0.45,0,1.23,2.31,2,0.000012")
    doTestAccepted("1,0.45,0,,1.23,2.31,2,0.000012,")
    doTestAccepted(",,,1,0.45,0,,1.23,2.31,2,0.000012,")
  }

  fun `test list of file types from one provider accepted`() {
    doTestAccepted("JAVA,XML")
    doTestAccepted("JAVA,XML,")
    doTestAccepted("JAVA,,XML,")
    doTestAccepted(",JAVA,XML,")
  }

  fun `test list of numbers from multiple providers accepted`() {
    doTestAccepted("1,-2,0;1,1")
    doTestAccepted("1,2,0;1,-1;;;")
    doTestAccepted("1,2;,0;1,1;,,;,;")
  }

  fun `test list of numerical features from multiple providers accepted`() {
    doTestAccepted("1,0.123,0;1,0.121")
    doTestAccepted("1,0.123,0;0.00001,1;;;")
    doTestAccepted("1,2.12213121323;,0.31;1,1.1;,,;,;")
  }

  fun `test list of file types from multiple providers accepted`() {
    doTestAccepted("JAVA,XML;PLAIN_TEXT")
    doTestAccepted("JAVA,XML,;PLAIN_TEXT,third.party")
    doTestAccepted("JAVA;XML,UNKNOWN;PLAIN_TEXT")
    doTestAccepted("JAVA;UNKNOWN,,XML;PLAIN_TEXT;")
  }

  fun `test list of mixed features from multiple providers accepted`() {
    doTestAccepted("1,;0,PLAIN_TEXT,0,1,,1,0,13,JAVA,2,1,1;0.51342,0.00921,0.0,0.0,0.0,4,12,0.43256,0.00142,0.08730,4.0;0,0,")
    doTestAccepted("1,;0,XML,0,0,,0,8,43,XML,38,1,0;0.54123,0.08301,0.0,0.0,0.0,1,10,0.98123,0.0098,0.01284,6.0;0,0,")
  }

  fun `test unknown file type rejected`() {
    doTestRejected("JAVA;UNKNOWN,,SomeUnknownType;PLAIN_TEXT;")
    doTestRejected("JAVA,UNKNOWN,SomeUnknownType,PLAIN_TEXT")
    doTestRejected("SomeUnknownType,PLAIN_TEXT")
    doTestRejected("SomeUnknownType")
  }

  fun `test not encoded boolean features rejected`() {
    doTestRejected("true,false,true")
    doTestRejected("true,false;true")
    doTestRejected("true;false;true")
  }

  fun `test features with unknown separator rejected`() {
    doTestRejected("JAVA:Scratch:|PLAIN_TEXT:third.party")
    doTestRejected("JAVA|Scratch:|PLAIN_TEXT:third.party")
  }
}