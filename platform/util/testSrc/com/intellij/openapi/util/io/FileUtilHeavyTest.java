/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author Irina.Chernushina, lene
 */
public class FileUtilHeavyTest {
  private static File myTempDirectory;
  private static File myVisitorTestDirectory;
  private static File myFindTestDirectory;
  private static File myFindTestFirstFile;
  private static File myFindTestSecondFile;

  @BeforeClass
  public static void setUp() throws IOException {
    myTempDirectory = FileUtil.createTempDirectory("FileUtilHeavyTest.", ".tmp");

    myVisitorTestDirectory = IoTestUtil.createTestDir(myTempDirectory, "visitor_test_dir");
    File dir1 = IoTestUtil.createTestDir(myVisitorTestDirectory, "dir1");
    IoTestUtil.createTestFile(dir1, "1");
    IoTestUtil.createTestFile(dir1, "2");
    File dir2 = IoTestUtil.createTestDir(myVisitorTestDirectory, "dir2");
    IoTestUtil.createTestFile(dir2, "1");
    IoTestUtil.createTestFile(dir2, "2");
    File dir21 = IoTestUtil.createTestDir(dir2, "inner");
    IoTestUtil.createTestFile(dir21, "1");
    IoTestUtil.createTestFile(dir21, "2");

    myFindTestDirectory = IoTestUtil.createTestDir(myTempDirectory, "find_file_test_dir");
    myFindTestFirstFile = IoTestUtil.createTestFile(myFindTestDirectory, "first");
    myFindTestSecondFile = IoTestUtil.createTestFile(myFindTestDirectory, "second");
  }

