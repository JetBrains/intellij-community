// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;

import static com.intellij.openapi.util.io.IoTestUtil.assertTimestampsEqual;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public abstract class FileAttributesReadingTest {
  public static class MainTest extends FileAttributesReadingTest {
    @BeforeClass
    public static void setUpClass() {
      FileSystemUtil.resetMediator();
      assertEquals(SystemInfo.isWindows ? "IdeaWin32" : "JnaUnix", FileSystemUtil.getMediatorName());
    }
  }

  public static class Nio2Test extends FileAttributesReadingTest {
    @BeforeClass
    public static void setUpClass() {
      System.setProperty(FileSystemUtil.FORCE_USE_NIO2_KEY, "true");
      FileSystemUtil.resetMediator();
      assertEquals("Nio2", FileSystemUtil.getMediatorName());
    }
  }

  public static class FallbackTest extends FileAttributesReadingTest {
    @BeforeClass
    public static void setUpClass() {
      System.setProperty(FileSystemUtil.FORCE_USE_FALLBACK_KEY, "true");
      FileSystemUtil.resetMediator();
      assertEquals("Fallback", FileSystemUtil.getMediatorName());
    }

    @Override public void linkToFile() { }
    @Override public void doubleLink() { }
    @Override public void linkToDirectory() { }
    @Override public void missingLink() { }
    @Override public void selfLink() { }
    @Override public void junction() { }
    @Override public void permissionsCloning() { }
  }

  @AfterClass
  public static void tearDownClass() {
    System.clearProperty(FileSystemUtil.FORCE_USE_NIO2_KEY);
    System.clearProperty(FileSystemUtil.FORCE_USE_FALLBACK_KEY);
    FileSystemUtil.resetMediator();
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

  private final byte[] myTestData = {'t', 'e', 's', 't'};

  @Test
  public void missingFile() {
    File file = new File(tempDir.getRoot(), "missing.txt");

    FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNull(attributes);

    String target = FileSystemUtil.resolveSymLink(file);
    assertNull(target);
  }

  @Test
  public void regularFile() throws IOException {
    File file = tempDir.newFile("file.txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(file);

    String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void readOnlyFile() throws IOException {
    File file = tempDir.newFile("file.txt");

    if (SystemInfo.isWindows) {
      Files.getFileAttributeView(file.toPath(), DosFileAttributeView.class).setReadOnly(true);
    }
    else {
      Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class).setPermissions(Collections.singleton(PosixFilePermission.OWNER_READ));
    }

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertFalse(attributes.isWritable());
  }

  @Test
  public void directory() throws IOException {
    File file = tempDir.newFolder("dir");

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(file);
    }

    String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void readOnlyDirectory() throws IOException {
    File dir = tempDir.newFolder("dir");

    if (SystemInfo.isWindows) {
      Files.getFileAttributeView(dir.toPath(), DosFileAttributeView.class).setReadOnly(true);
    }
    else {
      Files.getFileAttributeView(dir.toPath(), PosixFileAttributeView.class).setPermissions(Collections.singleton(PosixFilePermission.OWNER_READ));
    }

    FileAttributes attributes = getAttributes(dir);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(SystemInfo.isWindows, attributes.isWritable());
  }

  @Test
  public void root() {
    File file = new File(SystemInfo.isWindows ? "C:\\" : "/");

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(file);
    }
  }

  @Test
  public void badNames() throws IOException {
    File file = tempDir.newFile("file.txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(new File(file.getPath() + StringUtil.repeat(File.separator, 3)));
    assertFileAttributes(new File(file.getPath().replace(File.separator, StringUtil.repeat(File.separator, 3))));
    assertFileAttributes(new File(file.getPath().replace(File.separator, File.separator + "." + File.separator)));
    assertFileAttributes(
      new File(tempDir.getRoot(), File.separator + ".." + File.separator + tempDir.getRoot().getName() + File.separator + file.getName()));

    if (SystemInfo.isUnix) {
      File backSlashFile = tempDir.newFile("file\\txt");
      FileUtil.writeToFile(backSlashFile, myTestData);
      assertFileAttributes(backSlashFile);
    }
  }

  @Test
  public void special() {
    assumeTrue(SystemInfo.isUnix);
    File file = new File("/dev/null");

    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.SPECIAL, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(0, attributes.length);
    assertTrue(attributes.isWritable());

    String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToFile() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File file = tempDir.newFile("file.txt");
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    File link = new File(tempDir.getRoot(), "link");
    Files.createSymbolicLink(link.toPath(), file.toPath());

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK | FileAttributes.READ_ONLY, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void doubleLink() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File file = tempDir.newFile("file.txt");
    FileUtil.writeToFile(file, myTestData);
    assertTrue(file.setLastModified(file.lastModified() - 5000));
    assertTrue(file.setWritable(false, false));
    File link1 = new File(tempDir.getRoot(), "link1");
    Files.createSymbolicLink(link1.toPath(), file.toPath());
    File link2 = new File(tempDir.getRoot(), "link2");
    Files.createSymbolicLink(link2.toPath(), link1.toPath());

    FileAttributes attributes = getAttributes(link2);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.SYM_LINK | FileAttributes.READ_ONLY, attributes.flags);
    assertEquals(myTestData.length, attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertFalse(attributes.isWritable());

    String target = FileSystemUtil.resolveSymLink(link2);
    assertEquals(file.getPath(), target);
  }

  @Test
  public void linkToDirectory() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File dir = tempDir.newFolder("dir");
    if (SystemInfo.isUnix) assertTrue(dir.setWritable(false, false));
    assertTrue(dir.setLastModified(dir.lastModified() - 5000));
    File link = new File(tempDir.getRoot(), "link");
    Files.createSymbolicLink(link.toPath(), dir.toPath());

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(SystemInfo.isUnix ? FileAttributes.SYM_LINK | FileAttributes.READ_ONLY : FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(dir.length(), attributes.length);
    assertTimestampsEqual(dir.lastModified(), attributes.lastModified);
    if (SystemInfo.isUnix) assertFalse(attributes.isWritable());

    String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(dir.getPath(), target);
  }

  @Test
  public void missingLink() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File file = new File(tempDir.getRoot(), "file.txt");
    File link = new File(tempDir.getRoot(), "link");
    Files.createSymbolicLink(link.toPath(), file.toPath());

    FileAttributes attributes = getAttributes(link);
    assertNull(attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertEquals(0, attributes.length);

    String target = FileSystemUtil.resolveSymLink(link);
    assertNull(target, target);
  }

  @Test
  public void selfLink() throws IOException {
    assumeTrue(SystemInfo.areSymLinksSupported);

    File dir = tempDir.newFolder("dir");
    File link = new File(dir, "link");
    Files.createSymbolicLink(link.toPath(), dir.toPath());

    FileAttributes attributes = getAttributes(link);
    assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
    assertEquals(FileAttributes.SYM_LINK, attributes.flags);
    assertTimestampsEqual(dir.lastModified(), attributes.lastModified);

    String target = FileSystemUtil.resolveSymLink(link);
    assertEquals(dir.getPath(), target);
  }

  @Test
  public void junction() throws IOException {
    assumeTrue(SystemInfo.isWinVistaOrNewer);

    File target = tempDir.newFolder("dir");
    File path = new File(tempDir.getRoot(), "junction.dir");
    File junction = IoTestUtil.createJunction(target.getPath(), path.getAbsolutePath());

    try {
      FileAttributes attributes = getAttributes(junction);
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
      assertEquals(FileAttributes.SYM_LINK, attributes.flags);
      assertTrue(attributes.isWritable());

      String resolved1 = FileSystemUtil.resolveSymLink(junction);
      assertEquals(target.getPath(), resolved1);

      FileUtil.delete(target);

      attributes = getAttributes(junction);
      assertNull(attributes.type);
      assertEquals(FileAttributes.SYM_LINK, attributes.flags);
      assertTrue(attributes.isWritable());

      String resolved2 = FileSystemUtil.resolveSymLink(junction);
      assertNull(resolved2);
    }
    finally {
      IoTestUtil.deleteJunction(junction.getPath());
    }
  }

  @Test
  public void hiddenDir() throws IOException {
    assumeTrue(SystemInfo.isWindows);
    File dir = tempDir.newFolder("dir");
    FileAttributes attributes = getAttributes(dir);
    assertFalse(attributes.isHidden());
    Files.getFileAttributeView(dir.toPath(), DosFileAttributeView.class).setHidden(true);
    attributes = getAttributes(dir);
    assertTrue(attributes.isHidden());
  }

  @Test
  public void hiddenFile() throws IOException {
    assumeTrue(SystemInfo.isWindows);
    File file = tempDir.newFile("file");
    FileAttributes attributes = getAttributes(file);
    assertFalse(attributes.isHidden());
    Files.getFileAttributeView(file.toPath(), DosFileAttributeView.class).setHidden(true);
    attributes = getAttributes(file);
    assertTrue(attributes.isHidden());
  }

  @Test
  public void notSoHiddenRoot() {
    if (SystemInfo.isWindows) {
      File absRoot = new File("C:\\");
      FileAttributes absAttributes = getAttributes(absRoot);
      assertFalse(absAttributes.isHidden());

      File relRoot = new File("C:");
      FileAttributes relAttributes = getAttributes(relRoot);
      assertFalse(relAttributes.isHidden());
    }
    else {
      File absRoot = new File("/");
      FileAttributes absAttributes = getAttributes(absRoot);
      assertFalse(absAttributes.isHidden());
    }
  }

  @Test
  public void wellHiddenFile() {
    assumeTrue(SystemInfo.isWindows);
    File file = new File("C:\\Documents and Settings\\desktop.ini");
    assumeTrue(file.exists());

    FileAttributes attributes = getAttributes(file, false);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.HIDDEN, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
  }

  @Test
  public void extraLongName() throws IOException {
    String prefix = StringUtil.repeatSymbol('a', 128) + ".";
    File file = tempDir.newFile(prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".dir/" + prefix + ".txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(file);
    if (SystemInfo.isWindows) {
      assertDirectoriesEqual(file.getParentFile());
    }

    String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);

    if (SystemInfo.isWindows) {
      StringBuilder path = new StringBuilder(tempDir.getRoot().getPath());
      int length = 250 - path.length();
      for (int i = 0; i < length / 10; i++) {
        path.append("\\x_x_x_x_x");
      }

      File baseDir = new File(path.toString());
      assertTrue(baseDir.mkdirs());
      assertTrue(getAttributes(baseDir).isDirectory());

      for (int i = 1; i <= 100; i++) {
        File dir = new File(baseDir, StringUtil.repeat("x", i));
        assertTrue(dir.mkdir());
        assertTrue(getAttributes(dir).isDirectory());

        file = new File(dir, "file.txt");
        FileUtil.writeToFile(file, "test".getBytes(CharsetToolkit.UTF8_CHARSET));
        assertTrue(file.exists());
        assertFileAttributes(file);

        target = FileSystemUtil.resolveSymLink(file);
        assertEquals(file.getPath(), target);
      }
    }
  }

  @Test
  public void subst() throws IOException {
    assumeTrue(SystemInfo.isWindows);

    tempDir.newFile("file.txt");  // just to populate a directory
    File substRoot = IoTestUtil.createSubst(tempDir.getRoot().getPath());
    try {
      FileAttributes attributes = getAttributes(substRoot);
      assertEquals(FileAttributes.Type.DIRECTORY, attributes.type);
      assertDirectoriesEqual(substRoot);

      File[] children = substRoot.listFiles();
      assertNotNull(children);
      assertEquals(1, children.length);
      File file = children[0];
      String target = FileSystemUtil.resolveSymLink(file);
      assertEquals(file.getPath(), target);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
  }

  @Test
  public void hardLink() throws IOException {
    File target = tempDir.newFile("file.txt");
    File link = new File(tempDir.getRoot(), "link");
    Files.createLink(link.toPath(), target.toPath());

    FileAttributes attributes = getAttributes(link, SystemInfo.areSymLinksSupported);  // ignore XP
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(target.length(), attributes.length);
    assertTimestampsEqual(target.lastModified(), attributes.lastModified);

    FileUtil.writeToFile(target, myTestData);
    assertTrue(target.setLastModified(attributes.lastModified - 5000));
    assertTrue(target.length() > 0);
    assertTimestampsEqual(attributes.lastModified - 5000, target.lastModified());

    if (SystemInfo.isWindows) {
      byte[] bytes = FileUtil.loadFileBytes(link);
      assertEquals(myTestData.length, bytes.length);
    }

    attributes = getAttributes(link, SystemInfo.areSymLinksSupported);  // ignore XP
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(target.length(), attributes.length);
    assertTimestampsEqual(target.lastModified(), attributes.lastModified);

    String resolved = FileSystemUtil.resolveSymLink(link);
    assertEquals(link.getPath(), resolved);
  }

  @Test
  public void stamps() throws IOException, InterruptedException {
    FileAttributes attributes = FileSystemUtil.getAttributes(tempDir.getRoot());
    assumeTrue(attributes != null && attributes.lastModified > (attributes.lastModified/1000)*1000);

    long t1 = System.currentTimeMillis();
    TimeoutUtil.sleep(10);
    File file = tempDir.newFile("test.txt");
    TimeoutUtil.sleep(10);
    long t2 = System.currentTimeMillis();
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);

    t1 = System.currentTimeMillis();
    TimeoutUtil.sleep(10);
    FileUtil.writeToFile(file, myTestData);
    TimeoutUtil.sleep(10);
    t2 = System.currentTimeMillis();
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);

    ProcessBuilder cmd = SystemInfo.isWindows ? new ProcessBuilder("attrib", "-A", file.getPath()) : new ProcessBuilder("chmod", "644", file.getPath());
    assertEquals(0, cmd.start().waitFor());
    attributes = getAttributes(file);
    assertThat(attributes.lastModified).isBetween(t1, t2);
  }

  @Test
  public void notOwned() {
    assumeTrue(SystemInfo.isUnix);
    File userHome = new File(SystemProperties.getUserHome());

    FileAttributes homeAttributes = getAttributes(userHome);
    assertTrue(homeAttributes.isDirectory());
    assertTrue(homeAttributes.isWritable());

    FileAttributes parentAttributes = getAttributes(userHome.getParentFile());
    assertTrue(parentAttributes.isDirectory());
    assertFalse(parentAttributes.isWritable());
  }

  @Test
  public void permissionsCloning() throws IOException {
    assumeTrue(SystemInfo.isUnix);

    File donor = tempDir.newFile("donor");
    File recipient = tempDir.newFile("recipient");
    assertTrue(donor.setWritable(true, true));
    assertTrue(donor.setExecutable(true, true));
    assertTrue(recipient.setWritable(false, false));
    assertTrue(recipient.setExecutable(false, false));
    assertNotEquals(donor.canWrite(), recipient.canWrite());
    assertNotEquals(donor.canExecute(), recipient.canExecute());

    assertTrue(FileSystemUtil.clonePermissionsToExecute(donor.getPath(), recipient.getPath()));
    assertNotEquals(donor.canWrite(), recipient.canWrite());
    assertEquals(donor.canExecute(), recipient.canExecute());

    assertTrue(FileSystemUtil.clonePermissions(donor.getPath(), recipient.getPath()));
    assertEquals(donor.canWrite(), recipient.canWrite());
    assertEquals(donor.canExecute(), recipient.canExecute());
  }

  @Test
  public void testUnicodeName() throws IOException {
    String name = IoTestUtil.getUnicodeName();
    assumeTrue(name != null);
    File file = tempDir.newFile(name + ".txt");
    FileUtil.writeToFile(file, myTestData);

    assertFileAttributes(file);

    String target = FileSystemUtil.resolveSymLink(file);
    assertEquals(file.getPath(), target);
  }

  @NotNull
  private static FileAttributes getAttributes(@NotNull File file) {
    return getAttributes(file, true);
  }

  @NotNull
  private static FileAttributes getAttributes(@NotNull File file, boolean checkList) {
    FileAttributes attributes = FileSystemUtil.getAttributes(file);
    assertNotNull(file.getPath() + ", exists=" + file.exists(), attributes);

    if (SystemInfo.isWindows && checkList) {
      String parent = file.getParent();
      if (parent != null) {
        FileInfo[] infos = IdeaWin32.getInstance().listChildren(parent);
        assertNotNull(infos);
        for (FileInfo info : infos) {
          if (file.getName().equals(info.getName())) {
            assertEquals(attributes, info.toFileAttributes());
            return attributes;
          }
        }
        fail(file + " not listed");
      }
    }

    return attributes;
  }

  private static void assertFileAttributes(@NotNull File file) {
    FileAttributes attributes = getAttributes(file);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(0, attributes.flags);
    assertEquals(file.length(), attributes.length);
    assertTimestampsEqual(file.lastModified(), attributes.lastModified);
    assertTrue(attributes.isWritable());
  }

  private static void assertDirectoriesEqual(@NotNull File dir) {
    String[] list1 = dir.list();
    assertNotNull(list1);
    FileInfo[] list2 = IdeaWin32.getInstance().listChildren(dir.getPath());
    assertNotNull(list2);
    if (list1.length + 2 != list2.length) {
      assertEquals(Arrays.toString(list1), Arrays.toString(list2));
    }
  }
}