/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.junit;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.junit.ReplaceAssertLiteralWithAssertEqualsIntention
 * @author Bas Leijdekkers
 */
public class ReplaceAssertLiteralWithAssertEqualsIntentionTest extends IPPTestCase {

  public void testOutsideTestMethod() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  static public void assertNull(Object actual) {}" +
                       "  static public void assertEquals(Object expected, Object actual) {}" +
                       "}");
  }

  @Override
  protected String getRelativePath() {
    return "junit/replace_assert_literal_with_assert_equals";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name", "assertNull", "null");
  }
}
