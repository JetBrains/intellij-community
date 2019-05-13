// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.xml.util.XmlStringUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class HighlightInfoTest extends TestCase {

  public void testHtmlTooltipWithDescription() {
    String description = "description with <>";
    String tooltip = XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(description)
                                              + "<br>"
                                              + "<a href ='#navigation//some/path.txt:0'>"
                                              + XmlStringUtil.escapeString("hint with <>")
                                              + "</a>");

    assertTooltipValid(description, tooltip);
  }

  public void testHtmlTooltipWithoutDescription() {
    String description = "description with <>";
    String tooltip = XmlStringUtil.wrapInHtml("Different description: "
                                              + "<br>"
                                              + "<a href ='#navigation//some/path.txt:0'>"
                                              + XmlStringUtil.escapeString("hint with <>")
                                              + "</a>");

    assertTooltipValid(description, tooltip);
  }

  private static void assertTooltipValid(@NotNull String description, @NotNull String tooltip) {
    HighlightInfo newInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(1, 2)
      .description(description).escapedToolTip(tooltip).createUnconditionally();

    assertEquals(tooltip, newInfo.getToolTip());
  }
}
