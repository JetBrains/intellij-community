// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.strings;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class ConvertConcatenationToGstringTest extends GrIntentionTestCase {
  public ConvertConcatenationToGstringTest() {
    super("Convert to GString");
  }

  public void testSimpleCase() {
    doTest(true);
  }

  public void testVeryComplicatedCase() {
    doTest(true);
  }

  public void testQuotes() {
    doTest(true);
  }

  public void testQuotes2() {
    doTest(true);
  }

  public void testQuotesInMultilineString() {
    doTest(true);
  }

  public void testDot() {
    doTest(true);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertConcatenationToGstring/";
  }
}
