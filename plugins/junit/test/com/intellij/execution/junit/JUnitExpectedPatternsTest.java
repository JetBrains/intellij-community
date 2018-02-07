/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.junit4.ExpectedPatterns;
import junit.framework.Assert;
import org.junit.Test;

public class JUnitExpectedPatternsTest {
  @Test
  public void testArrayComparison() {
    Assert.assertNotNull(ExpectedPatterns.createExceptionNotification("arrays first differed at element [0]; expected: java.lang.String<Text> but was: java.lang.StringBuilder<Text>"));
  }

  @Test
  public void testMultiplePatternsInOneAssertion() {
    Assert.assertNull(ExpectedPatterns.createExceptionNotification("line1 expected:<java.lang.String<Text>> but was:<java.lang.StringBuilder<Text>>\n" +
                                                                   "line2 expected:<java.lang.String<Text1>> but was:<java.lang.StringBuilder<Text1>>"));
  }

  @Test
  public void testHamcrestAssertThatWithReason() {
    Assert.assertNotNull(ExpectedPatterns.createExceptionNotification("reason\n" +
                                                                      "Expected: is \"aaa\\naa\"\n" +
                                                                      "     but: was \"bbb\\nbb\""));
  }

  @Test
  public void testHamcrestAssertThatEqWithReason() {
    Assert.assertNotNull(ExpectedPatterns.createExceptionNotification("reason\n" +
                                                                      "Expected: \"aaa\\naa\"\n" +
                                                                      "     got: \"bbb\\nbb\""));
  }
}
