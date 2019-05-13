/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.whileloop;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWhileLoopWithDoWhileLoopIntentionTest extends IPPTestCase {

  public void testInfiniteLoop() { doTest(); }
  public void testRegular() { doTest(); }
  public void testNoBraces() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "whileloop/replace_while_with_do_while_loop";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.while.loop.with.do.while.loop.intention.name");
  }
}
