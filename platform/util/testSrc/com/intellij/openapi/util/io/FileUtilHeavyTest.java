// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author Irina.Chernushina, lene
 */
public class FileUtilHeavyTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testProcessSimple() throws IOException {
    setupVisitorTestDirectories();

    Map<String, Integer> result = new HashMap<>();
    FileUtil.processFilesRecursively(tempDir.getRoot(), file -> {
      Integer integer = result.get(file.getName());
      result.put(file.getName(), integer == null ? 1 : (integer + 1));
      return true;
    });

    assertEquals(6, result.size());
    assertEquals(1, result.get(tempDir.getRoot().getName()).intValue());
    assertEquals(3, result.get("1").intValue());
    assertEquals(3, result.get("2").intValue());
    assertEquals(1, result.get("dir1").intValue());
  }

  @Test
  public void testProcessStops() throws IOException {
    setupVisitorTestDirectories();

    int[] cnt = {0};
    FileUtil.processFilesRecursively(tempDir.getRoot(), file -> {
      ++cnt[0];
      return false;
    });

    assertEquals(1, cnt[0]);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testProcessDirectoryFilter() throws IOException {
    setupVisitorTestDirectories();

    Map<String, Integer> result = new HashMap<>();
    FileUtil.processFilesRecursively(tempDir.getRoot(), file -> {
      Integer integer = result.get(file.getName());
      result.put(file.getName(), integer == null ? 1 : (integer + 1));
      return true;
    }, file -> ! "dir2".equals(file.getName()));

    assertEquals(5, result.size());
    assertEquals(1, result.get(tempDir.getRoot().getName()).intValue());
    assertEquals(1, result.get("1").intValue());
    assertEquals(1, result.get("2").intValue());
    assertEquals(1, result.get("dir1").intValue());
    assertEquals(1, result.get("dir2").intValue());
    assertNull(result.get("dir21"));
  }

  private void setupVisitorTestDirectories() throws IOException {
    tempDir.newFile("dir1/1");
    tempDir.newFile("dir1/2");
    tempDir.newFile("dir2/1");
    tempDir.newFile("dir2/2");
    tempDir.newFile("dir2/inner/1");
    tempDir.newFile("dir2/inner/2");
  }

  @Test
  public void nonExistingFileInNonExistentDirectory() {
    assertThat(FileUtil.findFileInProvidedPath("123", "zero")).isNullOrEmpty();
  }

  @Test
  public void nonExistingFileInDirectory() {
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "zero")).isNullOrEmpty();
  }

  @Test
  public void nonExistingFile() throws IOException {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath() + "123", first.getName() + "123")).isNullOrEmpty();
  }

  @Test
  public void existingFileInDirectory() throws IOException {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "first")).isEqualTo(first.getPath());
  }

  @Test
  public void existingFile() throws IOException {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrderInDirectory() throws IOException {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrderInDirectory2() throws IOException {
    tempDir.newFile("first");
    File second = tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "second", "first")).isEqualTo(second.getPath());
  }

  @Test
  public void twoFilesOrder() throws IOException {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrder2() throws IOException {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void testDeleteFail() throws IOException {
    File targetDir = tempDir.newFolder("dir");
    File file = tempDir.newFile("dir/file");

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
  public void testSymlinkDeletion() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    File targetDir = tempDir.newFolder("target");
    File targetFile = tempDir.newFile("target/file");
    File directDirLink = new File(tempDir.getRoot(), "dirLink");
    Files.createSymbolicLink(directDirLink.toPath(), targetDir.toPath());
    File directFileLink = new File(tempDir.getRoot(), "fileLink");
    Files.createSymbolicLink(directFileLink.toPath(), targetFile.toPath());
    File linkParentDir = tempDir.newFolder("linkParent");
    Files.createSymbolicLink(new File(linkParentDir, "link").toPath(), targetDir.toPath());

    FileUtil.delete(directFileLink);
    FileUtil.delete(directDirLink);
    FileUtil.delete(linkParentDir);

    assertThat(directFileLink).doesNotExist();
    assertThat(directDirLink).doesNotExist();
    assertThat(linkParentDir).doesNotExist();
    assertThat(targetFile).exists();
  }

  @Test
  public void testJunctionDeletion() throws IOException {
    assumeTrue("Windows-only", SystemInfo.isWindows);

    File targetDir = tempDir.newFolder("target");
    File targetFile = tempDir.newFile("target/file");
    File directDirLink = new File(tempDir.getRoot(), "dirLink");
    IoTestUtil.createJunction(targetDir.getPath(), directDirLink.getPath());
    File linkParentDir = tempDir.newFolder("linkParent");
    IoTestUtil.createJunction(targetDir.getPath(), new File(linkParentDir, "link").getPath());

    FileUtil.delete(directDirLink);
    FileUtil.delete(linkParentDir);

    assertThat(directDirLink).doesNotExist();
    assertThat(linkParentDir).doesNotExist();
    assertThat(targetFile).exists();
  }

  @Test
  public void nioDeletion() throws IOException {
    try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build(FileUtilHeavyTest.class.getSimpleName())) {
      Path dir = Files.createDirectory(fs.getPath("dir"));
      Path file1 = Files.createFile(fs.getPath("dir", "file1"));
      Path file2 = Files.createFile(fs.getPath("dir", "file2"));
      assertThat(Files.list(dir)).containsExactlyInAnyOrder(file1, file2);

      FileUtil.delete(dir);
      assertThat(dir).doesNotExist();

      Path nonExisting = fs.getPath("non-existing");
      assertThat(nonExisting).doesNotExist();
      FileUtil.delete(nonExisting);
    }
  }

  @Test
  public void deletingNonExistentFile() throws IOException {
    File missing = new File(tempDir.getRoot(), "missing");
    FileUtil.delete(missing.toPath());
    assertTrue(FileUtil.delete(missing));
  }

  @Test
  public void testToCanonicalPathSymLinksAware() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    File rootDir = tempDir.newFolder("root");
    tempDir.newFolder("root/dir1/dir2/dir3/dir4");
    String root = FileUtil.toSystemIndependentName(FileUtil.resolveShortWindowsName(rootDir.getPath()));

    // non-recursive link
    Files.createSymbolicLink(new File(rootDir, "dir1/dir2_link").toPath(), new File(rootDir, "dir1/dir2").toPath());
    // recursive links to a parent dir
    Files.createSymbolicLink(new File(rootDir, "dir1/dir1_link").toPath(), new File(rootDir, "dir1").toPath());

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
    }
    else {
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
    File probe = tempDir.newFile("probe");
    assertEquals(SystemInfo.isFileSystemCaseSensitive, FileUtil.isFileSystemCaseSensitive(probe.getPath()));
  }

  @Test
  public void testFileRelativePath() {
    String relativePath = FileUtil.toSystemDependentName("relative/path.file");

    File existingDir = tempDir.getRoot();
    assertEquals(relativePath, FileUtil.getRelativePath(existingDir, new File(existingDir, relativePath)));

    File notExistingDirOrFile = new File("not/existing/path");
    assertEquals(relativePath, FileUtil.getRelativePath(notExistingDirOrFile, new File(notExistingDirOrFile, relativePath)));

    // FileUtil.getRelativePath(File, File) should have the same behavior then FileUtil.getRelativePath(String, String, char)
    File existingFile = IoTestUtil.createTestFile(existingDir, "foo.file");
    assertEquals(".." + File.separatorChar + relativePath,
                 FileUtil.getRelativePath(existingFile, new File(existingFile.getParent(), relativePath)));
  }
}