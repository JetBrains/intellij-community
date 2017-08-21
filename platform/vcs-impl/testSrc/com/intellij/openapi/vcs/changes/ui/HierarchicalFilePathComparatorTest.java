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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

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

  public void testEmptyPaths() {
    assertEquals(0, compare("", ""));
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

  public void testTransitive() {
    Collection<String> paths = ContainerUtil.list(
      "",
      "/",
      "~",
      "/~",
      "/~/",
      "~/project/A.java",
      "~/project/B.java",
      "~/project/Z.java",
      "~/project/a.java",
      "~/project/b.java",
      "~/project/z.java",
      "/aaa/",
      "/aaa-qwe/",
      "/ZZ.java",
      "A.java",
      "~/project/zzz/",
      "~/project/zzz",
      "~/project/zzz-qwe.java",
      "~/project/zzz-qwe.java/",
      "~/project/zzz-qwe.java/test",
      "~/project/aaa/",
      "~/project/aaa",
      "~/project/aaa",
      "~/project/aaa a",
      "~/project/aaa a/",
      "~/project/aaa a/bb",
      "~/project/aaa-qwe/",
      "~/project/aaa-qwe",
      "~/project/aaa-qwE",
      "~/project/aaa-qWe",
      "~/project/aaa-qwE/",
      "~/project/aaa-qWe/",
      "~/project/aaa-qwe/A.java",
      "~/project/dir/subdir/A.java",
      "~/project/dir/B.java",
      "~/project/dir/A.java",
      "~/project/B.java",
      "/folder/abd",
      "/folder/abd/",
      "/folder/abx/",
      "/folder/abx",
      "/folder/aaa",
      "/folder/AAA",
      "/folder/aaa/",
      "/folder/aaa/",
      "/folder/aaa/.gitignore",
      "/folder/aaa-qwerty/",
      "/folder/aaa-qwerty/qwerty",
      "/folder/aaa/qwerty/",
      "/folder/aAa/",
      "/folder/aAa",
      "/folder/aaA/.gitignore",
      "/folder/Aaa-qwerty/",
      "/folder/aAa-qwerty/qwerty",
      "/folder/aAa/qwerty/"
    );

    List<FilePath> filePaths = ContainerUtil.map(paths, it -> filePath(it));

    assertComparisonContractNotViolated(filePaths, true);
    assertComparisonContractNotViolated(filePaths, false);
  }

  private static void assertComparisonContractNotViolated(@NotNull List<FilePath> paths, boolean ignoreCase) {
    PlatformTestUtil.assertComparisonContractNotViolated(paths,
                                                         (path1, path2) -> compare(path1, path2, ignoreCase),
                                                         (path1, path2) -> equals(path1, path2, ignoreCase));
  }

  private static boolean equals(@NotNull FilePath path1, @NotNull FilePath path2, boolean ignoreCase) {
    if (path1.isDirectory() != path2.isDirectory()) return false;
    return ignoreCase || !SystemInfo.isFileSystemCaseSensitive
           ? StringUtil.equalsIgnoreCase(path1.getPath(), path2.getPath())
           : StringUtil.equals(path1.getPath(), path2.getPath());
  }

  private static int compare(@NotNull String path1, @NotNull String path2) {
    int compare1 = compare(filePath(path1), filePath(path2));
    int compare2 = compare(filePath(path2), filePath(path1));
    assert compare1 == -compare2;
    return compare1;
  }

  private static int compare(FilePath path1, FilePath path2) {
    return compare(path1, path2, true);
  }

  private static int compare(FilePath path1, FilePath path2, boolean ignoreCase) {
    HierarchicalFilePathComparator comparator = ignoreCase
                                                ? HierarchicalFilePathComparator.IGNORE_CASE
                                                : HierarchicalFilePathComparator.SYSTEM_CASE_SENSITIVE;
    return Integer.signum(comparator.compare(path1, path2));
  }

  @NotNull
  private static FilePath filePath(@NotNull String path) {
    return new LocalFilePath(path, StringUtil.endsWithChar(path, '/'));
  }
}
