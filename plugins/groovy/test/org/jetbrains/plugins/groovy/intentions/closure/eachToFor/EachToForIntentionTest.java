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
package org.jetbrains.plugins.groovy.intentions.closure.eachToFor;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import static org.jetbrains.plugins.groovy.intentions.closure.EachToForIntention.HINT;

/**
 * @author Maxim.Medvedev
 */
public class EachToForIntentionTest extends GrIntentionTestCase {
  public EachToForIntentionTest() {
    super(HINT);
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/EachToFor/";
  }

  public void testEachToFor() { doTest(true); }

  public void testEachToForWithFinal() { doTest(true); }

  public void testEachToForWithDefaultVariable() { doTest(true); }

  public void testEachForInWithNoQualifier() { doTest(true); }

  public void testWithClosureInBody() { doTest(true); }

  public void testUpdateReturn() { doTest(true); }

  public void testUpdateReturn2() { doTest(true); }
}
