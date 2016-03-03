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
package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see MergeCallSequenceToChainIntention
 * @author Bas Leijdekkers
 */
public class MergeCallSequenceToChainIntentionTest extends IPPTestCase {

  public void testAppend() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testParentheses2() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("merge.call.sequence.to.chain.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/merge_sequence";
  }
}
