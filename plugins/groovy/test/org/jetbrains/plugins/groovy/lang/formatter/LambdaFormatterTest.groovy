// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.formatter

import org.jetbrains.plugins.groovy.util.TestUtils

class LambdaFormatterTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/formatter/lambda/"

  void testBraceStyle1() { doTest() }

  void testBraceStyle2() { doTest() }

  void testBraceStyle3() { doTest() }

  void testBraceStyle4() { doTest() }

  void testBraceStyle5() { doTest() }

  void testBraceStyle6() { doTest() }

  void testBraceStyle7() { doTest() }

  void testBraceStyle8() { doTest() }

  void testOneLineLambda1() { doTest() }

  void testOneLineLambda2() { doTest() }

  void testOneLineLambda3() { doTest() }

  void testOneLineLambda4() { doTest() }

  void testOneLineLambda5() { doTest() }

  void testOneLineLambda6() { doTest() }

  void testOneLineLambda7() { doTest() }

  void testParams() { doTest() }

  void testLambdaParametersAligned() { doTest() }

  void testAlignLambdaBraceWithCall() { doTest() }

  void testChainCall1() { doTest() }

  void testChainCall2() { doTest() }

  void testChainCall3() { doTest() }

  void testChainCall4() { doTest() }

  void testChainCall5() { doTest() }

  void testChainCall6() { doTest() }

  void testChainCall7() { doTest() }

  void testChainCall8() { doTest() }

  void testChainCall9() { doTest() }

  void testChainCall10() { doTest() }

  void testChainCallWithSingleExpressionLambda1() { doTest() }

  void testChainCallWithSingleExpressionLambda2() { doTest() }

  void testNoFlyingGeese() { doTest() }

  void testNoFlyingGeese2() { doTest() }

  void testSpacesAroundArrow1() { doTest() }

  void testSpacesAroundArrow2() { doTest() }

  void testSpacesAroundArrow3() { doTest() }

  void testSpacesAroundArrow4() { doTest() }

  void testSpacesAroundArrow5() { doTest() }

  void testSpacesAroundArrow6() { doTest() }

  void testSpacesAroundArrow7() { doTest() }

  void testSpacesAroundArrow8() { doTest() }

  void testSpacesAroundArrow9() { doTest() }

  void testSpacesAroundArrow10() { doTest() }

  private doTest() {
    doTest(getTestName(true) + ".test")
  }
}
