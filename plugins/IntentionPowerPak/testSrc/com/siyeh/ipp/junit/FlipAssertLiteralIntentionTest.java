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
 * @see FlipAssertLiteralIntention
 * @author Bas Leijdekkers
 */
public class FlipAssertLiteralIntentionTest extends IPPTestCase {

  public void testMessage() { doTest(); }
  public void testExistingStaticImport() { doTest(); }
  public void testStaticImportWithoutTestMethod() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(java.lang.String message, boolean condition) {}" +
                       "  public static void assertFalse(boolean condition) {}" +
                       "}");
    myFixture.addClass("package org.junit;" +
                       "@Retention(RetentionPolicy.RUNTIME)" +
                       "@Target({ElementType.METHOD})" +
                       "public @interface Test {}");
  }

  @Override
  protected String getRelativePath() {
    return "junit/flip_assert_literal";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("flip.assert.literal.intention.name", "assertTrue", "assertFalse");
  }
}
