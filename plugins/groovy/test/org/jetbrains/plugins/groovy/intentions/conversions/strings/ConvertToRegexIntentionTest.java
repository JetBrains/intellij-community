// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToRegexIntentionTest extends GrIntentionTestCase {
  public ConvertToRegexIntentionTest() {
    super("Convert to slashy string");
  }

  public void testEscapeSlashes() { doTest(true); }
  public void testStringEndsWithBackslash() { doTest(true); }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertToRegex/";
  }
}
