// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static java.nio.file.attribute.PosixFilePermission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

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
    @NotNull Path link2 = directDirLink.toPath();
    @NotNull Path target2 = targetDir.toPath();
    Files.createSymbolicLink(link2, target2);
    File directFileLink = new File(tempDir.getRoot(), "fileLink");
    @NotNull Path link1 = directFileLink.toPath();
    @NotNull Path target1 = targetFile.toPath();
    Files.createSymbolicLink(link1, target1);
    File linkParentDir = tempDir.newDirectory("linkParent");
    @NotNull Path link = new File(linkParentDir, "link").toPath();
    @NotNull Path target = targetDir.toPath();
    Files.createSymbolicLink(link, target);

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
    assumeWindows();

    File targetDir = tempDir.newDirectory("target");
    File targetFile = tempDir.newFile("target/file");
    File directDirLink = new File(tempDir.getRoot(), "dirLink");
    createJunction(targetDir.getPath(), directDirLink.getPath());
    File linkParentDir = tempDir.newDirectory("linkParent");
    createJunction(targetDir.getPath(), new File(linkParentDir, "link").getPath());

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
    Files.createSymbolicLink(top.toPath().resolve("z-link"), top.toPath().resolve("a-dir"));

    FileUtil.delete(top);
    assertThat(top).doesNotExist();
  }

  @Test
  public void testRecursiveDeletionWithJunction() {
    assumeWindows();

    File top = tempDir.newDirectory("top");
    tempDir.newFile("top/a-dir/file");
    createJunction(top + "/a-dir", top + "/z-link");

    FileUtil.delete(top);
    assertThat(top).doesNotExist();
  }

  @Test
  public void deletingDosReadOnlyFile() throws IOException {
    assumeWindows();

    Path file = tempDir.newFile("file.txt").toPath();
    Files.getFileAttributeView(file, DosFileAttributeView.class).setReadOnly(true);
    FileUtil.delete(file);
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
    @NotNull Path link1 = new File(rootDir, "dir1/dir2_link").toPath();
    @NotNull Path target1 = new File(rootDir, "dir1/dir2").toPath();
    Files.createSymbolicLink(link1, target1);
    // recursive links to a parent dir
    @NotNull Path link = new File(rootDir, "dir1/dir1_link").toPath();
    @NotNull Path target = new File(rootDir, "dir1").toPath();
    Files.createSymbolicLink(link, target);

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
  public void testFileRelativePath() {
    String relativePath = FileUtil.toSystemDependentName("relative/path.file");

    File existingDir = tempDir.getRoot();
    assertEquals(relativePath, FileUtil.getRelativePath(existingDir, new File(existingDir, relativePath)));

    File notExistingDirOrFile = new File("not/existing/path");
    assertEquals(relativePath, FileUtil.getRelativePath(notExistingDirOrFile, new File(notExistingDirOrFile, relativePath)));

    // FileUtil.getRelativePath(File, File) should have the same behavior then FileUtil.getRelativePath(String, String, char)
    File existingFile = createTestFile(existingDir, "foo.file");
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
      File uncFile = new File(toLocalUncPath(file.getPath()));
      assertEquals(uncFile.toURI(), FileUtil.fileToUri(uncFile));
      assertEquals(uncFile, new File(FileUtil.fileToUri(uncFile)));

      File uncDir = uncFile.getParentFile();
      assertEquals(StringUtil.trimTrailing(uncDir.toURI().toString(), '/'), FileUtil.fileToUri(uncDir).toString());
      assertEquals(uncDir, new File(FileUtil.fileToUri(uncDir)));
    }
  }

  @Test
  public void permissionsCloning() throws IOException {
    assumeUnix();

    Path donor = tempDir.newFile("donor").toPath();
    Path recipient = tempDir.newFile("recipient").toPath();
    Files.setPosixFilePermissions(donor, EnumSet.of(OWNER_READ, OWNER_EXECUTE, GROUP_EXECUTE));
    Files.setPosixFilePermissions(recipient, EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ, OTHERS_EXECUTE));

    FileUtil.copyContent(donor.toFile(), recipient.toFile());

    assertEquals(EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ),
                 Files.getPosixFilePermissions(recipient));
  }
}
