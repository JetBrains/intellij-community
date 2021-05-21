// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * @author Irina.Chernushina, lene
 */
public class FileUtilHeavyTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testProcessSimple() {
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
  public void testProcessStops() {
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
  public void testProcessDirectoryFilter() {
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

  private void setupVisitorTestDirectories() {
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
  public void nonExistingFile() {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath() + "123", first.getName() + "123")).isNullOrEmpty();
  }

  @Test
  public void existingFileInDirectory() {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "first")).isEqualTo(first.getPath());
  }

  @Test
  public void existingFile() {
    File first = tempDir.newFile("first");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrderInDirectory() {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrderInDirectory2() {
    tempDir.newFile("first");
    File second = tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(tempDir.getRoot().getPath(), "second", "first")).isEqualTo(second.getPath());
  }

  @Test
  public void twoFilesOrder() {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void twoFilesOrder2() {
    File first = tempDir.newFile("first");
    tempDir.newFile("second");
    assertThat(FileUtil.findFileInProvidedPath(first.getPath(), "first", "second")).isEqualTo(first.getPath());
  }

  @Test
  public void testDeleteFail() throws IOException {
    File targetDir = tempDir.newDirectory("dir");
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
    assumeSymLinkCreationIsSupported();

    File targetDir = tempDir.newDirectory("target");
    File targetFile = tempDir.newFile("target/file");
    File directDirLink = new File(tempDir.getRoot(), "dirLink");
    IoTestUtil.createSymbolicLink(directDirLink.toPath(), targetDir.toPath());
    File directFileLink = new File(tempDir.getRoot(), "fileLink");
    IoTestUtil.createSymbolicLink(directFileLink.toPath(), targetFile.toPath());
    File linkParentDir = tempDir.newDirectory("linkParent");
    IoTestUtil.createSymbolicLink(new File(linkParentDir, "link").toPath(), targetDir.toPath());

    FileUtil.delete(directFileLink);
    FileUtil.delete(directDirLink);
    FileUtil.delete(linkParentDir);

    assertThat(directFileLink).doesNotExist();
    assertThat(directDirLink).doesNotExist();
    assertThat(linkParentDir).doesNotExist();
    assertThat(targetFile).exists();
  }

  @Test
  public void testJunctionDeletion() {
    IoTestUtil.assumeWindows();

    File targetDir = tempDir.newDirectory("target");
    File targetFile = tempDir.newFile("target/file");
    File directDirLink = new File(tempDir.getRoot(), "dirLink");
    IoTestUtil.createJunction(targetDir.getPath(), directDirLink.getPath());
    File linkParentDir = tempDir.newDirectory("linkParent");
    IoTestUtil.createJunction(targetDir.getPath(), new File(linkParentDir, "link").getPath());

    FileUtil.delete(directDirLink);
    FileUtil.delete(linkParentDir);

    assertThat(directDirLink).doesNotExist();
    assertThat(linkParentDir).doesNotExist();
    assertThat(targetFile).exists();
  }

  @Test
  public void testRecursiveDeletionWithSymlink() throws IOException {
    assumeSymLinkCreationIsSupported();

    File top = tempDir.newDirectory("top");
    tempDir.newFile("top/a-dir/file");
    IoTestUtil.createSymbolicLink(top.toPath().resolve("z-link"), top.toPath().resolve("a-dir"));

    FileUtil.delete(top);
    assertThat(top).doesNotExist();
  }

  @Test
  public void testRecursiveDeletionWithJunction() {
    IoTestUtil.assumeWindows();

    File top = tempDir.newDirectory("top");
    tempDir.newFile("top/a-dir/file");
    IoTestUtil.createJunction(top + "/a-dir", top + "/z-link");

    FileUtil.delete(top);
    assertThat(top).doesNotExist();
  }

  @Test
  public void nioDeletion() throws IOException {
    try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build(FileUtilHeavyTest.class.getSimpleName())) {
      Path dir = Files.createDirectory(fs.getPath("dir"));
      Path file1 = Files.createFile(fs.getPath("dir", "file1"));
      Path file2 = Files.createFile(fs.getPath("dir", "file2"));
      try (Stream<Path> stream = Files.list(dir)) {
        assertThat(stream).containsExactlyInAnyOrder(file1, file2);
      }

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
    assumeSymLinkCreationIsSupported();

    File rootDir = tempDir.newDirectory("root");
    tempDir.newDirectory("root/dir1/dir2/dir3/dir4");
    String root = FileUtil.toSystemIndependentName(FileUtil.resolveShortWindowsName(rootDir.getPath()));

    // non-recursive link
    IoTestUtil.createSymbolicLink(new File(rootDir, "dir1/dir2_link").toPath(), new File(rootDir, "dir1/dir2").toPath());
    // recursive links to a parent dir
    IoTestUtil.createSymbolicLink(new File(rootDir, "dir1/dir1_link").toPath(), new File(rootDir, "dir1").toPath());

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

  @Test
  public void fileToUri() {
    File file = tempDir.newFile("test.txt");
    assertEquals(file.toURI(), FileUtil.fileToUri(file));
    assertEquals(file, new File(FileUtil.fileToUri(file)));

    File dir = file.getParentFile();
    assertEquals(StringUtil.trimTrailing(dir.toURI().toString(), '/'), FileUtil.fileToUri(dir).toString());
    assertEquals(dir, new File(FileUtil.fileToUri(dir)));

    if (SystemInfo.isWindows) {
      File uncFile = new File(IoTestUtil.toLocalUncPath(file.getPath()));
      assertEquals(uncFile.toURI(), FileUtil.fileToUri(uncFile));
      assertEquals(uncFile, new File(FileUtil.fileToUri(uncFile)));

      File uncDir = uncFile.getParentFile();
      assertEquals(StringUtil.trimTrailing(uncDir.toURI().toString(), '/'), FileUtil.fileToUri(uncDir).toString());
      assertEquals(uncDir, new File(FileUtil.fileToUri(uncDir)));
    }
  }

  @Test
  public void createDirectories() throws IOException {
    Path existingDir = tempDir.newDirectory("existing").toPath();
    NioFiles.createDirectories(existingDir);

    Path nonExisting = tempDir.getRoot().toPath().resolve("d1/d2/d3/non-existing");
    NioFiles.createDirectories(nonExisting);
    assertThat(nonExisting).isDirectory();

    Path existingFile = tempDir.newFile("file").toPath();
    try {
      NioFiles.createDirectories(existingFile);
      fail("`createDirectories()` over an existing file shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }
    try {
      NioFiles.createDirectories(existingFile.resolve("dir"));
      fail("`createDirectories()` over an existing file shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }

    assumeSymLinkCreationIsSupported();

    Path endLink = tempDir.getRoot().toPath().resolve("end-link");
    IoTestUtil.createSymbolicLink(endLink, existingDir);
    NioFiles.createDirectories(endLink);
    assertThat(endLink).isDirectory().isSymbolicLink();

    Path middleLinkDir = endLink.resolve("d1/d2");
    NioFiles.createDirectories(middleLinkDir);
    assertThat(middleLinkDir).isDirectory();

    Path badLink = tempDir.getRoot().toPath().resolve("bad-link");
    IoTestUtil.createSymbolicLink(badLink, Paths.get("bad-target"));
    try {
      NioFiles.createDirectories(badLink);
      fail("`createDirectories()` over a dangling symlink shall not pass");
    }
    catch (FileAlreadyExistsException ignored) { }
  }

  @Test
  public void setReadOnly() throws IOException {
    Path f = tempDir.newFile("f").toPath();

    NioFiles.setReadOnly(f, true);
    try {
      Files.writeString(f, "test");
      fail("Writing to " + f + " should have failed");
    }
    catch (AccessDeniedException ignored) { }

    NioFiles.setReadOnly(f, false);
    Files.writeString(f, "test");

    Path d = tempDir.newDirectory("d").toPath(), child = d.resolve("f");

    NioFiles.setReadOnly(d, true);
    if (!SystemInfo.isWindows) {
      try {
        Files.createFile(child);
        fail("Creating " + child + " should have failed");
      }
      catch (AccessDeniedException ignored) { }
    }

    NioFiles.setReadOnly(d, false);
    Files.createFile(child);
  }
}
