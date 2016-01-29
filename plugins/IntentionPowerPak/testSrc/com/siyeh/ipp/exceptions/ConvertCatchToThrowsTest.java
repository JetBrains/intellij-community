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
package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ConvertCatchToThrowsIntention
 */
public class ConvertCatchToThrowsTest extends IPPTestCase {
  public void testSingleCatch() { doTest(); }
  public void testPluralCatches() { doTest(); }
  public void testMultiCatch() { doTest(); }
  public void testArmWithPluralCatches() { doTest(); }
  public void testArmWithSingleCatch() { doTest(); }
  public void testExistingThrows() { doTest(); }
  public void testLambda() { doTest(); }
  public void testLeaveFinallySection() { doTest(); }
  public void testTryWithConflictingDeclaration() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/catchToThrows";
  }
}
