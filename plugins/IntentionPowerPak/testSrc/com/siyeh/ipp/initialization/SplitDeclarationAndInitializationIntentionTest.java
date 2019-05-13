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
package com.siyeh.ipp.initialization;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.initialization.SplitDeclarationAndInitializationIntention
 * @author Bas Leijdekkers
 */
public class SplitDeclarationAndInitializationIntentionTest extends IPPTestCase {

  public void testArrayInitializer() { doTest(); }
  public void testArray() { doTest(); }
  public void testFieldUsedBeforeInitializer() { doTest(); }
  public void testFieldUsedBeforeInitializer1() { doTest(); }
  public void testMultipleFieldsSingleDeclaration() { doTest(); }
  public void testMultipleFieldsSingleDeclaration2() { doTest(); }
  public void testMultipleFieldsSingleDeclaration3() { doTest(); }
  public void testNotInsideCodeBlock() { doTest(); }
  public void testInsideCodeBlock() { assertIntentionNotAvailable(); }

  @Override
  protected String getRelativePath() {
    return "initialization";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("split.declaration.and.initialization.intention.name");
  }
}
