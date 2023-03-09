// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static com.intellij.openapi.util.io.IoTestUtil.assumeUnix;
import static java.nio.file.attribute.PosixFilePermission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class SafeFileOutputStreamTest {
  private static final String TEST_BACKUP_EXT = ".bak";
  private static final byte[] TEST_DATA = {'h', 'e', 'l', 'l', 'o'};

  @Rule public TempDirectory tempDir = new TempDirectory();

  @After public void tearDown() throws Exception {
    if (SystemInfo.isUnix) {
      new ProcessBuilder("chmod", "-R", "u+rw", tempDir.getRoot().getPath()).start().waitFor();
    }
  }

  @Test public void newFile() throws IOException {
    checkWriteSucceed(new File(tempDir.getRoot(), "new-file.txt"));
  }

  @Test public void existingFile() throws IOException {
    checkWriteSucceed(tempDir.newFile("test.txt"));
  }

  @Test public void overwritingBackup() throws IOException {
    File target = tempDir.newFile("test.txt");
    tempDir.newFile(target.getName() + TEST_BACKUP_EXT);
    checkWriteSucceed(target);
  }

  @Test public void keepingAttributes() throws IOException {
    assumeUnix();

    File target = tempDir.newFile("test.txt");
    Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE, OTHERS_EXECUTE);
    Files.setPosixFilePermissions(target.toPath(), permissions);
    checkWriteSucceed(target);
    assertThat(Files.getPosixFilePermissions(target.toPath())).isEqualTo(permissions);
  }

  @Test public void preservingSymlinks() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    File target = tempDir.newFile("test.txt");
    File link = new File(tempDir.getRoot(), "link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = target.toPath();
    Files.createSymbolicLink(link1, target1);
    checkWriteSucceed(link);
    assertThat(link.toPath()).isSymbolicLink();
  }

  @Test public void newFileInReadOnlyDirectory() throws IOException {
    assumeUnix();

    File dir = tempDir.newDirectory("dir");
    Files.setPosixFilePermissions(dir.toPath(), EnumSet.of(OWNER_READ, OWNER_EXECUTE));
    checkWriteFailed(new File(dir, "test.txt"));
  }

  @Test public void existingFileInReadOnlyDirectory() throws IOException {
    assumeUnix();

    File target = tempDir.newFile("dir/test.txt");
    Files.write(target.toPath(), new byte[]{'.'});
    Files.setPosixFilePermissions(target.getParentFile().toPath(), EnumSet.of(OWNER_READ, OWNER_EXECUTE));
    checkWriteFailed(target);
  }

  @Test public void largeFile() throws IOException, NoSuchAlgorithmException {
    File target = tempDir.newFile("test.dat");
    MessageDigest digest = MessageDigest.getInstance("SHA-256");

    Random random = new Random();
    byte[] buffer = new byte[8192];
    try (OutputStream out = openStream(target)) {
      for (int i = 0; i < 128; i++) {
        random.nextBytes(buffer);
        out.write(buffer);
        digest.update(buffer);
      }
    }
    byte[] expected = digest.digest();
    digest.reset();

    assertThat(target).isFile().hasDigest(digest, expected);
  }

  @Test public void backupRemovalNotCritical() throws IOException {
    assumeUnix();

    File target = tempDir.newFile("dir/test.txt"), backup = new File(target.getParent(), target.getName() + TEST_BACKUP_EXT);
    try (OutputStream out = openStream(target)) {
      out.write(TEST_DATA);
      while (!backup.exists()) TimeoutUtil.sleep(10);
      Files.setPosixFilePermissions(target.getParentFile().toPath(), EnumSet.of(OWNER_READ, OWNER_EXECUTE));
    }

    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
  }

  @Test public void abort() throws IOException {
    File target = tempDir.newFile("dir/test.txt"), backup = new File(target.getParent(), target.getName() + TEST_BACKUP_EXT);
    try (OutputStream out = openStream(target)) {
      out.write(TEST_DATA);
    }
    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
    try (SafeFileOutputStream out = openStream(target)) {
      out.write(new byte[]{'b', 'y', 'e'});
      out.abort();
    }
    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
    assertThat(backup).doesNotExist();
  }

  @Test public void readOnlyFileBackup() throws IOException {
    File target = tempDir.newFile("dir/test.txt");
    if (SystemInfo.isWindows) {
      Files.getFileAttributeView(target.toPath(), DosFileAttributeView.class).setReadOnly(true);
    }
    else {
      Files.setPosixFilePermissions(target.toPath(), EnumSet.of(OWNER_READ));
    }
    checkWriteFailed(target);
    if (SystemInfo.isWindows) {
      Files.getFileAttributeView(target.toPath(), DosFileAttributeView.class).setReadOnly(false);
    }
    else {
      Files.setPosixFilePermissions(target.toPath(), EnumSet.of(OWNER_READ, OWNER_WRITE));
    }
    checkWriteSucceed(target);
  }

  @Test public void preemptiveStreamNew() throws IOException {
    File target = new File(tempDir.getRoot(), "new-file.txt");
    try (OutputStream out = new PreemptiveSafeFileOutputStream(target.toPath())) {
      out.write(TEST_DATA[0]);
      out.write(TEST_DATA, 1, TEST_DATA.length - 1);
    }
    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
    assertThat(target.getParentFile().list()).containsExactly(target.getName());
  }

  @Test public void preemptiveStreamExisting() throws IOException {
    File target = tempDir.newFile("test.txt");
    try (OutputStream out = new PreemptiveSafeFileOutputStream(target.toPath())) {
      out.write(TEST_DATA[0]);
      out.write(TEST_DATA, 1, TEST_DATA.length - 1);
    }
    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
    assertThat(target.getParentFile().list()).containsExactly(target.getName());
  }

  private static void checkWriteSucceed(File target) throws IOException {
    try (OutputStream out = openStream(target)) {
      out.write(TEST_DATA[0]);
      out.write(TEST_DATA, 1, TEST_DATA.length - 1);
    }

    assertThat(target).isFile().hasBinaryContent(TEST_DATA);
    assertThat(new File(target.getParent(), target.getName() + TEST_BACKUP_EXT)).doesNotExist();
  }

  private static void checkWriteFailed(File target) throws IOException {
    boolean exists = Files.exists(target.toPath());
    FileTime ts = exists ? Files.getLastModifiedTime(target.toPath()) : null;
    byte[] content = exists ? Files.readAllBytes(target.toPath()) : null;

    try {
      try (OutputStream out = openStream(target)) { out.write(TEST_DATA); }
      fail("writing to " + target + " should have failed");
    }
    catch (IOException e) {
      if (exists) {
        assertThat(target).hasBinaryContent(content);
        assertThat(Files.getLastModifiedTime(target.toPath())).isEqualTo(ts);
      }
      else {
        assertThat(target).doesNotExist();
      }
      assertThat(e.getMessage()).contains(target.getPath());
    }
  }

  private static SafeFileOutputStream openStream(File target) {
    return new SafeFileOutputStream(target, TEST_BACKUP_EXT);
  }
}
