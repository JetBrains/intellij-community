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
package com.siyeh.ipp.braces;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see RemoveBracesIntention
 * @author Bas Leijdekkers
 */
public class RemoveBracesIntentionTest extends IPPTestCase {
  @Override
  protected String getRelativePath() {
    return "braces/remove";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("remove.braces.intention.name", "if");
  }

  public void testBetweenIfAndElse() { assertIntentionNotAvailable(RemoveBracesIntention.class);}
  public void testIfElse() { doTest(); }
  public void testIfElse2() { doTest(); }
}
