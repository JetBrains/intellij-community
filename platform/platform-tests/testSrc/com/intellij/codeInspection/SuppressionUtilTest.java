/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection;


import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SuppressionUtilTest {
  private static final Pattern PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

  @Test
  public void testCommonSuppressPattern() {
    doTest("comment");
    doTest("noinspection");

    doTest("noinspection I1", "I1");
    doTest("noinspection I1, I2", "I1", "I2");
    doTest("noinspection I.1 ,  I-2,I_3", "I.1", "I-2", "I_3");

    try {
      doTest("noinspection ID junk", "ID", "junk");
      fail();
    }
    catch (AssertionError e) {
      assertEquals("'junk' is not in 'ID'", e.getMessage());
    }
  }

  private static void doTest(final String comment, final String... inspections) {
    final Matcher m = PATTERN.matcher(comment);
    if (inspections.length == 0) {
      assertFalse(m.matches());
      return;
    }
    assertTrue(m.matches());

    final String inspectionsList = m.group(1).trim();
    for (final String inspection : inspections) {
      assertTrue("'" + inspection + "' is not in '" + inspectionsList + "'",
                 SuppressionUtil.isInspectionToolIdMentioned(inspectionsList, inspection));
    }
  }
}
