// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class FileAttributesReadingTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private final byte[] myTestData = {'t', 'e', 's', 't'};

  @Test
  public void winReparsePointAttributeConversion() {
    assumeWindows();

    var nioAttributes = new BasicFileAttributes() {
      @Override public FileTime lastModifiedTime() { return FileTime.from(Instant.now()); }
      @Override public FileTime lastAccessTime() { return lastModifiedTime(); }
      @Override public FileTime creationTime() { return lastModifiedTime(); }
      @Override public boolean isRegularFile() { return false; }
      @Override public boolean isDirectory() { return true; }
      @Override public boolean isSymbolicLink() { return false; }
      @Override public boolean isOther() { return true; }
      @Override public long size() { return 0; }
      @Override public Object fileKey() { return null; }
    };

    var rootAttributes = FileAttributes.fromNio(Path.of(System.getenv("SystemDrive") + '\\'), nioAttributes);
    assertTrue(rootAttributes.isDirectory());
    assertEquals(FileAttributes.Type.DIRECTORY, rootAttributes.getType());
    assertFalse(rootAttributes.isSymLink());

    var dirAttributes = FileAttributes.fromNio(Path.of(System.getenv("USERPROFILE")), nioAttributes);
    assertTrue(dirAttributes.isDirectory());
    assertEquals(FileAttributes.Type.DIRECTORY, dirAttributes.getType());
    assertTrue(dirAttributes.isSymLink());
  }

  @Test
  public void missingFile() {
    File file = new File(tempDir.getRoot(), "missing.txt");
    assertFalse(file.exists());
    FileAttributes attributes = getAttributes(file.getPath());
    assertNull(attributes);

    String target = resolveSymLink(file);
    assertNull(target);
  }

  @Test
  public void regularFile() throws IOException {
    File file = tempDir.newFile("file.txt");
    Files.write(file.toPath(), myTestData);

    assertFileAttributes(file);

    String target = resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void readOnlyFile() throws IOException {
    File file = tempDir.newFile("file.txt");
    NioFiles.setReadOnly(file.toPath(), true);
    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertFalse(attributes.isWritable());
  }

  @Test
  public void directory() {
    File file = tempDir.newDirectory("dir");

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType());
    assertFalse(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals("directory.length() is defined to be 0",
                 0, attributes.length);
    assertEquals(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());

    String target = resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void readOnlyDirectory() throws IOException {
    File dir = tempDir.newDirectory("dir");
    NioFiles.setReadOnly(dir.toPath(), true);
    FileAttributes attributes = getAttributes(dir);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType());
    assertTrue(attributes.isWritable());
  }

  @Test
  public void root() {
    File file = new File(SystemInfo.isWindows ? "C:\\" : "/");

    FileAttributes attributes = getAttributes(file);
    assertEquals(file + " " + attributes, FileAttributes.Type.DIRECTORY, attributes.getType());
    assertFalse(file + " " + attributes, attributes.isSymLink());
  }

  @Test
  public void badNames() throws IOException {
    File file = tempDir.newFile("file.txt");
    Files.write(file.toPath(), myTestData);

    assertFileAttributes(new File(file.getPath() + StringUtil.repeat(File.separator, 3)));
    assertFileAttributes(new File(file.getPath().replace(File.separator, StringUtil.repeat(File.separator, 3))));
    assertFileAttributes(new File(file.getPath().replace(File.separator, File.separator + "." + File.separator)));
    assertFileAttributes(
      new File(tempDir.getRoot(), File.separator + ".." + File.separator + tempDir.getRoot().getName() + File.separator + file.getName()));

    if (SystemInfo.isUnix) {
      File backSlashFile = tempDir.newFile("file\\txt");
      Files.write(backSlashFile.toPath(), myTestData);
      assertFileAttributes(backSlashFile);
    }
  }

  @Test
  public void special() {
    assumeUnix();
    File file = new File("/dev/null");

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.SPECIAL, attributes.getType());
    assertFalse(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals(0, attributes.length);
    assertTrue(attributes.isWritable());

    String target = resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToFile() throws IOException {
    assumeSymLinkCreationIsSupported();

    File file = tempDir.newFile("file.txt");
    Files.write(file.toPath(), myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    File link = new File(tempDir.getRoot(), "link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = file.toPath();
    Files.createSymbolicLink(link1, target1);

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertTrue(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertFalse(attributes.isWritable());

    assertEquals(myTestData.length, attributes.length);
    assertEquals(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    String target = resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void doubleLink() throws IOException {
    assumeSymLinkCreationIsSupported();

    File file = tempDir.newFile("file.txt");
    Files.write(file.toPath(), myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    File link1 = new File(tempDir.getRoot(), "link1");
    @NotNull Path link3 = link1.toPath();
    @NotNull Path target2 = file.toPath();
    Files.createSymbolicLink(link3, target2);
    File link2 = new File(tempDir.getRoot(), "link2");
    @NotNull Path link = link2.toPath();
    @NotNull Path target1 = link1.toPath();
    Files.createSymbolicLink(link, target1);

    FileAttributes attributes = getAttributes(link2);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertTrue(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertFalse(attributes.isWritable());

    assertEquals(myTestData.length, attributes.length);
    assertEquals(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    String target = resolveSymLink(link2);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToDirectory() throws IOException {
    assumeSymLinkCreationIsSupported();

    File dir = tempDir.newDirectory("dir");
    if (SystemInfo.isUnix) assertTrue(dir.setWritable(false, false));
    assertTrue(dir.setLastModified(dir.lastModified() - 5000));
    File link = new File(tempDir.getRoot(), "link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = dir.toPath();
    Files.createSymbolicLink(link1, target1);

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType());
    assertTrue(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals("directory.length() is defined to be 0",
                 0, attributes.length);
    assertEquals(dir.lastModified(), attributes.lastModified);

    String target = resolveSymLink(link);
    assertEquals(dir.getPath(), target);
  }

  @Test
  public void missingLink() throws IOException {
    assumeSymLinkCreationIsSupported();

    File file = new File(tempDir.getRoot(), "file.txt");
    assertFalse(file.exists());
    File link = new File(tempDir.getRoot(), "link");
    assertFalse(link.exists());
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = file.toPath();
    Files.createSymbolicLink(link1, target1);

    FileAttributes attributes = getAttributes(link);
    assertNull(attributes.getType());
    assertTrue(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals(0, attributes.length);

    String target = resolveSymLink(link);
    assertNull(target, target);
  }

  @Test
  public void selfLink() throws IOException {
    assumeSymLinkCreationIsSupported();

    File link = new File(tempDir.getRoot(), "self_link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target = link.toPath();
    Files.createSymbolicLink(link1, target);

    FileAttributes attributes = getAttributes(link);
    assertNull(attributes.getType());
    assertTrue(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals(0, attributes.lastModified);
    assertNull(resolveSymLink(link));
  }

  @Test
  public void innerSymlinkResolve() throws IOException {
    assumeSymLinkCreationIsSupported();

    File file = tempDir.newFile("dir/file.txt");
    File link = new File(tempDir.getRoot(), "link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = file.getParentFile().toPath();
    Files.createSymbolicLink(link1, target1);

    String target = resolveSymLink(new File(link.getPath() + '/' + file.getName()));
    assertEquals(file.getPath(), target);
  }

  @Test
  public void junction() throws IOException {
    assumeWindows();

    File target = tempDir.newDirectory("dir");
    File junction = createJunction(target.getPath(), tempDir.getRoot() + "/junction.dir");

    try {
      FileAttributes attributes = getAttributes(junction);
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.getType());
      assertTrue(attributes.isSymLink());
      assertFalse(attributes.isHidden());
      assertTrue(attributes.isWritable());

      String resolved1 = resolveSymLink(junction);
      assertEquals(target.getPath(), resolved1);

      Files.delete(target.toPath());

      attributes = getAttributes(junction);
      assertNull(attributes.getType());
      assertTrue(attributes.isSymLink());
      assertFalse(attributes.isHidden());
      assertTrue(attributes.isWritable());

      String resolved2 = resolveSymLink(junction);
      assertNull(resolved2);
    }
    finally {
      deleteJunction(junction.getPath());
    }
  }

  @Test
  public void innerJunctionResolve() {
    assumeWindows();

    File file = tempDir.newFile("dir/file.txt");
    File junction = new File(tempDir.getRoot(), "junction");
    createJunction(file.getParent(), junction.getPath());

    String target = resolveSymLink(new File(junction.getPath() + '/' + file.getName()));
    assertEquals(file.getPath(), target);
  }

  @Test
  public void hiddenDir() throws IOException {
    assumeWindows();
    File dir = tempDir.newDirectory("dir");
    FileAttributes attributes = getAttributes(dir);
    assertFalse(attributes.isHidden());
    Files.getFileAttributeView(dir.toPath(), DosFileAttributeView.class).setHidden(true);
    attributes = getAttributes(dir);
    assertTrue(attributes.isHidden());
  }

  @Test
  public void hiddenFile() throws IOException {
    assumeWindows();
    File file = tempDir.newFile("file");
    FileAttributes attributes = getAttributes(file);
    assertFalse(attributes.isHidden());
    Files.getFileAttributeView(file.toPath(), DosFileAttributeView.class).setHidden(true);
    attributes = getAttributes(file);
    assertTrue(attributes.isHidden());
  }

  @Test
  public void notSoHiddenRoot() {
    File absRoot = SystemInfo.isWindows ? new File(System.getenv("SystemDrive") + '\\') : new File("/");
    FileAttributes absAttributes = getAttributes(absRoot);
    assertFalse(absAttributes.isHidden());
  }

  @Test
  public void wellHiddenFile() {
    assumeWindows();
    File file = new File("C:\\Documents and Settings\\desktop.ini");
    assumeTrue(file +" is not there", file.exists());

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertFalse(attributes.isSymLink());
    assertTrue(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals(file.length(), attributes.length);
    assertEquals(file.lastModified(), attributes.lastModified);
  }

  @Test
  public void extraLongName() throws IOException {
    String prefix = StringUtil.repeatSymbol('a', 128) + ".";
    File file = tempDir.newFile(prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".txt");
    Files.write(file.toPath(), myTestData);

    assertFileAttributes(file);

    String target = resolveSymLink(file);
    assertEquals(file.getPath(), target);

    if (SystemInfo.isWindows) {
      StringBuilder path = new StringBuilder(tempDir.getRoot().getPath());
      int length = 250 - path.length();
      path.append("\\x_x_x_x_x".repeat(Math.max(0, length / 10)));

      File baseDir = new File(path.toString());
      assertTrue(baseDir.mkdirs());
      assertTrue(getAttributes(baseDir).isDirectory());

      for (int i = 1; i <= 100; i++) {
        File dir = new File(baseDir, StringUtil.repeat("x", i));
        assertTrue(dir.mkdir());
        assertTrue(getAttributes(dir).isDirectory());

        file = new File(dir, "file.txt");
        Files.write(file.toPath(), myTestData);
        assertTrue(file.exists());
        assertFileAttributes(file);

        target = resolveSymLink(file);
        assertEquals(file.getPath(), target);
      }
    }
  }

  @Test
  public void subst() {
    assumeWindows();

    tempDir.newFile("file.txt");  // just to populate a directory
    performTestOnWindowsSubst(tempDir.getRoot().getPath(), substRoot ->{
      FileAttributes attributes = getAttributes(substRoot);
      assertEquals(substRoot + " " + attributes, FileAttributes.Type.DIRECTORY, attributes.getType());
      assertFalse(substRoot + " " + attributes, attributes.isSymLink());

      File[] children = substRoot.listFiles();
      assertNotNull(children);
      assertEquals(1, children.length);
      File file = children[0];
      String target = resolveSymLink(file);
      assertEquals(file.getPath(), target);
    });
  }

  @Test
  public void hardLink() throws IOException {
    assumeSymLinkCreationIsSupported();
    File target = tempDir.newFile("file.txt");
    File link = new File(tempDir.getRoot(), "link");
    Files.createLink(link.toPath(), target.toPath());

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertEquals(target.length(), attributes.length);
    assertEquals(target.lastModified(), attributes.lastModified);

    Files.write(target.toPath(), myTestData);
    assertTrue(target.setLastModified(attributes.lastModified - 5000));
    assertTrue(target.length() > 0);
    assertEquals(attributes.lastModified - 5000, target.lastModified());

    if (SystemInfo.isWindows) {
      byte[] bytes = Files.readAllBytes(link.toPath());
      assertEquals(myTestData.length, bytes.length);
    }

    attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertEquals(target.length(), attributes.length);
    assertEquals(target.lastModified(), attributes.lastModified);

    String resolved = resolveSymLink(link);
    assertEquals(link.getPath(), resolved);
  }

  @Test
  public void stamps() throws IOException, InterruptedException {
    FileAttributes attributes = getAttributes(tempDir.getRoot());
    assumeTrue("expected FS has millisecond resolution but got lastModified: " + attributes.lastModified,
               attributes.lastModified > attributes.lastModified / 1000 * 1000);

    long t1 = System.currentTimeMillis();
    TimeoutUtil.sleep(10);
    File file = tempDir.newFile("test.txt");
    TimeoutUtil.sleep(10);
    long t2 = System.currentTimeMillis();
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);

    t1 = System.currentTimeMillis();
    TimeoutUtil.sleep(10);
    Files.write(file.toPath(), myTestData);
    TimeoutUtil.sleep(10);
    t2 = System.currentTimeMillis();
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);

    ProcessBuilder cmd = SystemInfo.isWindows
                         ? new ProcessBuilder("attrib", "-A", file.getPath()) : new ProcessBuilder("chmod", "644", file.getPath());
    assertEquals(0, cmd.start().waitFor());
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);
  }

  @Test
  public void notOwned() throws IOException {
    File userHome = new File(SystemProperties.getUserHome());

    FileAttributes homeAttributes = getAttributes(userHome);
    assertTrue(homeAttributes.isDirectory());
    assertTrue(homeAttributes.isWritable());

    FileAttributes parentAttributes = getAttributes(userHome.getParentFile());
    assertTrue(parentAttributes.isDirectory());
    assertTrue(parentAttributes.isWritable());

    if (SystemInfo.isUnix) {
      var mutantFile = tempDir.newFile("mutant");
      Files.setPosixFilePermissions(mutantFile.toPath(), PosixFilePermissions.fromString("r--rw-rw-"));
      var mutantAttrs = getAttributes(mutantFile);
      assertTrue(mutantAttrs.isFile());
      assertFalse(mutantAttrs.isWritable());

      var devNull = getAttributes(new File("/dev/null"));
      assertTrue(devNull.isSpecial());
      assertTrue(devNull.isWritable());

      var etcPasswd = getAttributes(new File("/etc/passwd"));
      assertTrue(etcPasswd.isFile());
      assertFalse(etcPasswd.isWritable());
    }
  }

  @Test
  public void unicodeName() throws IOException {
    String name = getUnicodeName();
    assumeTrue("Unicode names not supported", name != null);
    File file = tempDir.newFile(name + ".txt");
    Files.write(file.toPath(), myTestData);

    assertFileAttributes(file);

    String target = resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  private static @Nullable String resolveSymLink(File file) {
    String realPath = FileSystemUtil.resolveSymLink(file.getAbsolutePath());
    if (realPath != null && (SystemInfo.isWindows && realPath.startsWith("\\\\") || new File(realPath).exists())) {
      return realPath;
    }
    return null;
  }

  private static FileAttributes getAttributes(File file) {
    String path = file.getPath();
    FileAttributes attributes = getAttributes(path);
    assertNotNull(path + ", exists=" + file.exists(), attributes);
    return attributes;
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  private static @Nullable FileAttributes getAttributes(String path) {
    return FileSystemUtil.getAttributes(path);
  }

  private static void assertFileAttributes(File file) {
    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertFalse(attributes.isSymLink());
    assertFalse(attributes.isHidden());
    assertTrue(attributes.isWritable());
    assertEquals(file.length(), attributes.length);
    assertEquals(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
  }
}
