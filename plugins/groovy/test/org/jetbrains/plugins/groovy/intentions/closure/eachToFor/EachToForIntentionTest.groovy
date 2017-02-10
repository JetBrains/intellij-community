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

package org.jetbrains.plugins.groovy.intentions.closure.eachToFor

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.intentions.closure.EachToForIntention.HINT

/**
 * @author Maxim.Medvedev
 */
class EachToForIntentionTest extends GrIntentionTestCase {
  EachToForIntentionTest() {
    super(HINT)
  }

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "intentions/EachToFor/"
  }

  void testEachToFor() { doTest(true) }

  void testEachToForWithFinal() { doTest(true) }

  void testEachToForWithDefaultVariable() { doTest(true) }

  void testEachForInWithNoQualifier() { doTest(true) }

  void testWithClosureInBody() { doTest(true) }

  void testUpdateReturn() { doTest(true) }
  void testUpdateReturn2() { doTest(true) }
}
