// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AbstractExternalFilterTest {
  private static final String chineseFlexDocExample =
    "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=9\">";
  private static final String brokenExample         = "<head><meta http-equiv=\"X-UA-Compatible\" content=\"IE=9\">";
  private static final String confluenceExample     = "<meta charset=\"UTF-8\">";
  private static final String win1251Example        = "<meta HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=windows-1251\">";
  private static final String validUtf8Example      = "<meta http-equiv=\"Content-Type\" content=\"text/html\" charset=\"UTF-8\">>";

  @Test
  public void testEncodingDetector() {
    assertEquals("UTF-8", AbstractExternalFilter.parseContentEncoding(validUtf8Example));
    assertEquals("utf-8", AbstractExternalFilter.parseContentEncoding(chineseFlexDocExample));
    assertNull(AbstractExternalFilter.parseContentEncoding(brokenExample));
    assertEquals("UTF-8", AbstractExternalFilter.parseContentEncoding(confluenceExample));
    assertEquals("windows-1251", AbstractExternalFilter.parseContentEncoding(win1251Example));
  }
}