/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import junit.framework.TestCase;

/**
 * User: Vassiliy.Kudryashov
 */
public class AbstractExternalFilterTest extends TestCase {
  private static final String chineseFlexDocExample =
    "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=9\">";
  private static final String brokenExample         = "<head><meta http-equiv=\"X-UA-Compatible\" content=\"IE=9\">";
  private static final String confluenceExample     = "<meta charset=\"UTF-8\">";
  private static final String win1251Example        = "<meta HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=windows-1251\">";
  private static final String validUtf8Example      = "<meta http-equiv=\"Content-Type\" content=\"text/html\" charset=\"UTF-8\">>";

  public void testEncodingDetector() {
    assertEquals("UTF-8", AbstractExternalFilter.parseContentEncoding(validUtf8Example));
    assertEquals("utf-8", AbstractExternalFilter.parseContentEncoding(chineseFlexDocExample));
    assertNull(AbstractExternalFilter.parseContentEncoding(brokenExample));
    assertEquals("UTF-8", AbstractExternalFilter.parseContentEncoding(confluenceExample));
    assertEquals("windows-1251", AbstractExternalFilter.parseContentEncoding(win1251Example));
  }
}
