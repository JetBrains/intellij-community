/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ipp.equality;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.equality.ReplaceEqualityWithEqualsIntention
 * @author Bas Leijdekkers
 */
public class ReplaceEqualityWithEqualsIntentionTest extends IPPTestCase {

  public void testEnumComparison() { assertIntentionNotAvailable(); }
  public void testNullComparison() { assertIntentionNotAvailable(); }
  public void testPrimitiveComparison() { assertIntentionNotAvailable(); }
  public void testSimpleObjectComparison() { doTest(); }
  public void testNegatedObjectComparison() { doTest(IntentionPowerPackBundle.message("replace.equality.with.not.equals.intention.name")); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.equality.with.equals.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "equality/replace_equality_with_equals";
  }
}