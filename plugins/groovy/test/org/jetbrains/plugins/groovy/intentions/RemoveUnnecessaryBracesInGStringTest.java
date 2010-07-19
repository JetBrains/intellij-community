/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class RemoveUnnecessaryBracesInGStringTest extends GrIntentionTestCase {  
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/removeUnnecessaryBraces/";
  }

  public void testIntention() throws Exception {
    doTest("Remove unnecessary braces in GString", true);
  }

  public void testRefWithQualifier() throws Exception {
    doTest("Remove unnecessary braces in GString", true);
  }

  public void testNoIntention() throws Exception {
    doTest("Remove unnecessary braces in GString", false);
  }
}
