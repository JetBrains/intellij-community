// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HierarchicalFilePathComparatorTest extends TestCase {
  public void testInOneDirectory() {
    assertEquals(-1, compare("~/project/A.java", "~/project/B.java"));
    assertEquals(1, compare("~/project/Z.java", "~/project/B.java"));
  }

  public void testInDifferentDirs() {
    assertEquals(-1, compare("~/project/aaa/A.java", "~/project/bbb/B.java"));
    assertEquals(-1, compare("~/project/aaa/B.java", "~/project/zzz/A.java"));
    assertEquals(1, compare("~/project/zzz/A.java", "~/project/aaa/B.java"));
  }

  public void testInSubDir() {
    // all folders precede plain files
    assertEquals("Folders should precede plain files", -1, compare("~/project/dir/subdir/A.java", "~/project/dir/B.java"));
    assertEquals("Folders should precede plain files", -1, compare("~/project/dir/A.java", "~/project/B.java"));
    assertEquals("Folders should precede plain files", 1, compare("~/project/B.java", "~/project/dir/subdir/A.java"));
  }

  public void testEqualPaths() {
    assertEquals("Equal paths should compare to 0", 0, compare("~/project/A.java", "~/project/A.java"));
    assertEquals("Equal paths should compare to 0", 0, compare("~/project/aaa/A.java", "~/project/aaa/A.java"));
  }

  public void testRootPaths() {
    assertEquals(0, compare("/", "/"));
  }

  public void testSamePrefixFolder() {
    assertEquals(-1, compare("~/project/aaa/", "~/project/aaa-qwe/"));
    assertEquals(-1, compare("~/project/aaa/A.java", "~/project/aaa-qwe/A.java"));
    assertEquals(1, compare("~/project/zzz-qwe.java", "~/project/zzz/"));
  }

  public void testRootDirectory() {
    assertEquals(0, compare("A.java", "A.java"));
    assertEquals(0, compare("/aaa/", "/aaa/"));
    assertEquals(-1, compare("/aaa/", "/aaa-qwe/"));
    assertEquals(-1, compare("/aaa/", "/ZZ.java"));
  }

  public void testCaseIgnored() {
    assertEquals(-1, compare("a.java", "B.java"));
    assertEquals(-1, compare("A.java", "b.java"));
  }

  public void testAssociativeBug() {
    assertEquals(1, compare("/folder/aaa-qwerty/", "/folder/aaa/"));
    assertEquals(1, compare("/folder/aaa/.gitignore", "/folder/aaa/"));
    assertEquals(-1, compare("/folder/aaa/.gitignore", "/folder/aaa-qwerty/"));
    assertEquals(1, compare("/folder/aaa-qwerty/qwerty", "/folder/aaa/qwerty/"));
  }

  public void testTransitiveBug() {
    assertEquals(1, compare("/folder/abd", "/folder/abd/"));
    assertEquals(1, compare("/folder/abx/", "/folder/abd/"));
    assertEquals(-1, compare("/folder/abx/", "/folder/abd"));
  }

  public void testCaseSensitiveOrder() {
    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_SENSITIVE,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/a/Test1.txt_2",
      "/a/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_SENSITIVE,
      "/a/TEST3-1.TXT",
      "/a/TEST3-2.txt",
      "/a/Test3-12.TXT",
      "/a/Test3.TXT"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_SENSITIVE,
      "/Test2.txt/a",
      "/Test2_1.txt/a",
      "/Test2_12.txt/a",
      "/Test2_2.txt/b",
      "/Test2_22.txt/b"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_SENSITIVE,
      "/A/Test1.txt_2",
      "/A/Test1.txt_22",
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_SENSITIVE,
      "/B/Test1.txt_2",
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/b/Test1.txt_22"
    );
  }

  public void testCaseInsensitiveOrder() {
    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/a/Test1.txt_2",
      "/a/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/a/TEST3-1.TXT",
      "/a/Test3-12.TXT",
      "/a/TEST3-2.txt",
      "/a/Test3.TXT"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/Test2.txt/a",
      "/Test2_1.txt/a",
      "/Test2_12.txt/a",
      "/Test2_2.txt/b",
      "/Test2_22.txt/b"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/A/Test1.txt_2",
      "/A/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/B/Test1.txt_2",
      "/b/Test1.txt_22"
    );

    assertOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/A/B/a",
      "/a/b/a",
      "/a/b/a_2",
      "/A/B/a_2",
      "/A/B/a_3",
      "/a/b/a_3"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.CASE_INSENSITIVE,
      "/A/B/a_1",
      "/a/b/a_2",
      "/A/B_1/a_1",
      "/a/b_1/a_2",
      "/A/B_2/a_1",
      "/a/b_2/a_2"
    );
  }

  public void testNaturalOrder() {
    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_2",
      "/a/Test1.txt_12",
      "/a/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/a/Test3.TXT",
      "/a/TEST3-1.TXT",
      "/a/TEST3-2.txt",
      "/a/Test3-12.TXT"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/Test2.txt/a",
      "/Test2_1.txt/a",
      "/Test2_2.txt/b",
      "/Test2_12.txt/a",
      "/Test2_22.txt/b"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/A/Test1.txt_2",
      "/a/Test1.txt_12",
      "/A/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/a/Test1.txt",
      "/a/Test1.txt_1",
      "/a/Test1.txt_12",
      "/B/Test1.txt_2",
      "/b/Test1.txt_22"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/Test2.1.txt/a",
      "/Test2.1.txt/b",
      "/Test2_1.txt/a",
      "/Test2_1.txt/b"
    );

    assertStrictOrderedPaths(
      HierarchicalFilePathComparator.NATURAL,
      "/Test2!1.txt/b",
      "/Test2!2.txt/a",
      "/Test2.txt/a",
      "/Test2-1.txt/b",
      "/Test2-2.txt/a",
      "/Test2_1.txt/b",
      "/Test2_2.txt/a"
    );
  }

  public void testTransitive() {
    List<String> paths = Arrays
      .asList("/", "~", "/~", "/~/", "~/project/A.java", "~/project/B.java", "~/project/Z.java", "~/project/a.java", "~/project/b.java",
              "~/project/z.java", "/aaa/", "/aaa-qwe/", "/ZZ.java", "A.java", "~/project/zzz/", "~/project/zzz", "~/project/zzz-qwe.java",
              "~/project/zzz-qwe.java/", "~/project/zzz-qwe.java/test", "~/project/aaa/", "~/project/aaa", "~/project/aaa",
              "~/project/aaa a", "~/project/aaa a/", "~/project/aaa a/bb", "~/project/aaa-qwe/", "~/project/aaa-qwe", "~/project/aaa-qwE",
              "~/project/aaa-qWe", "~/project/aaa-qwE/", "~/project/aaa-qWe/", "~/project/aaa-qwe/A.java", "~/project/dir/subdir/A.java",
              "~/project/dir/B.java", "~/project/dir/A.java", "~/project/B.java", "/folder/abd", "/folder/abd/", "/folder/abx/",
              "/folder/abx", "/folder/aaa", "/folder/AAA", "/folder/aaa/", "/folder/aaa/", "/folder/aaa/.gitignore", "/folder/aaa-qwerty/",
              "/folder/aaa-qwerty/qwerty", "/folder/aaa/qwerty/", "/folder/aAa/", "/folder/aAa", "/folder/aaA/.gitignore",
              "/folder/Aaa-qwerty/", "/folder/aAa-qwerty/qwerty", "/folder/aAa/qwerty/", "/Test1", "/TEST1", "/Test1.txt", "/Test1.TXT_1",
              "/Test1.txt_2", "/Test1.txt_12", "/Test1.TXT_22", "/Test1.TXT 1", "/Test1.txt 2", "/Test1.txt 12", "/Test1.TXT 22",
              "/Test1.txt-1", "/Test1.TXT-2", "/Test1.txt-12", "/Test1.TXT-22", "/Test1_1.txt", "/Test1_2.txt", "/Test1_12.txt",
              "/TEST1 1.txt", "/TEST1 2.txt", "/Test1 12.txt", "/Test1.1.txt", "/Test1-2.txt", "/Test1-12.txt", "/a/Test1.txt",
              "/a/Test1.txt_1", "/a/Test1.txt_12", "/A/Test1.txt", "/A/Test1.txt_1", "/A/Test1.txt_12", "/B/Test1.txt_2", "/b/Test1.txt_22",
              "/b/Test1.txt_2", "/B/Test1.txt_22", "/Test1 12.txt/a/", "/Test1.1.txt/a/", "/Test1-2.txt/a/", "/Test1-12.txt/a/",
              "/Test1 12.txt/A/", "/Test1.1.txt/A/", "/Test1-2.txt/A/", "/Test1-12.txt/A/", "/Test1 12.txt/b/", "/Test1.1.txt/B/",
              "/Test1-2.txt/b/", "/Test1-12.txt/B/", "/Test1-12.txt", "/Test1-12.txt/", "/Test1!12.txt", "/Test1.12.txt", "/Test1/12.txt",
              "/Test1\\12.txt", "/Test1+12.txt", "/Test1A12.txt");
    List<FilePath> filePaths = ContainerUtil.map(paths, it -> filePath(it));

    assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.NATURAL);
    assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.CASE_SENSITIVE);
    assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.CASE_INSENSITIVE);
  }

  public void testNaturalPerformance() {
    List<FilePath> filePaths = generatePerformanceTestFilePaths();
    PlatformTestUtil.startPerformanceTest("Natural hierarchical comparator", 7000, () -> {
      assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.NATURAL);
    }).assertTiming();
  }

  public void testCaseInsensitivePerformance() {
    List<FilePath> filePaths = generatePerformanceTestFilePaths();
    PlatformTestUtil.startPerformanceTest("Case-insensitive hierarchical comparator", 4000, () -> {
      assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.CASE_INSENSITIVE);
    }).assertTiming();
  }

  public void testCaseSensitivePerformance() {
    List<FilePath> filePaths = generatePerformanceTestFilePaths();
    PlatformTestUtil.startPerformanceTest("Case-sensitive hierarchical comparator", 3000, () -> {
      assertComparisonContractNotViolated(filePaths, HierarchicalFilePathComparator.CASE_SENSITIVE);
    }).assertTiming();
  }

  private static List<FilePath> generatePerformanceTestFilePaths() {
    Random rng = new Random(0);
    int totalPaths = 300;
    int maxLength = 5;
    List<String> chunks = Arrays.asList("Test1.txt", "Test2.txt", "TEST1.TXT", "TEST2.txt", "Test1-txt", "Test2-txt",
                                        "Test1", "Test2", "Test1A1", "Test1b1", "Test1_txt", "Test2_txt",
                                        "1", "2", "a", "b", "A", "B", "HierarchicalFilePathComparatorHierarchicalFilePathComparator");

    List<FilePath> result = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < totalPaths; i++) {
      int length = rng.nextInt(maxLength) + 1;

      sb.setLength(0);
      for (int j = 0; j < length; j++) {
        sb.append("/");
        sb.append(chunks.get(rng.nextInt(chunks.size())));
      }
      if (rng.nextBoolean()) {
        sb.append("/");
      }
      result.add(filePath(sb.toString()));
    }
    return result;
  }

  private static void assertStrictOrderedPaths(@NotNull Comparator<FilePath> comparator, String @NotNull ... paths) {
    assertOrderedPaths(Arrays.asList(paths), comparator, true);
  }

  private static void assertOrderedPaths(@NotNull Comparator<FilePath> comparator, String @NotNull ... paths) {
    assertOrderedPaths(Arrays.asList(paths), comparator, false);
  }

  private static void assertOrderedPaths(@NotNull List<String> paths, @NotNull Comparator<FilePath> comparator, boolean strict) {
    List<FilePath> filePaths = ContainerUtil.map(paths, it -> filePath(it));
    List<FilePath> sortedFilePaths = ContainerUtil.sorted(filePaths, comparator);

    if (strict) {
      TreeSet<FilePath> pathsSet = new TreeSet<>(comparator);
      pathsSet.addAll(filePaths);
      assertEquals(filePaths.size(), pathsSet.size());
    }

    assertEquals(filePaths, sortedFilePaths);
    assertComparisonContractNotViolated(filePaths, comparator);
  }

  private static void assertComparisonContractNotViolated(@NotNull List<FilePath> filePaths, @NotNull Comparator<FilePath> comparator) {
    PlatformTestUtil.assertComparisonContractNotViolated(filePaths,
                                                         (path1, path2) -> compare(path1, path2, comparator),
                                                         (path1, path2) -> equals(path1, path2, comparator));
  }

  private static boolean equals(@NotNull FilePath path1, @NotNull FilePath path2, @NotNull Comparator<FilePath> comparator) {
    if (path1.isDirectory() != path2.isDirectory()) return false;
    boolean ignoreCase = comparator == HierarchicalFilePathComparator.NATURAL ||
                         comparator == HierarchicalFilePathComparator.CASE_INSENSITIVE;
    return ignoreCase ? StringUtil.equalsIgnoreCase(path1.getPath(), path2.getPath())
                      : StringUtil.equals(path1.getPath(), path2.getPath());
  }

  private static int compare(@NotNull String path1, @NotNull String path2) {
    int compare1 = compare(filePath(path1), filePath(path2), HierarchicalFilePathComparator.NATURAL);
    int compare2 = compare(filePath(path2), filePath(path1), HierarchicalFilePathComparator.NATURAL);
    assert compare1 == -compare2;
    return compare1;
  }

  private static int compare(FilePath path1, FilePath path2, @NotNull Comparator<FilePath> comparator) {
    return Integer.signum(comparator.compare(path1, path2));
  }

  @NotNull
  private static FilePath filePath(@NotNull String path) {
    return new LocalFilePath(path, StringUtil.endsWithChar(path, '/'));
  }
}
