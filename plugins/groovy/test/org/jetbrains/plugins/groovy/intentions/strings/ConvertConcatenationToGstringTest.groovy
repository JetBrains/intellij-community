// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.strings

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class ConvertConcatenationToGstringTest extends GrIntentionTestCase {
  ConvertConcatenationToGstringTest() {
    super("Convert to GString")
  }

  @NotNull
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_1_7

  final String basePath = TestUtils.testDataPath + 'intentions/convertConcatenationToGstring/'

  void testSimpleCase() {
    doTest(true)
  }

  void testVeryComplicatedCase() {
    doTest(true)
  }

  void testQuotes() {
    doTest(true)
  }

  void testQuotes2() {
    doTest(true)
  }

  void testQuotesInMultilineString() {
    doTest(true)
  }

  void testDot() {
    doTest(true)
  }
}
