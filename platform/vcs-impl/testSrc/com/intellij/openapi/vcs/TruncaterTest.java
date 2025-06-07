// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

public class TruncaterTest extends LightPlatformCodeInsightFixture4TestCase {
  @Test
  public void testTruncationByWords() {
    FontMetrics metrics = new JLabel().getFontMetrics(new Font(Font.DIALOG, Font.PLAIN, 10));
    String longString = "This is a long string that needs to be truncated. ".repeat(10000);
    String truncatedString = CommittedChangeListRenderer.truncateDescription(longString, metrics, 100);
    assertEquals("This is a long string", truncatedString);
  }

  @Test
  public void testTruncationByWordsWithoutSpace() {
    FontMetrics metrics = new JLabel().getFontMetrics(new Font(Font.DIALOG, Font.PLAIN, 10));
    String longString = "Thisisalongstringwithoutspaces".repeat(100);
    String truncatedString = CommittedChangeListRenderer.truncateDescription(longString, metrics, 100000);
    assertEquals("Thisisalongstringwithoutspaces".repeat(100), truncatedString);
  }

}
