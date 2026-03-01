// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UrlFilterTest extends BasePlatformTestCase {

  private UrlFilter myFilter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFilter = new UrlFilter(getProject());
  }

  public void testRfcSamples() {
    //https://datatracker.ietf.org/doc/html/rfc8089#appendix-F
    assertFileHyperlink("file:///path/to/file", 0, 20, "/path/to/file", -1, -1);
    assertFileHyperlink("file:/path/to/file", 0, 18, "/path/to/file", -1, -1);
    assertFileHyperlink("file:c:/path/to/file", 0, 20, "c:/path/to/file", -1, -1);
    assertFileHyperlink("file:/c:/path/to/file", 0, 21, "c:/path/to/file", -1, -1);
    assertFileHyperlink("file:///c:/path/to/file", 0, 23, "c:/path/to/file", -1, -1);
    assertBrowserHyperlink("file://host.example.com/path/to/file", 0, 36);
    //These are supposed to be browser links, but that would break links to local files in WSL
    assertFileHyperlink("file:////host.example.com/path/to/file", 0, 38, "//host.example.com/path/to/file", -1, -1);
  }

  public void testSingleFileHyperlink() {
    assertFileHyperlink(" at file:///home/file.txt", 4, 25, "/home/file.txt", -1, -1);
    assertFileHyperlink("file:///home/file.txt", 0, 21, "/home/file.txt", -1, -1);
    assertFileHyperlink("text before file:///home/file.txt:3 some test after", 12, 35, "/home/file.txt", 3, -1);
    assertFileHyperlink("text before file:///home/file.txt:3:30 some test after", 12, 38, "/home/file.txt", 3, 30);
    assertFileHyperlink("Click file:///C:/Users/user/file.js:12:40", 6, 41, "C:/Users/user/file.js", 12, 40);
    assertFileHyperlink("Click (file:///C:/Users/user/file.js:12:40)", 7, 42, "C:/Users/user/file.js", 12, 40);
    assertFileHyperlink("Click file:///C:/Users/user/file.js(1)", 6, 35, "C:/Users/user/file.js", -1, -1);
    assertFileHyperlink("See file:////wsl$/Ubuntu-20.04/projects/report.txt:4",
                        4, 52, "//wsl$/Ubuntu-20.04/projects/report.txt", 4, -1);
  }

  public void testMinimalFileProtocol() {
    assertFileHyperlink("Click file:/path/to/file.diff:2:4 to see the difference", 6, 33, "/path/to/file.diff", 2, 4);
    assertFileHyperlink("file:/path/to/file.txt", 0, 22, "/path/to/file.txt", -1, -1);
  }

  public void testWindowsPathsWithBackslashes() {
    assertFileHyperlink("file:///D:\\Projects\\file.txt", 0, 28, "D:\\Projects\\file.txt", -1, -1);
    assertFileHyperlink("Click file:/C:\\Users\\user\\file.js:12:40", 6, 39, "C:\\Users\\user\\file.js", 12, 40);
    assertFileHyperlink("Error at file:///C:\\Windows\\System32\\config.sys:5", 9, 49, "C:\\Windows\\System32\\config.sys", 5, -1);
    assertFileHyperlink("See file:/E:\\workspace\\test.kt:10 for details", 4, 33, "E:\\workspace\\test.kt", 10, -1);
    assertFileHyperlink("file:/C:\\config.ini", 0, 19, "C:\\config.ini", -1, -1);
    assertFileHyperlink("file:///E:\\boot.log:1", 0, 21, "E:\\boot.log", 1, -1);

    assertFileHyperlink("file:/C:\\my-project\\src_main\\file.test.js", 0, 41, "C:\\my-project\\src_main\\file.test.js", -1, -1);
    assertFileHyperlink("file:///D:\\data.backup\\file_v2.0.txt:5:10", 0, 41, "D:\\data.backup\\file_v2.0.txt", 5, 10);

    assertFileHyperlink("file:/C:\\Projects\\", 0, 18, "C:\\Projects\\", -1, -1);
    assertFileHyperlink("file:///D:\\temp\\build\\", 0, 22, "D:\\temp\\build\\", -1, -1);
  }

  public void testSpecialCharactersInPaths() {
    //We do not filter bad Windows paths to avoid regex complexity
    assertFileHyperlink("file:/C:\\bad*name\\file.txt", 0, 26, "C:\\bad*name\\file.txt", -1, -1);
    assertFileHyperlink("file:/D:\\dir>output\\result.dat", 0, 30, "D:\\dir>output\\result.dat", -1, -1);
    assertFileHyperlink("file:///C:\\pipe|name\\data.bin:5", 0, 31, "C:\\pipe|name\\data.bin", 5, -1);
    assertFileHyperlink("file:///home/test[1].txt", 0, 24, "/home/test[1].txt", -1, -1);
  }

  @SuppressWarnings("NonAsciiCharacters")
  public void testInternationalCharactersInPaths() {
    assertFileHyperlink("file:///home/користувач/файл.txt", 0, 32, "/home/користувач/файл.txt", -1, -1);
    assertFileHyperlink("file:/C:\\書類\\プロジェクト\\コード.java:20", 0, 30, "C:\\書類\\プロジェクト\\コード.java", 20, -1);
    assertFileHyperlink("file:///home/用户/文件.txt", 0, 22, "/home/用户/文件.txt", -1, -1);
  }

  public void testNoFileHyperlink() {
    assertBrowserHyperlink("file://path/to/file.txt", 0, 23);
    assertBrowserHyperlink("file://C:\\path\\to\\file.txt", 0, 26);
    assertBrowserHyperlink("file://192.168.1.50/path/to/file.txt", 0, 36);
  }

  public void testSingleBrowserHyperlink() {
    assertBrowserHyperlink("http://test.com", 0, 15);
    assertBrowserHyperlink(" at http://test.com", 4, 19);
    assertBrowserHyperlink("http://test.com?q=text&", 0, 23);
  }

  public void testMultipleHyperlinks() {
    assertHyperlinks(applyFilter("file:///home/file1.txt -> file:///home/file2.txt"), List.of(
      new FileLinkInfo(0, 22, "/home/file1.txt", -1, -1),
      new FileLinkInfo(26, 48, "/home/file2.txt", -1, -1)
    ));
  }

  public void testMinimalRepresentationFileLinks() {
    assertBrowserHyperlink("file:sample.txt", 0, 0);
    assertFileHyperlink("file:sample.txt", 0, 0, "", -1, -1);
    assertFileHyperlink("file:C:\\Windows\\System32\\config.sys", 0, 35, "C:\\Windows\\System32\\config.sys", -1, -1);
    assertFileHyperlink("file:C:/path/to/file.txt", 0, 24, "C:/path/to/file.txt", -1, -1);
  }

  @PerformanceUnitTest
  public void testPerformanceSimple() {
    List<LinkInfo> expected = List.of(new FileLinkInfo(7, 30, "/home/file.txt", 3, -1),
                                      new FileLinkInfo(34, 62, "/home/result.txt", 3, 30));
    Benchmark.newBenchmark("Find file hyperlinks", () -> {
      for (int i = 0; i < 100_000; i++) {
        Filter.Result result = applyFilter("before file:///home/file.txt:3 -> file:///home/result.txt:3:30 after");
        assertHyperlinks(result, expected);
      }
    }).start();
  }

  public void testUrlEncodedFileLinks() {
    assertFileHyperlink("e file:///home/path%20with%20space/file.kt:3 Expecting an expression", 2, 44,
                        "/home/path with space/file.kt", 3, -1);
    assertFileHyperlink("w file:///home/wrongly%EncodedPath/file.kt:3:10 Variable 'q' is never used", 2, 47,
                        "/home/wrongly%EncodedPath/file.kt", 3, 10);
    assertFileHyperlink("Click file:////wsl$/Ubuntu-20.04/path-test-gradle%206/src/main/kotlin/base/Starter.kt:4:10",
                        6, 90, "//wsl$/Ubuntu-20.04/path-test-gradle 6/src/main/kotlin/base/Starter.kt", 4, 10);
  }

  public void testUrlAndFileInOneString() {
    assertHyperlinks(applyFilter(
                       "w: file:///Users/kmp-app-march/shared/build.gradle.kts:9:13: 'kotlinOptions(KotlinJvmOptions.() -> Unit): Unit' is deprecated. Please migrate to the compilerOptions DSL. More details are here: https://kotl.in/u1r8ln\n\n"),
                     List.of(
                       new FileLinkInfo(3, 59, "/Users/kmp-app-march/shared/build.gradle.kts", 9, 13),
                       new LinkInfo(193, 215)
                     ));
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

  private static void assertHyperlinks(@Nullable Filter.Result result, List<LinkInfo> infos) {
    if (result == null) {
      for (int i = 0; i < infos.size(); i++) {
        var info = infos.get(i);
        assertEquals(0, info.myHighlightEndOffset);
        assertEquals(0, info.myHighlightStartOffset);
      }
    }
    else {
      List<Filter.ResultItem> items = result.getResultItems();
      assertEquals(infos.size(), items.size());
      for (int i = 0; i < infos.size(); i++) {
        assertHyperlink(items.get(i), infos.get(i));
      }
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
    assertEquals(expected.myFilePath, actual.filePath);
    assertEquals(expected.myLine, actual.documentLine == -1 ? -1 : actual.documentLine + 1);
    assertEquals(expected.myColumn, actual.documentColumn == -1 ? -1 : actual.documentColumn + 1);
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
