/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.local;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class LocalFileSystemTest extends PlatformLangTestCase {
  public void testChildrenAccessedButNotCached() throws Exception {
    File dir = createTempDirectory(false);
    ManagingFS managingFS = ManagingFS.getInstance();

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(vFile);
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertFalse(managingFS.wereChildrenAccessed(vFile));

    File child = new File(dir, "child");
    boolean created = child.createNewFile();
    assertTrue(created);

    File subdir = new File(dir, "subdir");
    boolean subdirCreated = subdir.mkdir();
    assertTrue(subdirCreated);

    File subChild = new File(subdir, "subdir");
    boolean subChildCreated = subChild.createNewFile();
    assertTrue(subChildCreated);

    VirtualFile childVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(child.getPath().replace(File.separatorChar, '/'));
    assertNotNull(childVFile);
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));

    VirtualFile subdirVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(subdirVFile);
    assertFalse(managingFS.areChildrenLoaded(subdirVFile));
    assertFalse(managingFS.wereChildrenAccessed(subdirVFile));
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));

    vFile.getChildren();
    assertTrue(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));
    assertFalse(managingFS.areChildrenLoaded(subdirVFile));
    assertFalse(managingFS.wereChildrenAccessed(subdirVFile));

    VirtualFile subChildVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subChild.getPath().replace(File.separatorChar, '/'));
    assertNotNull(subChildVFile);
    assertTrue(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));
    assertFalse(managingFS.areChildrenLoaded(subdirVFile));
    assertTrue(managingFS.wereChildrenAccessed(subdirVFile));
  }

  public void testRefreshAndFindFile() throws Exception {
    File dir = createTempDirectory();

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(vFile);
    vFile.getChildren();

    for (int i = 0; i < 100; i++) {
      File subdir = new File(dir, "a" + i);
      assertTrue(subdir.mkdir());
    }

    File subdir = new File(dir, "aaa");
    assertTrue(subdir.mkdir());

    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(file);
  }

  public void testCopyFile() throws Exception {
    File fromDir = createTempDirectory();
    File toDir = createTempDirectory();

    VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
    VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(fromVDir);
    assertNotNull(toVDir);
    final VirtualFile fileToCopy = fromVDir.createChildData(this, "temp_file");
    final byte[] byteContent = {0, 1, 2, 3};
    fileToCopy.setBinaryContent(byteContent);
    final String newName = "new_temp_file";
    final VirtualFile copy = fileToCopy.copy(this, toVDir, newName);
    assertEquals(newName, copy.getName());
    assertTrue(Arrays.equals(byteContent, copy.contentsToByteArray()));
  }

  public void testCopyDir() throws Exception {
    File fromDir = createTempDirectory();
    File toDir = createTempDirectory();

    VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
    VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
    assertNotNull(fromVDir);
    assertNotNull(toVDir);
    final VirtualFile dirToCopy = fromVDir.createChildDirectory(this, "dir");
    final VirtualFile file = dirToCopy.createChildData(this, "temp_file");
    file.setBinaryContent(new byte[]{0, 1, 2, 3});
    final String newName = "dir";
    final VirtualFile dirCopy = dirToCopy.copy(this, toVDir, newName);
    assertEquals(newName, dirCopy.getName());
    PlatformTestUtil.assertDirectoriesEqual(toVDir, fromVDir);
  }

  public void testUnicodeNames() throws Exception {
    final File dirFile = createTempDirectory();
    final String name = "te\u00dft123123123.txt";
    final File childFile = new File(dirFile, name);
    assert childFile.createNewFile() || childFile.exists() : childFile;

    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dirFile);
    assertNotNull(dir);

    final VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(childFile);
    assertNotNull(child);

    assertTrue(childFile.delete());
  }

  public void testFindRoot() throws IOException {
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath("wrong_path");
    assertNull(root);

    VirtualFile root2;
    if (SystemInfo.isWindows) {
      root = LocalFileSystem.getInstance().findFileByPath("\\\\unit-133");
      assertNotNull(root);
      root2 = LocalFileSystem.getInstance().findFileByPath("//UNIT-133");
      assertNotNull(root2);
      assertEquals(String.valueOf(root2), root, root2);
      RefreshQueue.getInstance().processSingleEvent(new VFileDeleteEvent(this, root, false));

      root = LocalFileSystem.getInstance().findFileByIoFile(new File("\\\\unit-133"));
      assertNotNull(root);
      RefreshQueue.getInstance().processSingleEvent(new VFileDeleteEvent(this, root, false));

      if (new File("c:").exists()) {
        root = LocalFileSystem.getInstance().findFileByPath("c:");
        assertNotNull(root);
        assertEquals("C:/", root.getPath());

        root2 = LocalFileSystem.getInstance().findFileByPath("C:\\");
        assertEquals(String.valueOf(root2), root, root2);
      }
    }
    else if (SystemInfo.isUnix) {
      root = LocalFileSystem.getInstance().findFileByPath("/");
      assertNotNull(root);
      assertEquals(root.getPath(), "/");
    }

    root = LocalFileSystem.getInstance().findFileByPath("");
    assertNotNull(root);

    File jarFile = IoTestUtil.createTestJar();
    root = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath() + "!/");
    assertNotNull(root);

    root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath().replace(File.separator, "//") + "!/");
    assertEquals(String.valueOf(root2), root, root2);

    if (!SystemInfo.isFileSystemCaseSensitive) {
      root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath().toUpperCase(Locale.US) + "!/");
      assertEquals(String.valueOf(root2), root, root2);
    }
  }

  public void testFileLength() throws Exception {
    File file = FileUtil.createTempFile("test", "txt");
    FileUtil.writeToFile(file, "hello");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    String s = VfsUtilCore.loadText(virtualFile);
    assertEquals("hello", s);
    assertEquals(5, virtualFile.getLength());

    FileUtil.writeToFile(file, "new content");
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContents();
    s = VfsUtilCore.loadText(virtualFile);
    assertEquals("new content", s);
    assertEquals(11, virtualFile.getLength());
  }

  public void testHardLinks() throws Exception {
    if (!SystemInfo.isWindows && !SystemInfo.isUnix) {
      System.err.println(getName() + " skipped: " + SystemInfo.OS_NAME);
      return;
    }

    final boolean safeWrite = GeneralSettings.getInstance().isUseSafeWrite();
    final File dir = FileUtil.createTempDirectory("hardlinks.", ".dir", false);
    final SafeWriteRequestor requestor = new SafeWriteRequestor() { };
    try {
      GeneralSettings.getInstance().setUseSafeWrite(false);

      final File targetFile = new File(dir, "targetFile");
      assertTrue(targetFile.createNewFile());
      final File hardLinkFile = IoTestUtil.createHardLink(targetFile.getAbsolutePath(), "hardLinkFile");

      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile);
      assertNotNull(file);
      file.setBinaryContent("hello".getBytes("UTF-8"), 0, 0, requestor);
      assertTrue(file.getLength() > 0);

      final VirtualFile check = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(hardLinkFile);
      assertNotNull(check);
      assertEquals(file.getLength(), check.getLength());
      assertEquals("hello", VfsUtilCore.loadText(check));
    }
    finally {
      GeneralSettings.getInstance().setUseSafeWrite(safeWrite);
      FileUtil.delete(dir);
    }
  }

  public void testWindowsHiddenDirectory() throws Exception {
    if (!SystemInfo.isWindows) {
      System.err.println(getName() + " skipped: " + SystemInfo.OS_NAME);
      return;
    }

    File file = new File("C:\\Documents and Settings\\desktop.ini");
    if (!file.exists()) {
      System.err.println(getName() + " skipped: missing " + file);
      return;
    }

    String parent = FileUtil.toSystemIndependentName(file.getParent());
    VirtualDirectoryImpl.allowRootAccess(parent);
    try {
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      assertNotNull(virtualFile);

      NewVirtualFileSystem fs = (NewVirtualFileSystem)virtualFile.getFileSystem();
      FileAttributes attributes = fs.getAttributes(virtualFile);
      assertNotNull(attributes);
      assertEquals(FileAttributes.Type.FILE, attributes.type);
      assertEquals(FileAttributes.HIDDEN, attributes.flags);
    }
    finally {
      VirtualDirectoryImpl.disallowRootAccess(parent);
    }
  }

  public void testRefreshSeesLatestDirectoryContents() throws Exception {
    File testDir = FileUtil.createTempDirectory("RefreshChildrenTest." + getName(), null);
    String content = "";
    FileUtil.writeToFile(new File(testDir, "Foo.java"), content);

    LocalFileSystem local = LocalFileSystem.getInstance();
    VirtualFile virtualDir = local.findFileByIoFile(testDir);
    assert virtualDir != null : virtualDir;
    virtualDir.getChildren();
    virtualDir.refresh(false, true);
    checkChildCount(virtualDir, 1);

    FileUtil.writeToFile(new File(testDir, "Bar.java"), content);
    virtualDir.refresh(false, true);
    checkChildCount(virtualDir, 2);
  }

  private static void checkChildCount(VirtualFile virtualDir, int expectedCount) {
    VirtualFile[] children = virtualDir.getChildren();
    if (children.length != expectedCount) {
      System.err.println("children:");
      for (VirtualFile child : children) {
        System.err.println(child.getPath());
      }
    }
    assertEquals(expectedCount, children.length);
  }

  public void testSingleFileRootRefresh() throws Exception {
    File file = FileUtil.createTempFile("test.", ".txt");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    assertTrue(virtualFile.exists());
    assertTrue(virtualFile.isValid());

    virtualFile.refresh(false, false);
    assertFalse(((VirtualFileSystemEntry)virtualFile).isDirty());

    FileUtil.delete(file);
    assertFalse(file.exists());
    virtualFile.refresh(false, false);
    assertFalse(virtualFile.exists());
    assertFalse(virtualFile.isValid());
  }

  public void testBadFileName() throws Exception {
    if (!SystemInfo.isUnix) {
      System.err.println(getName() + " skipped: " + SystemInfo.OS_NAME);
      return;
    }

    final File dir = FileUtil.createTempDirectory("test.", ".dir");
    final File file = FileUtil.createTempFile(dir, "test\\", "\\txt", true);

    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertEquals(0, vDir.getChildren().length);

    ((VirtualFileSystemEntry)vDir).markDirtyRecursively();
    vDir.refresh(false, true);

    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNull(vFile);
  }

  public void testGetAttributesConvertsToAbsolute() throws Exception {
    PersistentFS fs = PersistentFS.getInstance();
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    NewVirtualFile fakeRoot = fs.findRoot("", lfs);
    assertNotNull(fakeRoot);
    File userDir = new File(System.getProperty("user.dir"));
    File[] files = userDir.listFiles();
    File fileToQuery;
    if (files != null && files.length != 0) {
      fileToQuery = files[0];
    }
    else if (userDir.isDirectory()) {
      fileToQuery = FileUtil.createTempFile(userDir, getTestName(false), "", true);
      myFilesToDelete.add(fileToQuery);
    }
    else {
      // can't test
      return;
    }

    FileAttributes attributes = lfs.getAttributes(new FakeVirtualFile(fakeRoot, fileToQuery.getName()));
    assertNull(attributes);

    attributes = lfs.getAttributes(new FakeVirtualFile(fakeRoot, "windows"));
    assertNull(attributes);
    attributes = lfs.getAttributes(new FakeVirtualFile(fakeRoot, "usr"));
    assertNull(attributes);
    attributes = lfs.getAttributes(new FakeVirtualFile(fakeRoot, "Users"));
    assertNull(attributes);
  }

  public void testCopyToPointDir() throws Exception {
    File top = createTempDirectory(false);
    File sub = IoTestUtil.createTestDir(top, "sub");
    File file = IoTestUtil.createTestFile(top, "file.txt", "hi there");

    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile topDir = lfs.refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    VirtualFile sourceFile = lfs.refreshAndFindFileByIoFile(file);
    assertNotNull(sourceFile);
    VirtualFile parentDir = lfs.refreshAndFindFileByIoFile(sub);
    assertNotNull(parentDir);
    assertEquals(2, topDir.getChildren().length);

    try {
      sourceFile.copy(this, parentDir, ".");
      fail("Copying a file into a '.' path should have failed");
    }
    catch (IOException e) {
      System.out.println(e.getMessage());
    }

    topDir.refresh(false, true);
    assertTrue(topDir.exists());
    assertEquals(2, topDir.getChildren().length);
  }
}