  @AfterClass
  public static void tearDown() {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @Test
  public void testProcessSimple() {
    final Map<String, Integer> result = new HashMap<>();
    FileUtil.processFilesRecursively(myVisitorTestDirectory, file -> {
      Integer integer = result.get(file.getName());
      result.put(file.getName(), integer == null ? 1 : (integer + 1));
      return true;
    });

    assertEquals(6, result.size());
    assertEquals(1, result.get(myVisitorTestDirectory.getName()).intValue());
    assertEquals(3, result.get("1").intValue());
    assertEquals(3, result.get("2").intValue());
    assertEquals(1, result.get("dir1").intValue());
  }

  @Test
  public void testProcessStops() {
    final int[] cnt = new int[]{0};
    FileUtil.processFilesRecursively(myVisitorTestDirectory, file -> {
      ++cnt[0];
      return false;
    });

    assertEquals(1, cnt[0]);
  }

  @Test
  public void testProcessDirectoryFilter() {
    final Map<String, Integer> result = new HashMap<>();
    FileUtil.processFilesRecursively(myVisitorTestDirectory, file -> {
      Integer integer = result.get(file.getName());
      result.put(file.getName(), integer == null ? 1 : (integer + 1));
      return true;
    }, file -> ! "dir2".equals(file.getName()));

    assertEquals(5, result.size());
    assertEquals(1, result.get(myVisitorTestDirectory.getName()).intValue());
    assertEquals(1, result.get("1").intValue());
    assertEquals(1, result.get("2").intValue());
    assertEquals(1, result.get("dir1").intValue());
    assertEquals(1, result.get("dir2").intValue());
    assertNull(result.get("dir21"));
  }

  @Test
  public void nonExistingFileInNonExistentDirectory() {
    String path = FileUtil.findFileInProvidedPath("123", "zero");
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void nonExistingFileInDirectory() {
    String path = FileUtil.findFileInProvidedPath(myFindTestDirectory.getAbsolutePath(), "zero");
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void nonExistingFile() {
    String path = FileUtil.findFileInProvidedPath(myFindTestFirstFile.getAbsolutePath() + "123", myFindTestFirstFile.getName() + "123");
    assertTrue(StringUtil.isEmpty(path));
  }

  @Test
  public void existingFileInDirectory() {
    String path = FileUtil.findFileInProvidedPath(myFindTestDirectory.getAbsolutePath(), "first");
    assertEquals(path, myFindTestFirstFile.getAbsolutePath());
  }

  @Test
  public void existingFile() {
    String path = FileUtil.findFileInProvidedPath(myFindTestFirstFile.getAbsolutePath(), "first");
    assertEquals(path, myFindTestFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrderInDirectory() {
    String path = FileUtil.findFileInProvidedPath(myFindTestDirectory.getAbsolutePath(), "first", "second");
    assertEquals(path, myFindTestFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrderInDirectory2() {
    String path = FileUtil.findFileInProvidedPath(myFindTestDirectory.getAbsolutePath(), "second", "first");
    assertEquals(path, myFindTestSecondFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrder() {
    String path = FileUtil.findFileInProvidedPath(myFindTestFirstFile.getAbsolutePath(), "first", "second");
    assertEquals(path, myFindTestFirstFile.getAbsolutePath());
  }

  @Test
  public void twoFilesOrder2() {
    String path = FileUtil.findFileInProvidedPath(myFindTestFirstFile.getAbsolutePath(), "second", "first");
    assertEquals(path, myFindTestFirstFile.getAbsolutePath());
  }

  @Test
  public void testDeleteFail() throws IOException {
    File targetDir = IoTestUtil.createTestDir(myTempDirectory, "failed_delete");
    File file = IoTestUtil.createTestFile(targetDir, "file");

    if (SystemInfo.isWindows) {
      try (RandomAccessFile rw = new RandomAccessFile(file, "rw"); FileLock ignored = rw.getChannel().tryLock()) {
        assertFalse(FileUtil.delete(file));
      }
    }
    else {
      assertTrue(targetDir.setWritable(false, false));
      try {
        assertFalse(FileUtil.delete(file));
      }
      finally {
        assertTrue(targetDir.setWritable(true, true));
      }
    }
  }

  @Test
  public void testRepeatableOperation() throws IOException {
    abstract class CountableIOOperation implements FileUtilRt.RepeatableIOOperation<Boolean, IOException> {
      private int count = 0;

      @Override
      public Boolean execute(boolean lastAttempt) {
        count++;
        return stop(lastAttempt) ? true : null;
      }

      protected abstract boolean stop(boolean lastAttempt);
    }

    CountableIOOperation successful = new CountableIOOperation() {
      @Override protected boolean stop(boolean lastAttempt) { return true; }
    };
    FileUtilRt.doIOOperation(successful);
    assertEquals(1, successful.count);

    CountableIOOperation failed = new CountableIOOperation() {
      @Override protected boolean stop(boolean lastAttempt) { return false; }
    };
    FileUtilRt.doIOOperation(failed);
    assertEquals(10, failed.count);

    CountableIOOperation lastShot = new CountableIOOperation() {
      @Override protected boolean stop(boolean lastAttempt) { return lastAttempt; }
    };
    FileUtilRt.doIOOperation(lastShot);
    assertEquals(10, lastShot.count);
  }

  @Test
  public void testSymlinkDeletion() {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File targetDir = IoTestUtil.createTestDir(myTempDirectory, "lnk_del_test_1");
    IoTestUtil.createTestFile(targetDir, "file");
    File linkDir = IoTestUtil.createTestDir(myTempDirectory, "lnk_del_test_2");
    IoTestUtil.createTestFile(linkDir, "file");
    IoTestUtil.createSymLink(targetDir.getPath(), linkDir.getPath() + "/link");

    assertEquals(1, targetDir.list().length);
    FileUtil.delete(linkDir);
    assertFalse(linkDir.exists());
    assertEquals(1, targetDir.list().length);
  }

  @Test
  public void testJunctionDeletion() {
    assumeTrue(SystemInfo.isWindows);

    File targetDir = IoTestUtil.createTestDir(myTempDirectory, "jct_del_test_1");
    IoTestUtil.createTestFile(targetDir, "file");
    File linkDir = IoTestUtil.createTestDir(myTempDirectory, "jct_del_test_2");
    IoTestUtil.createTestFile(linkDir, "file");
    IoTestUtil.createJunction(targetDir.getPath(), linkDir.getPath() + "/link");

    assertEquals(1, targetDir.list().length);
    FileUtil.delete(linkDir);
    assertFalse(linkDir.exists());
    assertEquals(1, targetDir.list().length);
  }

  @Test
  public void testToCanonicalPathSymLinksAware() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File rootDir = IoTestUtil.createTestDir(myTempDirectory, "root");
    assertTrue(new File(rootDir, "dir1/dir2/dir3/dir4").mkdirs());

    String root = FileUtil.toSystemIndependentName(FileUtil.resolveShortWindowsName(rootDir.getPath()));

    // non-recursive link
    IoTestUtil.createSymLink(new File(rootDir, "dir1/dir2").getPath(), new File(rootDir, "dir1/dir2_link").getPath());
    // recursive links to a parent dir
    IoTestUtil.createSymLink(new File(rootDir, "dir1").getPath(), new File(rootDir, "dir1/dir1_link").getPath());

    // I) links should NOT be resolved when ../ stays inside the linked path
    // I.I) non-recursive links
    assertEquals(root + "/dir1/dir2_link", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/./", true));
    assertEquals(root + "/dir1/dir2_link", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/dir3/../", true));
    assertEquals(root + "/dir1/dir2_link/dir3", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/dir3/dir4/../", true));
    assertEquals(root + "/dir1/dir2_link", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/dir3/dir4/../../", true));
    assertEquals(root + "/dir1/dir2_link", FileUtil.toCanonicalPath(root + "/dir1/../dir1/dir2_link/dir3/../", true));

    // I.II) recursive links
    assertEquals(root + "/dir1/dir1_link", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/./", true));
    assertEquals(root + "/dir1/dir1_link", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/../", true));
    assertEquals(root + "/dir1/dir1_link/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/dir3/../", true));
    assertEquals(root + "/dir1/dir1_link", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/dir3/../../", true));
    assertEquals(root + "/dir1/dir1_link", FileUtil.toCanonicalPath(root + "/dir1/../dir1/dir1_link/dir2/../", true));

    // II) links should be resolved is ../ escapes outside

    // II.I) non-recursive links
    assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/../", true));
    assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/../dir2", true));
    assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/../../dir1/dir2", true));
    assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/dir3/../../dir2", true));
    assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir2_link/dir3/../../../dir1/dir2", true));
    assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/../dir1/dir2_link/../dir2", true));

    // II.I) recursive links
    // the rules seems to be different when ../ goes over recursive link:
    // * on Windows ../ goes to link's parent
    // * on Unix ../ goes to target's parent
    if (SystemInfo.isWindows) {
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../", true));
      assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../dir2", true));
      assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../../dir1/dir2", true));
      assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/../../dir2", true));
      assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/../../../dir1/dir2", true));
      assertEquals(root + "/dir1/dir2", FileUtil.toCanonicalPath(root + "/dir1/../dir1/dir1_link/../dir2", true));
    } else {
      assertEquals(root, FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../", true));
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../dir1", true));
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/../../root/dir1", true));
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/../../dir1", true));
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/dir1_link/dir2/../../../root/dir1", true));
      assertEquals(root + "/dir1", FileUtil.toCanonicalPath(root + "/dir1/../dir1/dir1_link/../dir1", true));
    }

    // some corner cases, behavior should be the same as the default FileUtil.toCanonicalPath
    assertEquals(FileUtil.toCanonicalPath("..", false), FileUtil.toCanonicalPath("..", true));
    assertEquals(FileUtil.toCanonicalPath("../", false),  FileUtil.toCanonicalPath("../", true));
    assertEquals(FileUtil.toCanonicalPath("/..", false),  FileUtil.toCanonicalPath("/..", true));
    assertEquals(FileUtil.toCanonicalPath("/../", false),  FileUtil.toCanonicalPath("/../", true));
  }

  @Test
  public void testCaseSensitivityDetection() throws IOException {
    String path = myFindTestFirstFile.getPath();
    assertEquals(SystemInfo.isFileSystemCaseSensitive, FileUtil.isFileSystemCaseSensitive(path));
  }

  @Test
  public void testFileRelativePath() {
    String relativePath = FileUtil.toSystemDependentName("relative/path.file");

    File existingDir = myTempDirectory;
    assertEquals(relativePath, FileUtil.getRelativePath(existingDir, new File(existingDir, relativePath)));

    File notExistingDirOrFile = new File("not/existing/path");
    assertEquals(relativePath, FileUtil.getRelativePath(notExistingDirOrFile, new File(notExistingDirOrFile, relativePath)));

    // FileUtil.getRelativePath(File, File) should have the same behavior then FileUtil.getRelativePath(String, String, char)
    File existingFile = IoTestUtil.createTestFile(existingDir, "foo.file");
    assertEquals(".." + File.separatorChar + relativePath,
                 FileUtil.getRelativePath(existingFile, new File(existingFile.getParent(), relativePath)));
  }
}