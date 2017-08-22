/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalFileSystemTest extends PlatformTestCase {
  private LocalFileSystem myFS;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null) {
            boolean shouldBeValid = !(event instanceof VFileCreateEvent);
            assertEquals(event.toString(), shouldBeValid, file.isValid());
          }
        }
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null) {
            boolean shouldBeValid = !(event instanceof VFileDeleteEvent);
            assertEquals(event.toString(), shouldBeValid, file.isValid());
          }
        }
      }
    });

    myFS = LocalFileSystem.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myFS = null;
    super.tearDown();
  }

  public void testBasics() throws IOException {
    VirtualFile dir = PlatformTestUtil.notNull(myFS.refreshAndFindFileByIoFile(createTempDirectory(false)));
    assertTrue(dir.isValid());
    assertEquals(0, dir.getChildren().length);

    VirtualFile child = WriteAction.compute(() -> dir.createChildData(this, "child.txt"));
    assertTrue(child.isValid());
    assertTrue(new File(child.getPath()).exists());
    assertEquals(1, dir.getChildren().length);
    assertEquals(child, dir.getChildren()[0]);

    WriteAction.run(() -> child.delete(this));
    assertFalse(child.isValid());
    assertFalse(new File(child.getPath()).exists());
    assertEquals(0, dir.getChildren().length);
  }

  public void testChildrenAccessedButNotCached() throws Exception {
    File dir = createTempDirectory(false);
    ManagingFS managingFS = ManagingFS.getInstance();

    VirtualFile vFile = myFS.refreshAndFindFileByPath(dir.getPath());
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

    VirtualFile childVFile = myFS.refreshAndFindFileByPath(child.getPath());
    assertNotNull(childVFile);
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));

    VirtualFile subdirVFile = myFS.refreshAndFindFileByPath(subdir.getPath());
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

    VirtualFile subChildVFile = myFS.refreshAndFindFileByPath(subChild.getPath());
    assertNotNull(subChildVFile);
    assertTrue(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));
    assertFalse(managingFS.areChildrenLoaded(subdirVFile));
    assertTrue(managingFS.wereChildrenAccessed(subdirVFile));
  }

  public void testRefreshAndFindFile() throws Exception {
    doTestRefreshAndFindFile(createTempDirectory());
  }

  public static void doTestRefreshAndFindFile(@NotNull File tempDir) throws IOException {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile tempVDir = lfs.refreshAndFindFileByPath(tempDir.getPath());
    assertNotNull(tempVDir);

    File file1 = new File(tempDir, "some/nested/dir/hello.txt");
    FileUtil.writeToFile(file1, "hello");
    assertNotNull(lfs.refreshAndFindFileByPath(file1.getPath()));

    File file2 = new File(tempDir, "another/nested/dir/hello.txt");
    FileUtil.writeToFile(file2, "hello again");
    assertNotNull(lfs.refreshAndFindFileByPath(file2.getPath()));

    tempVDir.getChildren();
    tempVDir.refresh(false, true);
    File file3 = new File(tempDir, "one/more/nested/dir/hello.txt");
    FileUtil.writeToFile(file3, "hello again");
    assertNotNull(lfs.refreshAndFindFileByPath(file3.getPath()));
  }

  public void testRefreshEquality() throws IOException {
    doTestRefreshEquality(createTempDirectory());
  }

  public static void doTestRefreshEquality(@NotNull File tempDir) throws IOException {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile tempVDir = lfs.refreshAndFindFileByPath(tempDir.getPath());
    assertNotNull(tempVDir);
    assertEquals(0, tempVDir.getChildren().length);

    FileUtil.writeToFile(new File(tempDir, "file1.txt"), "hello");
    tempVDir.refresh(false, false);
    assertEquals(1, tempVDir.getChildren().length);
    FileUtil.writeToFile(new File(tempDir, "file2.txt"), "hello");
    tempVDir.refresh(false, true);
    assertEquals(2, tempVDir.getChildren().length);

    File tempDir1 = IoTestUtil.createTestDir(tempDir, "sub1");
    VirtualFile tempVDir1 = lfs.refreshAndFindFileByIoFile(tempDir1);
    assertNotNull(tempVDir1);
    FileUtil.writeToFile(new File(tempDir1, "file.txt"), "hello");
    tempVDir1.refresh(false, false);
    assertEquals(1, tempVDir1.getChildren().length);

    File tempDir2 = IoTestUtil.createTestDir(tempDir, "sub2");
    VirtualFile tempVDir2 = lfs.refreshAndFindFileByIoFile(tempDir2);
    assertNotNull(tempVDir2);
    FileUtil.writeToFile(new File(tempDir2, "file.txt"), "hello");
    tempVDir2.refresh(false, true);
    assertEquals(1, tempVDir2.getChildren().length);
  }

  public void testCopyFile() throws Exception {
    File fromDir = createTempDirectory();
    File toDir = createTempDirectory();

    VirtualFile fromVDir = myFS.findFileByPath(fromDir.getPath());
    VirtualFile toVDir = myFS.findFileByPath(toDir.getPath());
    assertNotNull(fromVDir);
    assertNotNull(toVDir);
    final VirtualFile fileToCopy = createChildData(fromVDir, "temp_file");
    final byte[] byteContent = {0, 1, 2, 3};
    setBinaryContent(fileToCopy,byteContent);
    final String newName = "new_temp_file";
    final VirtualFile copy = copy(fileToCopy, toVDir, newName);
    assertEquals(newName, copy.getName());
    assertTrue(Arrays.equals(byteContent, copy.contentsToByteArray()));
  }

  public void testCopyDir() throws Exception {
    File fromDir = createTempDirectory();
    File toDir = createTempDirectory();

    VirtualFile fromVDir = myFS.findFileByPath(fromDir.getPath());
    VirtualFile toVDir = myFS.findFileByPath(toDir.getPath());
    assertNotNull(fromVDir);
    assertNotNull(toVDir);
    final VirtualFile dirToCopy = createChildDirectory(fromVDir, "dir");
    final VirtualFile file = createChildData(dirToCopy, "temp_file");
    setBinaryContent(file,new byte[]{0, 1, 2, 3});
    final String newName = "dir";
    final VirtualFile dirCopy = copy(dirToCopy, toVDir, newName);
    assertEquals(newName, dirCopy.getName());
    PlatformTestUtil.assertDirectoriesEqual(toVDir, fromVDir);
  }

  public void testUnicodeNames() throws Exception {
    final File dirFile = createTempDirectory();
    final String name = "a-\u00df-123123123.txt";
    final File childFile = new File(dirFile, name);
    boolean created = childFile.createNewFile();
    assertTrue(created || childFile.exists());

    final VirtualFile dir = myFS.refreshAndFindFileByIoFile(dirFile);
    assertNotNull(dir);

    final VirtualFile child = myFS.refreshAndFindFileByIoFile(childFile);
    assertNotNull(Arrays.toString(dir.getChildren()), child);

    assertTrue(childFile.delete());
  }

  public void testFindRoot() {
    VirtualFile root = myFS.findFileByPath("wrong_path");
    assertNull(root);

    VirtualFile root2;
    if (SystemInfo.isWindows) {
      root = myFS.findFileByPath("\\\\unit-133");
      assertNotNull(root);
      root2 = myFS.findFileByPath("//UNIT-133");
      assertNotNull(root2);
      assertEquals(String.valueOf(root2), root, root2);
      RefreshQueue.getInstance().processSingleEvent(new VFileDeleteEvent(this, root, false));

      root = myFS.findFileByIoFile(new File("\\\\unit-133"));
      assertNotNull(root);
      RefreshQueue.getInstance().processSingleEvent(new VFileDeleteEvent(this, root, false));

      if (new File("c:").exists()) {
        root = myFS.findFileByPath("c:");
        assertNotNull(root);
        assertEquals("C:/", root.getPath());

        root2 = myFS.findFileByPath("C:\\");
        assertSame(String.valueOf(root), root, root2);

        VirtualFileManager fm = VirtualFileManager.getInstance();
        root = fm.findFileByUrl("file://C:/");
        assertNotNull(root);
        root2 = fm.findFileByUrl("file:///c:/");
        assertSame(String.valueOf(root), root, root2);
      }
    }
    else if (SystemInfo.isUnix) {
      root = myFS.findFileByPath("/");
      assertNotNull(root);
      assertEquals("/", root.getPath());
    }

    root = myFS.findFileByPath("");
    assertNotNull(root);

    File jarFile = IoTestUtil.createTestJar();
    assertNotNull(getVirtualFile(jarFile));
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
    VirtualFile virtualFile = myFS.refreshAndFindFileByIoFile(file);
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
    GeneralSettings settings = GeneralSettings.getInstance();
    boolean safeWrite = settings.isUseSafeWrite();
    SafeWriteRequestor requestor = new SafeWriteRequestor() { };
    File dir = FileUtil.createTempDirectory("hardlinks.", ".dir", false);
    byte[] testData = "hello".getBytes(CharsetToolkit.UTF8_CHARSET);

    try {
      settings.setUseSafeWrite(false);

      File targetFile = new File(dir, "targetFile");
      assertTrue(targetFile.createNewFile());
      File hardLinkFile = IoTestUtil.createHardLink(targetFile.getAbsolutePath(), "hardLinkFile");

      VirtualFile file = myFS.refreshAndFindFileByIoFile(targetFile);
      assertNotNull(file);
      setBinaryContent(file, testData, 0, 0, requestor);
      assertTrue(file.getLength() > 0);

      if (SystemInfo.isWindows) {
        byte[] bytes = FileUtil.loadFileBytes(hardLinkFile);
        assertEquals(testData.length, bytes.length);
      }

      VirtualFile check = myFS.refreshAndFindFileByIoFile(hardLinkFile);
      assertNotNull(check);
      assertEquals(file.getLength(), check.getLength());
      assertEquals("hello", VfsUtilCore.loadText(check));
    }
    finally {
      settings.setUseSafeWrite(safeWrite);
      FileUtil.delete(dir);
    }
  }

  public void testWindowsHiddenDirectory() {
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
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), parent);

    VirtualFile virtualFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);

    NewVirtualFileSystem fs = (NewVirtualFileSystem)virtualFile.getFileSystem();
    FileAttributes attributes = fs.getAttributes(virtualFile);
    assertNotNull(attributes);
    assertEquals(FileAttributes.Type.FILE, attributes.type);
    assertEquals(FileAttributes.HIDDEN, attributes.flags);
  }

  public void testRefreshSeesLatestDirectoryContents() throws Exception {
    File testDir = FileUtil.createTempDirectory("RefreshChildrenTest." + getName(), null);
    String content = "";
    FileUtil.writeToFile(new File(testDir, "Foo.java"), content);

    VirtualFile virtualDir = getVirtualFile(testDir);
    assertNotNull(virtualDir);
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
    VirtualFile virtualFile = myFS.refreshAndFindFileByIoFile(file);
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

    final VirtualFile vDir = myFS.refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertEquals(0, vDir.getChildren().length);

    ((VirtualFileSystemEntry)vDir).markDirtyRecursively();
    vDir.refresh(false, true);

    final VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNull(vFile);
  }

  public void testNoMoreFakeRoots() {
    try {
      PersistentFS.getInstance().findRoot("", myFS);
      fail("should fail by assertion in PersistentFsImpl.findRoot()");
    }
    catch (Throwable t) {
      String message = t.getMessage();
      assertTrue(message, message.startsWith("Invalid root"));
    }
  }

  public void testCopyToPointDir() throws Exception {
    File top = createTempDirectory(false);
    File sub = IoTestUtil.createTestDir(top, "sub");
    File file = IoTestUtil.createTestFile(top, "file.txt", "hi there");

    VirtualFile topDir = myFS.refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    VirtualFile sourceFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(sourceFile);
    VirtualFile parentDir = myFS.refreshAndFindFileByIoFile(sub);
    assertNotNull(parentDir);
    assertEquals(2, topDir.getChildren().length);

    try {
      sourceFile.copy(this, parentDir, ".");
      fail("Copying a file into a '.' path should have failed");
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    topDir.refresh(false, true);
    assertTrue(topDir.exists());
    assertEquals(2, topDir.getChildren().length);
  }

  public void testCaseInsensitiveRename() throws IOException {
    File file = createTempFile("file.txt", "");
    File home = PlatformTestUtil.notNull(file.getParentFile());
    assertOrderedEquals(Collections.singletonList("file.txt"), home.list());

    VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    WriteAction.run(() -> vFile.rename(LocalFileSystemTest.class, "FILE.txt"));

    assertEquals("FILE.txt", vFile.getName());
    assertOrderedEquals(Collections.singletonList("FILE.txt"), home.list());
  }

  public void testFileCaseChange() throws Exception {
    if (SystemInfo.isFileSystemCaseSensitive) {
      System.err.println("Ignored: case-insensitive FS required");
      return;
    }

    File top = createTempDirectory(false);
    File file = IoTestUtil.createTestFile(top, "file.txt", "test");

    VirtualFile topDir = myFS.refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    VirtualFile sourceFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(sourceFile);

    String newName = StringUtil.capitalize(file.getName());
    FileUtil.rename(file, newName);
    topDir.refresh(false, true);
    assertFalse(((VirtualDirectoryImpl)topDir).allChildrenLoaded());
    assertTrue(sourceFile.isValid());
    assertEquals(newName, sourceFile.getName());

    topDir.getChildren();
    newName = newName.toLowerCase(Locale.ENGLISH);
    FileUtil.rename(file, newName);
    topDir.refresh(false, true);
    assertTrue(((VirtualDirectoryImpl)topDir).allChildrenLoaded());
    assertTrue(sourceFile.isValid());
    assertEquals(newName, sourceFile.getName());
  }

  public void testPartialRefresh() throws Exception {
    File top = createTempDirectory(false);
    doTestPartialRefresh(top);
  }

  public static void doTestPartialRefresh(@NotNull File top) {
    File sub = IoTestUtil.createTestDir(top, "sub");
    File file1 = IoTestUtil.createTestFile(top, "file1.txt", ".");
    File file2 = IoTestUtil.createTestFile(sub, "file2.txt", ".");

    LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile topDir = lfs.refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    VirtualFile subDir = lfs.refreshAndFindFileByIoFile(sub);
    assertNotNull(subDir);
    VirtualFile vFile1 = lfs.refreshAndFindFileByIoFile(file1);
    assertNotNull(vFile1);
    VirtualFile vFile2 = lfs.refreshAndFindFileByIoFile(file2);
    assertNotNull(vFile2);
    topDir.refresh(false, true);

    Set<VirtualFile> processed = ContainerUtil.newHashSet();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      IoTestUtil.updateFile(file1, "++");
      IoTestUtil.updateFile(file2, "++");
      ((NewVirtualFile)topDir).markDirtyRecursively();
      topDir.refresh(false, false);
      assertThat(processed).containsExactly(vFile1);  // vFile2 should stay unvisited after non-recursive refresh

      processed.clear();
      topDir.refresh(false, true);
      assertThat(processed).containsExactly(vFile2);  // vFile2 changes should be picked up by a next recursive refresh
    }
    finally {
      connection.disconnect();
    }
  }

  public void testSymlinkTargetBlink() throws Exception {
    if (!SystemInfo.areSymLinksSupported) {
      System.err.println("Ignored: symlinks not supported: "+SystemInfo.getOsNameAndVersion());
      return;
    }

    File top = createTempDirectory(true);
    File target = IoTestUtil.createTestDir(top, "target");
    File link = IoTestUtil.createSymLink(target.getPath(), top.getPath() + "/link");

    VirtualFile vTop = myFS.refreshAndFindFileByIoFile(top);
    assertNotNull(vTop);
    assertTrue(vTop.isValid());
    VirtualFile vTarget = myFS.refreshAndFindFileByIoFile(target);
    assertNotNull(vTarget);
    assertTrue(vTarget.isValid());
    VirtualFile vLink = myFS.refreshAndFindFileByIoFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertTrue(vLink.isDirectory());

    FileUtil.delete(target);
    vTop.refresh(false, true);
    assertFalse(vTarget.isValid());
    assertFalse(vLink.isValid());
    vLink = myFS.refreshAndFindFileByIoFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertFalse(vLink.isDirectory());

    FileUtil.createDirectory(target);
    vTop.refresh(false, true);
    assertFalse(vLink.isValid());
    vLink = myFS.refreshAndFindFileByIoFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertTrue(vLink.isDirectory());
  }

  public void testInterruptedRefresh() throws Exception {
    File top = createTempDirectory(false);
    doTestInterruptedRefresh(top);
  }

  public static void doTestInterruptedRefresh(@NotNull File top) throws Exception {
    for (int i = 1; i <= 3; i++) {
      File sub = IoTestUtil.createTestDir(top, "sub_" + i);
      for (int j = 1; j <= 3; j++) {
        IoTestUtil.createTestDir(sub, "sub_" + j);
      }
    }
    Files.walkFileTree(top.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        for (int k = 1; k <= 3; k++) {
          IoTestUtil.createTestFile(dir.toFile(), "file_" + k, ".");
        }
        return FileVisitResult.CONTINUE;
      }
    });

    VirtualFile topDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    Set<VirtualFile> files = ContainerUtil.newHashSet();
    VfsUtilCore.processFilesRecursively(topDir, file -> { if (!file.isDirectory()) files.add(file); return true; });
    assertEquals(39, files.size());  // 13 dirs of 3 files
    topDir.refresh(false, true);

    Set<VirtualFile> processed = ContainerUtil.newHashSet();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      files.forEach(f -> IoTestUtil.updateFile(new File(f.getPath()), "+++"));
      ((NewVirtualFile)topDir).markDirtyRecursively();

      RefreshWorker.setCancellingCondition(file -> file.getPath().endsWith(top.getName() + "/sub_2/file_2"));
      topDir.refresh(false, true);
      assertThat(processed.size()).isGreaterThan(0).isLessThan(files.size());

      RefreshWorker.setCancellingCondition(null);
      topDir.refresh(false, true);
      assertThat(processed).isEqualTo(files);
    }
    finally {
      connection.disconnect();
      RefreshWorker.setCancellingCondition(null);
    }
  }

  public void testInvalidFileName() throws IOException {
    WriteAction.run(() -> {
      VirtualFile tempDir = myFS.refreshAndFindFileByIoFile(createTempDirectory());
      assertNotNull(tempDir);
      try {
        tempDir.createChildData(this, "a/b");
        fail("invalid file name should have been rejected");
      }
      catch (IOException e) {
        assertEquals(VfsBundle.message("file.invalid.name.error", "a/b"), e.getMessage());
      }
    });
  }

  public void testDuplicateViaRename() throws IOException {
    WriteAction.run(() -> {
      VirtualFile tempDir = myFS.refreshAndFindFileByIoFile(createTempDirectory());
      assertNotNull(tempDir);

      VirtualFile file1 = tempDir.createChildData(this, "a.txt");
      FileUtil.delete(VfsUtilCore.virtualToIoFile(file1));

      VirtualFile file2 = tempDir.createChildData(this, "b.txt");
      try {
        file2.rename(this, "a.txt");
        fail("duplicate file name should have been rejected");
      }
      catch (IOException e) {
        assertEquals(VfsBundle.message("vfs.target.already.exists.error", file1.getPath()), e.getMessage());
      }
    });
  }

  public void testBrokenSymlinkMove() throws IOException {
    if (!SystemInfo.areSymLinksSupported) {
      System.err.println(getName() + " skipped: " + SystemInfo.OS_NAME);
      return;
    }

    File srcDir = IoTestUtil.createTestDir("src");
    File link = IoTestUtil.createSymLink(srcDir.getPath() + "/missing", srcDir.getPath() + "/link", false);
    File dstDir = IoTestUtil.createTestDir("dst");

    WriteAction.run(() -> {
      VirtualFile file = myFS.refreshAndFindFileByIoFile(link);
      assertNotNull(file);

      VirtualFile target = myFS.refreshAndFindFileByIoFile(dstDir);
      assertNotNull(target);

      myFS.moveFile(this, file, target);
    });

    assertOrderedEquals(ArrayUtil.EMPTY_STRING_ARRAY, srcDir.list());
    assertOrderedEquals(new String[]{link.getName()}, dstDir.list());
  }

  public void testFileContentChangeEvents() throws IOException {
    File file = IoTestUtil.createTestFile("file.txt");
    long stamp = file.lastModified();
    VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int[] updated = {0};
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileContentChangeEvent && vFile.equals(event.getFile())) {
            updated[0]++;
            break;
          }
        }
      }
    });

    FileUtil.writeToFile(file, "content");
    assertTrue(file.setLastModified(stamp));
    vFile.refresh(false, false);
    assertEquals(1, updated[0]);

    FileUtil.writeToFile(file, "more content");
    assertTrue(file.setLastModified(stamp));
    vFile.refresh(false, false);
    assertEquals(2, updated[0]);
  }

  public void testReadOnly() throws IOException {
    File file = IoTestUtil.createTestFile("file.txt");
    VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    assertWritable(file, vFile, true);

    WriteAction.run(() -> vFile.setWritable(false));
    assertWritable(file, vFile, false);
    vFile.refresh(false, false);
    assertWritable(file, vFile, false);

    WriteAction.run(() -> vFile.setWritable(true));
    assertWritable(file, vFile, true);
    vFile.refresh(false, false);
    assertWritable(file, vFile, true);
  }

  private static void assertWritable(File file, VirtualFile vFile, boolean expected) {
    assertEquals(expected, file.canWrite());
    assertEquals(expected, ObjectUtils.assertNotNull(FileSystemUtil.getAttributes(file)).isWritable());
    assertEquals(expected, vFile.isWritable());
  }
}