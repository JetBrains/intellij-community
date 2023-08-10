// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UrlFilterTest extends BasePlatformTestCase {

  private UrlFilter myFilter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFilter = new UrlFilter(getProject());
  }


  public void testSingleFileHyperlink() {
    assertFileHyperlink(" at file:///home/file.txt", 4, 25, "/home/file.txt", 1, 1);
    assertFileHyperlink("file:///home/file.txt", 0, 21, "/home/file.txt", 1, 1);
    assertFileHyperlink("text before file:///home/file.txt:3 some test after", 12, 35, "/home/file.txt", 3, 1);
    assertFileHyperlink("text before file:///home/file.txt:3:30 some test after", 12, 38, "/home/file.txt", 3, 30);
    assertFileHyperlink("Click file:///C:/Users/user/file.js:12:40", 6, 41, "C:/Users/user/file.js", 12, 40);
    assertFileHyperlink("See file:////wsl$/Ubuntu-20.04/projects/report.txt:4",
                        4, 52, "//wsl$/Ubuntu-20.04/projects/report.txt", 4, 1);
  }

  public void testSingleBrowserHyperlink() {
    assertBrowserHyperlink("http://test.com", 0, 15);
    assertBrowserHyperlink(" at http://test.com", 4, 19);
    assertBrowserHyperlink("http://test.com?q=text&", 0, 23);
  }

  public void testMultipleHyperlinks() {
    assertHyperlinks(applyFilter("file:///home/file1.txt -> file:///home/file2.txt"), List.of(
      new FileLinkInfo(0, 22, "/home/file1.txt", 1, 1), new FileLinkInfo(26, 48, "/home/file2.txt", 1, 1)
    ));
  }

  public void testPerformanceSimple() {
    List<LinkInfo> expected = List.of(new FileLinkInfo(7, 30, "/home/file.txt", 3, 1),
                                      new FileLinkInfo(34, 62, "/home/result.txt", 3, 30));
    PlatformTestUtil.startPerformanceTest("Find file hyperlinks", 3000, () -> {
      for (int i = 0; i < 100_000; i++) {
        Filter.Result result = applyFilter("before file:///home/file.txt:3 -> file:///home/result.txt:3:30 after");
        assertHyperlinks(result, expected);
      }
    }).assertTiming();
  }

  public void testUrlEncodedFileLinks() {
    assertFileHyperlink("e file:///home/path%20with%20space/file.kt:3 Expecting an expression", 2, 44,
                        "/home/path with space/file.kt", 3, 1);
    assertFileHyperlink("w file:///home/wrongly%EncodedPath/file.kt:3:10 Variable 'q' is never used", 2, 47,
                        "/home/wrongly%EncodedPath/file.kt", 3, 10);
    assertFileHyperlink("Click file:////wsl$/Ubuntu-20.04/path-test-gradle%206/src/main/kotlin/base/Starter.kt:4:10",
                        6, 90, "//wsl$/Ubuntu-20.04/path-test-gradle 6/src/main/kotlin/base/Starter.kt", 4, 10);
  }

  private Filter.Result applyFilter(@NotNull String line) {
    return myFilter.applyFilter(line, line.length());
  }

  private void assertFileHyperlink(@NotNull String text, int highlightStartOffset, int highlightEndOffset,
                                   @NotNull String filePath, int line, int column) {
    assertHyperlinks(applyFilter(text),
                     List.of(new FileLinkInfo(highlightStartOffset, highlightEndOffset, filePath, line, column)));
  }

  private void assertBrowserHyperlink(@NotNull String text, int highlightStartOffset, int highlightEndOffset) {
    assertHyperlinks(applyFilter(text), List.of(new LinkInfo(highlightStartOffset, highlightEndOffset)));
  }

  private static void assertHyperlinks(@NotNull Filter.Result result, List<LinkInfo> infos) {
    List<Filter.ResultItem> items = result.getResultItems();
    assertEquals(infos.size(), items.size());
    for (int i = 0; i < infos.size(); i++) {
      assertHyperlink(items.get(i), infos.get(i));
    }
  }

  private static void assertHyperlink(@NotNull Filter.ResultItem actualItem, @NotNull UrlFilterTest.LinkInfo expectedLinkInfo) {
    assertEquals(expectedLinkInfo.myHighlightStartOffset, actualItem.getHighlightStartOffset());
    assertEquals(expectedLinkInfo.myHighlightEndOffset, actualItem.getHighlightEndOffset());
    if (expectedLinkInfo instanceof FileLinkInfo) {
      assertInstanceOf(actualItem.getHyperlinkInfo(), UrlFilter.FileUrlHyperlinkInfo.class);
      assertFileLink((FileLinkInfo)expectedLinkInfo, (UrlFilter.FileUrlHyperlinkInfo)actualItem.getHyperlinkInfo());
    }
    else {
      assertInstanceOf(actualItem.getHyperlinkInfo(), OpenUrlHyperlinkInfo.class);
    }
  }

  private static void assertFileLink(@NotNull FileLinkInfo expected, @NotNull UrlFilter.FileUrlHyperlinkInfo actual) {
    assertEquals(expected.myFilePath, actual.myFilePath);
    assertEquals(expected.myLine, actual.myDocumentLine + 1);
    assertEquals(expected.myColumn, actual.myDocumentColumn + 1);
  }

  private static final class FileLinkInfo extends LinkInfo {
    private final String myFilePath;
    private final int myLine;
    private final int myColumn;

    private FileLinkInfo(int highlightStartOffset, int highlightEndOffset, @NotNull String filePath, int line, int column) {
      super(highlightStartOffset, highlightEndOffset);
      myFilePath = filePath;
      myLine = line;
      myColumn = column;
    }
  }

  private static class LinkInfo {
    private final int myHighlightStartOffset;
    private final int myHighlightEndOffset;

    private LinkInfo(int highlightStartOffset, int highlightEndOffset) {
      myHighlightStartOffset = highlightStartOffset;
      myHighlightEndOffset = highlightEndOffset;
    }
  }
}
