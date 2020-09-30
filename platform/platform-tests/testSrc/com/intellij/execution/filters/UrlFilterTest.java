// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UrlFilterTest extends BasePlatformTestCase {

  public void testSingleFileHyperlink() {
    UrlFilter filter = new UrlFilter(getProject());
    assertSingleFileHyperlink(applyFilter(filter, " at file:///home/file.txt"), 4, 25);
    assertSingleFileHyperlink(applyFilter(filter, "file:///home/file.txt"), 0, 21);
    assertSingleFileHyperlink(applyFilter(filter, "text before file:///home/file.txt:3 some test after"), 12, 35);
    assertSingleFileHyperlink(applyFilter(filter, "text before file:///home/file.txt:3:30 some test after"), 12, 38);
  }

  public void testSingleBrowserHyperlink() {
    assertSingleBrowserHyperlink(applyFilter(new UrlFilter(), "file:///home/file.txt"), 0, 21);
    UrlFilter filter = new UrlFilter(getProject());
    assertSingleBrowserHyperlink(applyFilter(filter, " at http://test.com"), 4, 19);
    assertSingleBrowserHyperlink(applyFilter(filter, "http://test.com?q=text&"), 0, 23);
  }

  public void testMultipleHyperlinks() {
    UrlFilter filter = new UrlFilter(getProject());
    assertHyperlinks(applyFilter(filter, "file:///home/file1.txt -> file:///home/file2.txt"), Arrays.asList(
      new LinkInfo(0, 22, true), new LinkInfo(26, 48, true)
    ));
  }

  public void testPerformanceSimple() {
    UrlFilter filter = new UrlFilter(getProject());
    List<LinkInfo> expected = Arrays.asList(new LinkInfo(7, 30, true), new LinkInfo(34, 62, true));
    PlatformTestUtil.startPerformanceTest("Find file hyperlinks", 3000, () -> {
      for (int i = 0; i < 100_000; i++) {
        Filter.Result result = applyFilter(filter, "before file:///home/file.txt:3 -> file:///home/result.txt:3:30 after");
        assertHyperlinks(result, expected);
      }
    }).assertTiming();
  }

  private static Filter.Result applyFilter(@NotNull UrlFilter filter, @NotNull String line) {
    return filter.applyFilter(line, line.length());
  }

  private static void assertSingleFileHyperlink(@NotNull Filter.Result result, int highlightStartOffset, int highlightEndOffset) {
    assertHyperlinks(result, Collections.singletonList(new LinkInfo(highlightStartOffset, highlightEndOffset, true)));
  }

  private static void assertSingleBrowserHyperlink(@NotNull Filter.Result result, int highlightStartOffset, int highlightEndOffset) {
    assertHyperlinks(result, Collections.singletonList(new LinkInfo(highlightStartOffset, highlightEndOffset, false)));
  }

  private static void assertHyperlinks(@NotNull Filter.Result result, List<LinkInfo> infos) {
    List<Filter.ResultItem> items = result.getResultItems();
    assertEquals(infos.size(), items.size());
    for (int i = 0; i < infos.size(); i++) {
      assertHyperlink(items.get(i), infos.get(i));
    }
  }

  private static void assertHyperlink(@NotNull Filter.ResultItem actualItem, @NotNull LinkInfo expectedLinkInfo) {
    assertEquals(expectedLinkInfo.myHighlightStartOffset, actualItem.getHighlightStartOffset());
    assertEquals(expectedLinkInfo.myHighlightEndOffset, actualItem.getHighlightEndOffset());
    if (expectedLinkInfo.myIsFileHyperLink) {
      assertInstanceOf(actualItem.getHyperlinkInfo(), LazyFileHyperlinkInfo.class);
    }
    else {
      assertInstanceOf(actualItem.getHyperlinkInfo(), BrowserHyperlinkInfo.class);
    }
  }

  private static final class LinkInfo {
    private final int myHighlightStartOffset;
    private final int myHighlightEndOffset;
    private final boolean myIsFileHyperLink;

    private LinkInfo(int highlightStartOffset, int highlightEndOffset, boolean isFileHyperLink) {
      myHighlightStartOffset = highlightStartOffset;
      myHighlightEndOffset = highlightEndOffset;
      myIsFileHyperLink = isFileHyperLink;
    }
  }
}
