// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.core.CoreBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystemMarker;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class LocalFileSystemTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private LocalFileSystem myFS;

  @Before
  public void setUp() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null && !(file.getFileSystem() instanceof TempFileSystemMarker)) {
            boolean shouldBeValid = !(event instanceof VFileCreateEvent);
            assertEquals(event.toString(), shouldBeValid, file.isValid());
          }
        }
      }

      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null && !(file.getFileSystem() instanceof TempFileSystemMarker)) {
            boolean shouldBeValid = !(event instanceof VFileDeleteEvent);
            assertEquals(event.toString(), shouldBeValid, file.isValid());
          }
        }
      }
    });

    myFS = LocalFileSystem.getInstance();
  }

  @After
  public void tearDown() {
    myFS = null;
  }

  @Test
  public void testBasics() throws IOException {
    VirtualFile dir = requireNonNull(myFS.refreshAndFindFileByIoFile(tempDir.newDirectory("xxx")));
    assertTrue(dir.isValid());
    assertEquals(0, dir.getChildren().length);

    VirtualFile child = WriteAction.computeAndWait(() -> dir.createChildData(this, "child.txt"));
    assertTrue(child.isValid());
    assertTrue(new File(child.getPath()).exists());
    assertEquals(1, dir.getChildren().length);
    assertEquals(child, dir.getChildren()[0]);

    WriteAction.runAndWait(() -> child.delete(this));
    assertFalse(child.isValid());
    assertFalse(new File(child.getPath()).exists());
    assertEquals(0, dir.getChildren().length);
  }

  @Test
  public void findChildWithSpecialName() {
    VirtualFile dir = requireNonNull(myFS.refreshAndFindFileByIoFile(tempDir.newDirectory("xxx")));
    assertFalse(((VirtualDirectoryImpl)dir).allChildrenLoaded());
    assertNull(dir.findChild("."));
    assertNull(dir.findChild(".."));
  }

  @Test
  public void testChildrenAccessedButNotCached() throws IOException {
    File dir = tempDir.newDirectory("xxx");
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

  @Test
  public void testRefreshAndFindFile() throws IOException {
    doTestRefreshAndFindFile(tempDir.newDirectory("top"));
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

  @Test
  public void testRefreshEquality() throws IOException {
    doTestRefreshEquality(tempDir.newDirectory("top"));
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

    File tempDir1 = createTestDir(tempDir, "sub1");
    VirtualFile tempVDir1 = lfs.refreshAndFindFileByIoFile(tempDir1);
    assertNotNull(tempVDir1);
    FileUtil.writeToFile(new File(tempDir1, "file.txt"), "hello");
    tempVDir1.refresh(false, false);
    assertEquals(1, tempVDir1.getChildren().length);

    File tempDir2 = createTestDir(tempDir, "sub2");
    VirtualFile tempVDir2 = lfs.refreshAndFindFileByIoFile(tempDir2);
    assertNotNull(tempVDir2);
    FileUtil.writeToFile(new File(tempDir2, "file.txt"), "hello");
    tempVDir2.refresh(false, true);
    assertEquals(1, tempVDir2.getChildren().length);
  }

  @Test
  public void testFindFileSeparatorNormalization() {
    tempDir.newFile("a/b/c/f");
    VirtualFile file = myFS.refreshAndFindFileByPath(tempDir.getRoot() + "/a\\b//c\\f");
    assertNotNull(file);
    assertEquals("f", file.getName());
    assertEquals("c", file.getParent().getName());
    assertEquals("b", file.getParent().getParent().getName());
    assertEquals("a", file.getParent().getParent().getParent().getName());
    assertEquals(file, myFS.refreshAndFindFileByIoFile(new File(tempDir.getRoot(), "a\\b//c\\f")));
    assertEquals(file, myFS.refreshAndFindFileByNioFile(tempDir.getRoot().toPath().resolve("a\\b//c\\f")));
  }

  @Test
  public void testCopyFile() throws IOException {
    runInEdtAndWait(() -> {
      File fromDir = tempDir.newDirectory("from");
      File toDir = tempDir.newDirectory("to");

      VirtualFile fromVDir = myFS.refreshAndFindFileByIoFile(fromDir);
      VirtualFile toVDir = myFS.refreshAndFindFileByIoFile(toDir);
      assertNotNull(fromVDir);
      assertNotNull(toVDir);
      VirtualFile fileToCopy = WriteAction.compute(() -> fromVDir.createChildData(this, "temp_file"));
      byte[] byteContent = {0, 1, 2, 3};
      WriteAction.run(() -> fileToCopy.setBinaryContent(byteContent));
      String newName = "new_temp_file";
      VirtualFile copy = WriteAction.compute(() -> fileToCopy.copy(this, toVDir, newName));
      assertEquals(newName, copy.getName());
      assertArrayEquals(byteContent, copy.contentsToByteArray());
    });
  }

  @Test
  public void testCopyDir() throws IOException {
    runInEdtAndWait(() -> {
      File fromDir = tempDir.newDirectory("from");
      File toDir = tempDir.newDirectory("to");

      VirtualFile fromVDir = myFS.refreshAndFindFileByIoFile(fromDir);
      VirtualFile toVDir = myFS.refreshAndFindFileByIoFile(toDir);
      assertNotNull(fromVDir);
      assertNotNull(toVDir);
      VirtualFile dirToCopy = WriteAction.compute(() -> fromVDir.createChildDirectory(this, "dir"));
      VirtualFile file = WriteAction.compute(() -> dirToCopy.createChildData(this, "temp_file"));
      WriteAction.run(() -> file.setBinaryContent(new byte[]{0, 1, 2, 3}));
      String newName = "dir";
      VirtualFile dirCopy = WriteAction.compute(() -> dirToCopy.copy(this, toVDir, newName));
      assertEquals(newName, dirCopy.getName());
      PlatformTestUtil.assertDirectoriesEqual(toVDir, fromVDir);
    });
  }

  @Test
  public void testUnicodeName() {
    String name = getUnicodeName();
    assumeTrue(name != null);
    File childFile = tempDir.newFile(name + ".txt");

    VirtualFile dir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
    assertNotNull(dir);

    VirtualFile child = myFS.refreshAndFindFileByIoFile(childFile);
    assertNotNull(Arrays.toString(dir.getChildren()) + " : " + Arrays.toString(tempDir.getRoot().list()), child);
  }

  @Test
  public void testFindRoot() {
    assertNull(myFS.findFileByPath("wrong_path"));

    if (SystemInfo.isWindows) {
      String systemDrive = System.getenv("SystemDrive");
      VirtualFile root = myFS.findFileByPath(systemDrive.toLowerCase(Locale.ENGLISH));
      assertNotNull(root);
      assertEquals(systemDrive.toUpperCase(Locale.ENGLISH) + '/', root.getPath());
      VirtualFile root2 = myFS.findFileByPath(systemDrive.toUpperCase(Locale.ENGLISH) + '\\');
      assertSame(String.valueOf(root), root, root2);

      VirtualFileManager fm = VirtualFileManager.getInstance();
      root = fm.findFileByUrl("file://" + systemDrive.toUpperCase(Locale.ENGLISH) + '/');
      assertNotNull(root);
      root2 = fm.findFileByUrl("file:///" + systemDrive.toLowerCase(Locale.ENGLISH) + '/');
      assertSame(String.valueOf(root), root, root2);

      assertNull(myFS.findFileByPath("\\\\some-unc-server"));
      assertNull(myFS.findFileByPath("//SOME-UNC-SERVER"));
      assertNull(myFS.findFileByIoFile(new File("\\\\some-unc-server")));
      assertNull(myFS.findFileByPath("\\\\wsl$"));
      assertNull(myFS.findFileByPath("\\\\?\\C:\\"));

      root = myFS.findFileByPath("\\\\some-unc-server\\some-unc-share");
      assertNotNull(root);
      root2 = myFS.findFileByPath("//SOME-UNC-SERVER/SOME-UNC-SHARE");
      assertSame(String.valueOf(root), root, root2);
      assertEquals("\\\\some-unc-server\\some-unc-share", root.getPresentableName());
      RefreshQueue.getInstance().processEvents(false, List.of(new VFileDeleteEvent(this, root)));
    }
    else if (SystemInfo.isUnix) {
      VirtualFile root = myFS.findFileByPath("/");
      assertNotNull(root);
      assertEquals("/", root.getPath());
    }

    VirtualFile root = myFS.findFileByPath("");
    assertNotNull(root);

    File jarFile = createTestJar(tempDir.newFile("test.jar"));
    assertNotNull(myFS.refreshAndFindFileByIoFile(jarFile));
    root = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath() + "!/");
    assertNotNull(root);

    VirtualFile root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath().replace(File.separator, "//") + "!/");
    assertEquals(String.valueOf(root2), root, root2);

    if (!SystemInfo.isFileSystemCaseSensitive) {
      root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.getPath().toUpperCase(Locale.US) + "!/");
      assertEquals(String.valueOf(root2), root, root2);
    }
  }

  @Test
  public void testUncOperations() throws IOException {
    assumeWindows();
    Path uncRootPath = Paths.get(toLocalUncPath(tempDir.getRoot().getPath()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    VirtualFile uncRootFile = myFS.refreshAndFindFileByPath(uncRootPath.toString());
    assertNotNull("not found: " + uncRootPath, uncRootFile);
    assertTrue(uncRootFile.isValid());

    try {
      assertThat(uncRootFile.getChildren()).isEmpty();

      byte[] data = "original data".getBytes(StandardCharsets.UTF_8);
      Path testLocalPath1 = Files.write(tempDir.newFile("test1.txt").toPath(), data);
      uncRootFile.refresh(false, false);
      VirtualFile testFile1 = uncRootFile.findChild(testLocalPath1.getFileName().toString());
      assertNotNull("not found: " + testLocalPath1, testFile1);
      assertTrue("invalid: " + testFile1, testFile1.isValid());
      assertThat(uncRootFile.getChildren()).hasSize(1);

      assertThat(testFile1.contentsToByteArray(false)).isEqualTo(data);
      data = "new content".getBytes(StandardCharsets.UTF_8);
      Files.write(testLocalPath1, data);
      uncRootFile.refresh(false, false);
      assertThat(testFile1.contentsToByteArray(false)).isEqualTo(data);

      VirtualFile testFile2 = WriteAction.computeAndWait(() -> uncRootFile.createChildData(this, "test2.txt"));
      Path testLocalPath2 = tempDir.getRoot().toPath().resolve(testFile2.getName());
      assertThat(testLocalPath2).isRegularFile();
      uncRootFile.refresh(false, false);
      assertTrue("invalid: " + testFile1, testFile1.isValid());
      assertTrue("invalid: " + testFile2, testFile2.isValid());
      WriteAction.runAndWait(() -> testFile2.delete(this));
      assertTrue("invalid: " + testFile1, testFile1.isValid());
      assertFalse("still valid: " + testFile2, testFile2.isValid());
      assertThat(testLocalPath2).doesNotExist();

      Files.delete(testLocalPath1);
      uncRootFile.refresh(false, false);
      assertFalse("still valid: " + testFile1, testFile1.isValid());
      assertThat(uncRootFile.getChildren()).isEmpty();
    }
    finally {
      RefreshQueue.getInstance().processEvents(false, List.of(new VFileDeleteEvent(this, uncRootFile)));
      assertFalse("still valid: " + uncRootFile, uncRootFile.isValid());
    }
  }

  @Test
  public void testFileLength() throws IOException {
    File file = tempDir.newFile("test.txt");
    FileUtil.writeToFile(file, "hello");
    VirtualFile virtualFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    String s = VfsUtilCore.loadText(virtualFile);
    assertEquals("hello", s);
    assertEquals(5, virtualFile.getLength());

    FileUtil.writeToFile(file, "new content");
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContent(((VirtualFileWithId)virtualFile).getId());
    s = VfsUtilCore.loadText(virtualFile);
    assertEquals("new content", s);
    assertEquals(11, virtualFile.getLength());
  }

  @Test
  public void testHardLinks() throws IOException {
    GeneralSettings settings = GeneralSettings.getInstance();
    boolean safeWrite = settings.isUseSafeWrite();
    SafeWriteRequestor requestor = new SafeWriteRequestor() { };
    byte[] testData = "hello".getBytes(StandardCharsets.UTF_8);

    try {
      settings.setUseSafeWrite(false);

      File targetFile = tempDir.newFile("targetFile");
      File hardLinkFile = new File(tempDir.getRoot(), "hardLinkFile");
      Files.createLink(hardLinkFile.toPath(), targetFile.toPath());

      VirtualFile file = myFS.refreshAndFindFileByIoFile(targetFile);
      assertNotNull(file);
      WriteAction.runAndWait(() -> file.setBinaryContent(testData, 0, 0, requestor));
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
    }
  }

  @Test
  public void testWindowsHiddenDirectory() {
    assumeWindows();

    File file = new File("C:\\Documents and Settings\\desktop.ini");
    assumeTrue("Documents and Settings assumed to exist", file.exists());

    String parent = FileUtil.toSystemIndependentName(file.getParent());
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), parent);

    VirtualFile virtualFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);

    NewVirtualFileSystem fs = (NewVirtualFileSystem)virtualFile.getFileSystem();
    FileAttributes attributes = fs.getAttributes(virtualFile);
    assertNotNull(attributes);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertTrue(attributes.isHidden());
  }

  @Test
  public void testRefreshSeesLatestDirectoryContents() throws IOException {
    String content = "";
    FileUtil.writeToFile(new File(tempDir.getRoot(), "Foo.java"), content);

    VirtualFile virtualDir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
    assertNotNull(virtualDir);
    virtualDir.getChildren();
    virtualDir.refresh(false, true);
    assertThat(virtualDir.getChildren()).hasSize(1);

    FileUtil.writeToFile(new File(tempDir.getRoot(), "Bar.java"), content);
    virtualDir.refresh(false, true);
    assertEquals(2, virtualDir.getChildren().length);
  }

  @Test
  public void testSingleFileRootRefresh() throws IOException {
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

  @Test
  public void testBadFileNameUnderUnix() {
    assumeUnix();

    File file = tempDir.newFile("test\\file.txt");
    VirtualFile vDir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
    assertNotNull(vDir);
    assertThat(vDir.getChildren()).isEmpty();

    ((VirtualFileSystemEntry)vDir).markDirtyRecursively();
    vDir.refresh(false, true);
    assertNull(myFS.refreshAndFindFileByIoFile(file));
  }

  @Test
  public void testNoMoreFakeRoots() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      try {
        ManagingFS.getInstance().findRoot("", myFS);
        fail("should fail by assertion in PersistentFsImpl.findRoot()");
      }
      catch (Throwable t) {
        String message = t.getMessage();
        assertTrue(message, message.startsWith("Invalid root"));
      }
    });
  }

  @Test
  public void testFindRootWithDeepNestedFileMustThrow() {
    try {
      File d = tempDir.newDirectory();
      VirtualFile vDir = requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(d));
      ManagingFS.getInstance().findRoot(vDir.getPath(), myFS);
      fail("should fail by assertion in PersistentFsImpl.findRoot()");
    }
    catch (Throwable t) {
      String message = t.getMessage();
      assertTrue(message, message.startsWith("Must pass FS root path, but got"));
    }
  }

  @Test
  public void testCopyToPointDir() {
    File sub = tempDir.newDirectory("sub");
    File file = tempDir.newFile("file.txt");

    VirtualFile topDir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
    assertNotNull(topDir);
    VirtualFile sourceFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(sourceFile);
    VirtualFile parentDir = myFS.refreshAndFindFileByIoFile(sub);
    assertNotNull(parentDir);
    assertEquals(2, topDir.getChildren().length);

    try {
      WriteAction.runAndWait(() -> sourceFile.copy(this, parentDir, ".") );
      fail("Copying a file into a '.' path should have failed");
    }
    catch (IOException ignored) {
    }

    topDir.refresh(false, true);
    assertTrue(topDir.exists());
    assertEquals(2, topDir.getChildren().length);
  }

  @Test
  public void testCaseInsensitiveRename() throws IOException {
    File file = tempDir.newFile("file.txt");
    File home = requireNonNull(file.getParentFile());
    assertThat(home.list()).containsExactly("file.txt");

    VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    WriteAction.runAndWait(() -> vFile.rename(LocalFileSystemTest.class, "FILE.txt"));

    assertEquals("FILE.txt", vFile.getName());
    assertThat(home.list()).containsExactly("FILE.txt");
  }

  @Test
  public void testFileCaseChange() throws IOException {
    assumeCaseInsensitiveFS();

    File file = tempDir.newFile("file.txt");

    VirtualFile topDir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
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

  @Test
  public void testPartialRefresh() throws IOException {
    doTestPartialRefresh(tempDir.newDirectory("top"));
  }

  public static void doTestPartialRefresh(@NotNull File top) throws IOException {
    File sub = createTestDir(top, "sub");
    File file1 = createTestFile(top, "file1.txt", ".");
    File file2 = createTestFile(sub, "file2.txt", ".");

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

    Set<VirtualFile> processed = new HashSet<>();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      FileUtil.writeToFile(file1, "++");
      FileUtil.writeToFile(file2, "++");
      ((NewVirtualFile)topDir).markDirtyRecursively();
      topDir.refresh(false, false);
      assertThat(processed).containsExactly(vFile1);  // `vFile2` should stay unvisited after non-recursive refresh

      processed.clear();
      topDir.refresh(false, true);
      assertThat(processed).containsExactly(vFile2);  // `vFile2` changes should be picked up by the next recursive refresh
    }
    finally {
      connection.disconnect();
    }
  }

  @Test
  public void testSymlinkTargetBlink() throws IOException {
    assumeSymLinkCreationIsSupported();

    File target = tempDir.newDirectory("target");
    File link = new File(tempDir.getRoot(), "link");
    @NotNull Path link1 = link.toPath();
    @NotNull Path target1 = target.toPath();
    Files.createSymbolicLink(link1, target1);

    VirtualFile vTop = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
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

  @Test
  public void testInterruptedRefresh() throws IOException {
    doTestInterruptedRefresh(tempDir.newDirectory("top"));
  }

  public static void doTestInterruptedRefresh(@NotNull File top) throws IOException {
    for (int i = 1; i <= 3; i++) {
      File sub = createTestDir(top, "sub_" + i);
      for (int j = 1; j <= 3; j++) {
        createTestDir(sub, "sub_" + j);
      }
    }
    Files.walkFileTree(top.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        for (int k = 1; k <= 3; k++) {
          createTestFile(dir.toFile(), "file_" + k, ".");
        }
        return FileVisitResult.CONTINUE;
      }
    });

    VirtualFile topDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(top);
    assertNotNull(topDir);
    Set<VirtualFile> files = new HashSet<>();
    VfsUtilCore.processFilesRecursively(topDir, file -> { if (!file.isDirectory()) files.add(file); return true; });
    assertThat(files).hasSize(39);  // 13 dirs of 3 files
    topDir.refresh(false, true);

    Set<VirtualFile> processed = new HashSet<>();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      files.forEach(f -> writeToFile(new File(f.getPath()), "+++"));
      ((NewVirtualFile)topDir).markDirtyRecursively();

      RefreshSession session = RefreshQueue.getInstance().createSession(false, true, null);
      String stopAt = top.getName() + "/sub_2/sub_2";
      RefreshQueueImpl.setTestListener(file -> {
        if (file.getPath().endsWith(stopAt)) session.cancel();
      });
      session.addFile(topDir);
      session.launch();
      assertThat(processed).hasSizeBetween(1, files.size() - 1);

      RefreshQueueImpl.setTestListener(null);
      topDir.refresh(false, true);
      assertThat(processed).isEqualTo(files);
    }
    finally {
      connection.disconnect();
      RefreshQueueImpl.setTestListener(null);
    }
  }

  @Test
  public void testInvalidFileName() {
    runInEdtAndWait(() -> {
      VirtualFile dir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
      assertNotNull(dir);
      try {
        WriteAction.run(() -> dir.createChildData(this, "a/b"));
        fail("invalid file name should have been rejected");
      }
      catch (IOException e) {
        assertEquals(CoreBundle.message("file.invalid.name.error", "a/b"), e.getMessage());
      }
    });
  }

  @Test
  public void testDuplicateViaRename() throws IOException {
    runInEdtAndWait(() -> {
      VirtualFile dir = myFS.refreshAndFindFileByIoFile(tempDir.getRoot());
      assertNotNull(dir);

      VirtualFile file1 = WriteAction.compute(() -> dir.createChildData(this, "a.txt"));
      FileUtil.delete(VfsUtilCore.virtualToIoFile(file1));

      VirtualFile file2 = WriteAction.compute(() -> dir.createChildData(this, "b.txt"));
      try {
        WriteAction.run(() -> file2.rename(this, "a.txt"));
        fail("duplicate file name should have been rejected");
      }
      catch (IOException e) {
        assertEquals(IdeCoreBundle.message("vfs.target.already.exists.error", file1.getPath()), e.getMessage());
      }
    });
  }

  @Test
  public void testBrokenSymlinkMove() throws IOException {
    assumeSymLinkCreationIsSupported();

    runInEdtAndWait(() -> {
      File srcDir = tempDir.newDirectory("src");
      File link = new File(tempDir.getRoot(), "link");
      @NotNull Path link1 = link.toPath();
      @NotNull Path target1 = new File(tempDir.getRoot(), "missing").toPath();
      Files.createSymbolicLink(link1, target1);
      File dstDir = tempDir.newDirectory("dst");

      VirtualFile file = myFS.refreshAndFindFileByIoFile(link);
      assertNotNull(file);

      VirtualFile target = myFS.refreshAndFindFileByIoFile(dstDir);
      assertNotNull(target);

      WriteAction.run(() -> myFS.moveFile(this, file, target));

      assertThat(srcDir.list()).isEmpty();
      assertThat(dstDir.list()).containsExactly(link.getName());
    });
  }

  @Test
  public void testFileContentChangeEvents() throws IOException {
    File file = tempDir.newFile("file.txt");
    long stamp = file.lastModified();
    VirtualFile vFile = myFS.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int[] updated = {0};
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
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

  @Test
  public void testReadOnly() throws IOException {
    runInEdtAndWait(() -> {
      File file = tempDir.newFile("file.txt");
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
    });
  }

  private static void assertWritable(File file, VirtualFile vFile, boolean expected) {
    assertEquals(expected, file.canWrite());
    assertEquals(expected, requireNonNull(FileSystemUtil.getAttributes(file)).isWritable());
    assertEquals(expected, vFile.isWritable());
  }

  @Test
  public void testMountFilterSanity() {
    VirtualFile userHome = myFS.refreshAndFindFileByPath(SystemProperties.getUserHome());
    assertNotNull(userHome);
    VirtualFile home = userHome.getParent();
    assumeTrue("User home is mapped to root (" + userHome + ")", home != null);
    assertThat(myFS.list(home)).containsExactlyInAnyOrder(new File(home.getPath()).list());
  }

  @Test
  public void testNioPathIsImplementedForDir() {
    File newDir = tempDir.newDirectory("someDir-32");
    VirtualFile newDirFile = myFS.refreshAndFindFileByPath(newDir.getPath());
    assertNotNull(newDirFile);
    assertThat(newDirFile.toNioPath()).isNotNull().isEqualTo(newDir.toPath());
  }

  @Test
  public void testNioPathIsImplementedForFile() {
    File newDir = tempDir.newFile("someFile-32");
    VirtualFile newDirFile = myFS.refreshAndFindFileByPath(newDir.getPath());
    assertNotNull(newDirFile);
    assertThat(newDirFile.toNioPath()).isNotNull().isEqualTo(newDir.toPath());
  }

  @Test
  public void testFindFileByUrlPerformance() {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    Benchmark.newBenchmark("findFileByUrl", () -> {
      for (int i=0; i<10_000_000;i++) {
        assertNull(virtualFileManager.findFileByUrl("temp://"));
      }
    }).start();
  }

  @Test
  public void testFindFileByNioPath() {
    File file = tempDir.newFile("some-new-file");

    VirtualFile nioFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toPath());
    VirtualFile nioFile2 = VirtualFileManager.getInstance().findFileByNioPath(file.toPath());

    assertNotNull(nioFile);
    assertNotNull(nioFile2);
    assertEquals(nioFile, nioFile2);
  }

  @Test
  public void testFindNioPathConsistency() {
    File file = tempDir.newFile("some-new-file");

    VirtualFile ioFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    VirtualFile nioFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toPath());

    assertNotNull(ioFile);
    assertNotNull(nioFile);
    assertEquals(nioFile, ioFile);

    assertEquals(file.toPath(), nioFile.toNioPath());
    assertEquals(file.toPath(), ioFile.toNioPath());
  }

  @Test
  public void caseSensitivityNativeAPIMustWorkInSimpleCasesAndIsCachedInVirtualFileFlags() {
    File file = tempDir.newFile("dir/0");
    assertFalse(FileSystemUtil.isCaseToggleable(file.getName()));

    VirtualDirectoryImpl dir = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());
    assertNotNull(dir);
    assertEquals(CaseSensitivity.UNKNOWN, dir.getChildrenCaseSensitivity());

    VirtualDirectoryImpl.generateCaseSensitivityChangedEventForUnknownCase(dir, file.getName());
    CaseSensitivity expected = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertEquals(expected, dir.getChildrenCaseSensitivity());
    assertEquals(expected == CaseSensitivity.SENSITIVE, dir.isCaseSensitive());
  }

  @Test(timeout = 30_000)
  public void specialFileDoesNotCauseHangs() {
    assumeUnix();

    Path fifo = tempDir.getRoot().toPath().resolve("test.fifo");
    createFifo(fifo.toString());
    VirtualFile file = requireNonNull(myFS.refreshAndFindFileByNioFile(fifo));
    assertThat(file.is(VFileProperty.SPECIAL)).isTrue();
    assertThat(file.getLength()).isEqualTo(0);

    assertThatExceptionOfType(NoSuchFileException.class)
      .isThrownBy(() -> file.getInputStream())
      .withMessageContaining("Not a file");
    assertThatExceptionOfType(NoSuchFileException.class)
      .isThrownBy(() -> file.contentsToByteArray())
      .withMessageContaining("Not a file");
  }

  @Test
  public void directoryListing() {
    var dir = tempDir.newVirtualFile("dir/1").getParent();
    assertDirectoryListing(dir, "1");

    var file = tempDir.newVirtualFile("file");
    assertDirectoryListing(file);

    var missing = new FakeVirtualFile(tempDir.getVirtualFileRoot(), "missing");
    assertDirectoryListing(missing);
  }

  @Test
  public void directoryListingViaSymlink() throws IOException {
    assumeSymLinkCreationIsSupported();

    var dir = tempDir.newFile("dir/1").toPath().getParent();
    var dirLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("dirLink"), dir);
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(dirLink), "1");

    var file = tempDir.newFile("file").toPath();
    var fileLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("fileLink"), file);
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(fileLink));

    var missingLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("missingLink"), Path.of("missing"));
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(missingLink));
  }

  private void assertDirectoryListing(VirtualFile vFile, String... expected) {
    var list1 = myFS.list(vFile);
    var list2 = ((LocalFileSystemImpl)myFS).listWithCaching(vFile);
    ((LocalFileSystemImpl)myFS).clearListCache();
    assertThat(list1).
      containsExactlyInAnyOrder(expected).
      containsExactlyInAnyOrder(list2);
  }

  @Test
  public void testFileContentWithAlmostTooLargeLength() throws IOException {
    byte[] expectedContent = new byte[FileSizeLimit.getDefaultContentLoadLimit()];
    Arrays.fill(expectedContent, (byte) 'a');
    File file = tempDir.newFile("test.txt");
    FileUtil.writeToFile(file, expectedContent);

    byte[] actualContent = myFS.refreshAndFindFileByIoFile(file).contentsToByteArray();
    assertArrayEquals(expectedContent, actualContent);
  }

  @Test
  public void canonicallyCasedHardLink() throws IOException {
    assumeTrue("Requires JRE 21+", JavaVersion.current().isAtLeast(21));
    var original = tempDir.newFile("original").toPath();
    var hardLink = Files.createLink(original.resolveSibling("hardLink"), original);
    assertThat(myFS.refreshAndFindFileByNioFile(hardLink).getName()).isEqualTo(hardLink.getFileName().toString());
    assertThat(myFS.refreshAndFindFileByNioFile(original).getName()).isEqualTo(original.getFileName().toString());
  }

  @Test
  public void canonicallyCasedDecomposedName() {
    assumeTrue("Requires JRE 21+", JavaVersion.current().isAtLeast(21));
    @SuppressWarnings({"NonAsciiCharacters", "SpellCheckingInspection"}) var name = "schön";
    var nfdName = Normalizer.normalize(name, Normalizer.Form.NFD);
    var nfcName = Normalizer.normalize(name, Normalizer.Form.NFC);
    var nfdFile = tempDir.newFile(nfdName).toPath();
    var nfcFile = nfdFile.resolveSibling(nfcName);
    assumeTrue("Filesystem does not support normalization", Files.exists(nfcFile));
    assertThat(myFS.refreshAndFindFileByNioFile(nfcFile).getName()).isEqualTo(nfdName);
  }
}
