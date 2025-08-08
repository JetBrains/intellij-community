// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.CurrentJavaVersion;
import com.intellij.util.system.OS;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.util.io.DirectoryContentSpecKt.jarFile;
import static java.nio.charset.StandardCharsets.UTF_8;
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
    var connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (var event : events) {
          var file = event.getFile();
          if (file != null && !(file.getFileSystem() instanceof TempFileSystemMarker)) {
            var shouldBeValid = !(event instanceof VFileCreateEvent);
            assertEquals(event.toString(), shouldBeValid, file.isValid());
          }
        }
      }

      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (var event : events) {
          var file = event.getFile();
          if (file != null && !(file.getFileSystem() instanceof TempFileSystemMarker)) {
            var shouldBeValid = !(event instanceof VFileDeleteEvent);
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
    var dir = requireNonNull(myFS.refreshAndFindFileByNioFile(tempDir.newDirectoryPath("xxx")));
    assertTrue(dir.isValid());
    assertEquals(0, dir.getChildren().length);

    var child = WriteAction.computeAndWait(() -> dir.createChildData(this, "child.txt"));
    assertTrue(child.isValid());
    assertThat(child.toNioPath()).isRegularFile();
    assertEquals(1, dir.getChildren().length);
    assertEquals(child, dir.getChildren()[0]);

    WriteAction.runAndWait(() -> child.delete(this));
    assertFalse(child.isValid());
    assertThat(child.toNioPath()).doesNotExist();
    assertEquals(0, dir.getChildren().length);
  }

  @Test
  public void findChildWithSpecialName() {
    var dir = requireNonNull(myFS.refreshAndFindFileByNioFile(tempDir.newDirectoryPath("xxx")));
    assertFalse(((VirtualDirectoryImpl)dir).allChildrenLoaded());
    assertNull(dir.findChild("."));
    assertNull(dir.findChild(".."));
  }

  @Test
  public void testChildrenAccessedButNotCached() throws IOException {
    var dir = tempDir.newDirectoryPath("xxx");
    var managingFS = ManagingFS.getInstance();

    var vFile = myFS.refreshAndFindFileByNioFile(dir);
    assertNotNull(vFile);
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertFalse(managingFS.wereChildrenAccessed(vFile));

    var child = Files.createFile(dir.resolve("child"));
    var subdir = Files.createDirectory(dir.resolve("subdir"));
    var subChild = Files.createFile(subdir.resolve("subdir"));

    var childVFile = myFS.refreshAndFindFileByNioFile(child);
    assertNotNull(childVFile);
    assertFalse(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));

    var subdirVFile = myFS.refreshAndFindFileByNioFile(subdir);
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

    var subChildVFile = myFS.refreshAndFindFileByNioFile(subChild);
    assertNotNull(subChildVFile);
    assertTrue(managingFS.areChildrenLoaded(vFile));
    assertTrue(managingFS.wereChildrenAccessed(vFile));
    assertFalse(managingFS.areChildrenLoaded(subdirVFile));
    assertTrue(managingFS.wereChildrenAccessed(subdirVFile));
  }

  @Test
  public void testRefreshAndFindFile() throws IOException {
    doTestRefreshAndFindFile(tempDir.newDirectoryPath("top"));
  }

  @Test
  public void testRefreshEquality() throws IOException {
    doTestRefreshEquality(tempDir.newDirectoryPath("top"));
  }

  @Test
  public void testFindFileSeparatorNormalization() {
    tempDir.newFileNio("a/b/c/f");
    var file = myFS.refreshAndFindFileByPath(tempDir.getRootPath() + "/a\\b//c\\f");
    assertNotNull(file);
    assertEquals("f", file.getName());
    assertEquals("c", file.getParent().getName());
    assertEquals("b", file.getParent().getParent().getName());
    assertEquals("a", file.getParent().getParent().getParent().getName());
    var nioFile = tempDir.getRootPath().resolve("a\\b//c\\f");
    assertEquals(file, myFS.refreshAndFindFileByNioFile(nioFile));
  }

  @Test
  public void testCopyFile() throws IOException {
    runInEdtAndWait(() -> {
      var fromDir = tempDir.newDirectoryPath("from");
      var toDir = tempDir.newDirectoryPath("to");

      var fromVDir = myFS.refreshAndFindFileByNioFile(fromDir);
      var toVDir = myFS.refreshAndFindFileByNioFile(toDir);
      assertNotNull(fromVDir);
      assertNotNull(toVDir);
      var fileToCopy = WriteAction.compute(() -> fromVDir.createChildData(this, "temp_file"));
      byte[] byteContent = {0, 1, 2, 3};
      WriteAction.run(() -> fileToCopy.setBinaryContent(byteContent));
      var newName = "new_temp_file";
      var copy = WriteAction.compute(() -> fileToCopy.copy(this, toVDir, newName));
      assertEquals(newName, copy.getName());
      assertArrayEquals(byteContent, copy.contentsToByteArray());
    });
  }

  @Test
  public void testCopyDir() throws IOException {
    runInEdtAndWait(() -> {
      var fromDir = tempDir.newDirectoryPath("from");
      var toDir = tempDir.newDirectoryPath("to");

      var fromVDir = myFS.refreshAndFindFileByNioFile(fromDir);
      var toVDir = myFS.refreshAndFindFileByNioFile(toDir);
      assertNotNull(fromVDir);
      assertNotNull(toVDir);
      var dirToCopy = WriteAction.compute(() -> fromVDir.createChildDirectory(this, "dir"));
      var file = WriteAction.compute(() -> dirToCopy.createChildData(this, "temp_file"));
      WriteAction.run(() -> file.setBinaryContent(new byte[]{0, 1, 2, 3}));
      var newName = "dir";
      var dirCopy = WriteAction.compute(() -> dirToCopy.copy(this, toVDir, newName));
      assertEquals(newName, dirCopy.getName());
      PlatformTestUtil.assertDirectoriesEqual(toVDir, fromVDir);
    });
  }

  @Test
  public void testUnicodeName() {
    var name = IoTestUtil.getUnicodeName();
    assumeTrue(name != null);
    var childFile = tempDir.newFileNio(name + ".txt");

    var dir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(dir);

    var child = myFS.refreshAndFindFileByNioFile(childFile);
    assertNotNull(Arrays.toString(dir.getChildren()) + " : " + NioFiles.list(tempDir.getRootPath()), child);
  }

  @Test
  public void testFindRoot() {
    assertNull(myFS.findFileByPath("wrong_path"));

    if (OS.CURRENT == OS.Windows) {
      var systemDrive = System.getenv("SystemDrive");
      var root = myFS.findFileByPath(systemDrive.toLowerCase(Locale.ENGLISH));
      assertNotNull(root);
      assertEquals(systemDrive.toUpperCase(Locale.ENGLISH) + '/', root.getPath());
      var root2 = myFS.findFileByPath(systemDrive.toUpperCase(Locale.ENGLISH) + '\\');
      assertSame(String.valueOf(root), root, root2);

      var fm = VirtualFileManager.getInstance();
      root = fm.findFileByUrl("file://" + systemDrive.toUpperCase(Locale.ENGLISH) + '/');
      assertNotNull(root);
      root2 = fm.findFileByUrl("file:///" + systemDrive.toLowerCase(Locale.ENGLISH) + '/');
      assertSame(String.valueOf(root), root, root2);

      assertNull(myFS.findFileByPath("\\\\some-unc-server"));
      assertNull(myFS.findFileByPath("//SOME-UNC-SERVER"));
      assertNull(myFS.findFileByPath("\\\\wsl$"));
      assertNull(myFS.findFileByPath("\\\\?\\C:\\"));

      root = myFS.findFileByPath("\\\\some-unc-server\\some-unc-share");
      assertNotNull(root);
      root2 = myFS.findFileByPath("//SOME-UNC-SERVER/SOME-UNC-SHARE");
      assertSame(String.valueOf(root), root, root2);
      assertEquals("\\\\some-unc-server\\some-unc-share", root.getPresentableName());
      RefreshQueue.getInstance().processEvents(false, List.of(new VFileDeleteEvent(this, root)));
    }
    else {
      var root = myFS.findFileByPath("/");
      assertNotNull(root);
      assertEquals("/", root.getPath());
    }

    var root = myFS.findFileByPath("");
    assertNotNull(root);

    var jarFile = tempDir.newFileNio("test.jar");
    jarFile(__ -> Unit.INSTANCE).generate(jarFile);
    assertNotNull(myFS.refreshAndFindFileByNioFile(jarFile));
    root = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile + "!/");
    assertNotNull(root);

    var root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.toString().replace('\\', '/').replace("/", "//") + "!/");
    assertEquals(String.valueOf(root2), root, root2);

    if (!SystemInfo.isFileSystemCaseSensitive) {
      root2 = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.toString().toUpperCase(Locale.US) + "!/");
      assertEquals(String.valueOf(root2), root, root2);
    }
  }

  @Test
  public void testUncOperations() throws IOException {
    IoTestUtil.assumeWindows();
    var uncRootPath = Path.of(IoTestUtil.toLocalUncPath(tempDir.getRootPath().toString()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    var uncRootFile = myFS.refreshAndFindFileByPath(uncRootPath.toString());
    assertNotNull("not found: " + uncRootPath, uncRootFile);
    assertTrue(uncRootFile.isValid());

    try {
      assertThat(uncRootFile.getChildren()).isEmpty();

      var data = "original data".getBytes(UTF_8);
      var testLocalPath1 = Files.write(tempDir.newFileNio("test1.txt"), data);
      uncRootFile.refresh(false, false);
      var testFile1 = uncRootFile.findChild(testLocalPath1.getFileName().toString());
      assertNotNull("not found: " + testLocalPath1, testFile1);
      assertTrue("invalid: " + testFile1, testFile1.isValid());
      assertThat(uncRootFile.getChildren()).hasSize(1);

      assertThat(testFile1.contentsToByteArray(false)).isEqualTo(data);
      data = "new content".getBytes(UTF_8);
      Files.write(testLocalPath1, data);
      uncRootFile.refresh(false, false);
      assertThat(testFile1.contentsToByteArray(false)).isEqualTo(data);

      var testFile2 = WriteAction.computeAndWait(() -> uncRootFile.createChildData(this, "test2.txt"));
      var testLocalPath2 = tempDir.getRootPath().resolve(testFile2.getName());
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
    var file = tempDir.newFileNio("test.txt");
    Files.writeString(file, "hello");
    var virtualFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(virtualFile);
    var s = VfsUtilCore.loadText(virtualFile);
    assertEquals("hello", s);
    assertEquals(5, virtualFile.getLength());

    Files.writeString(file, "new content");
    ((PersistentFSImpl)PersistentFS.getInstance()).cleanPersistedContent(((VirtualFileWithId)virtualFile).getId());
    s = VfsUtilCore.loadText(virtualFile);
    assertEquals("new content", s);
    assertEquals(11, virtualFile.getLength());
  }

  @Test
  public void testHardLinks() throws IOException {
    var settings = GeneralSettings.getInstance();
    var safeWrite = settings.isUseSafeWrite();
    var requestor = new SafeWriteRequestor() {
    };
    var testData = "hello".getBytes(UTF_8);

    try {
      settings.setUseSafeWrite(false);

      var targetFile = tempDir.newFileNio("targetFile");
      var hardLinkFile =tempDir.getRootPath().resolve("hardLinkFile");
      Files.createLink(hardLinkFile, targetFile);

      var file = myFS.refreshAndFindFileByNioFile(targetFile);
      assertNotNull(file);
      WriteAction.runAndWait(() -> file.setBinaryContent(testData, 0, 0, requestor));
      assertTrue(file.getLength() > 0);

      if (OS.CURRENT == OS.Windows) {
        var bytes = Files.readAllBytes(hardLinkFile);
        assertEquals(testData.length, bytes.length);
      }

      var check = myFS.refreshAndFindFileByNioFile(hardLinkFile);
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
    IoTestUtil.assumeWindows();

    var file = Path.of("C:\\Documents and Settings\\desktop.ini");
    assumeTrue("Documents and Settings assumed to exist", Files.exists(file));

    VfsRootAccess.allowRootAccess(getTestRootDisposable(), file.getParent().toString().replace('\\', '/'));

    var virtualFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(virtualFile);

    var fs = (NewVirtualFileSystem)virtualFile.getFileSystem();
    var attributes = fs.getAttributes(virtualFile);
    assertNotNull(attributes);
    assertEquals(FileAttributes.Type.FILE, attributes.getType());
    assertTrue(attributes.isHidden());
  }

  @Test
  public void testRefreshSeesLatestDirectoryContents() throws IOException {
    var content = "";
    Files.writeString(tempDir.getRootPath().resolve("Foo.java"), content);

    var virtualDir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(virtualDir);
    virtualDir.getChildren();
    virtualDir.refresh(false, true);
    assertThat(virtualDir.getChildren()).hasSize(1);

    Files.writeString(tempDir.getRootPath().resolve("Bar.java"), content);
    virtualDir.refresh(false, true);
    assertEquals(2, virtualDir.getChildren().length);
  }

  @Test
  public void testSingleFileRootRefresh() throws IOException {
    var file = Files.createTempFile(tempDir.getRootPath(), "test.", ".txt");
    var virtualFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(virtualFile);
    assertTrue(virtualFile.exists());
    assertTrue(virtualFile.isValid());

    virtualFile.refresh(false, false);
    assertFalse(((VirtualFileSystemEntry)virtualFile).isDirty());

    Files.delete(file);
    assertThat(file).doesNotExist();
    virtualFile.refresh(false, false);
    assertFalse(virtualFile.exists());
    assertFalse(virtualFile.isValid());
  }

  @Test
  public void testBadFileNameUnderUnix() {
    IoTestUtil.assumeUnix();

    var file = tempDir.newFileNio("test\\file.txt");
    var vDir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(vDir);
    assertThat(vDir.getChildren()).isEmpty();

    ((VirtualFileSystemEntry)vDir).markDirtyRecursively();
    vDir.refresh(false, true);
    assertNull(myFS.refreshAndFindFileByNioFile(file));
  }

  @Test
  public void testNoMoreFakeRoots() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    //noinspection deprecation
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      try {
        ManagingFS.getInstance().findRoot("", myFS);
        fail("should fail by assertion in PersistentFsImpl.findRoot()");
      }
      catch (Throwable t) {
        var message = t.getMessage();
        assertTrue(message, message.startsWith("Invalid root"));
      }
    });
  }

  @Test
  public void testFindRootWithDeepNestedFileMustThrow() {
    try {
      var d = tempDir.newDirectoryPath();
      var vDir = requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(d));
      ManagingFS.getInstance().findRoot(vDir.getPath(), myFS);
      fail("should fail by assertion in PersistentFsImpl.findRoot()");
    }
    catch (Throwable t) {
      var message = t.getMessage();
      assertTrue(message, message.startsWith("Must pass FS root path, but got"));
    }
  }

  @Test
  public void testCopyToPointDir() {
    var sub = tempDir.newDirectoryPath("sub");
    var file = tempDir.newFileNio("file.txt");

    var topDir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(topDir);
    var sourceFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(sourceFile);
    var parentDir = myFS.refreshAndFindFileByNioFile(sub);
    assertNotNull(parentDir);
    assertEquals(2, topDir.getChildren().length);

    try {
      WriteAction.runAndWait(() -> sourceFile.copy(this, parentDir, "."));
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
    var home = tempDir.newDirectoryPath();
    var file = Files.createFile(home.resolve("file.txt"));
    assertThat(NioFiles.list(home).stream().map(p -> p.getFileName().toString())).containsExactly("file.txt");

    var vFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(vFile);
    WriteAction.runAndWait(() -> vFile.rename(LocalFileSystemTest.class, "FILE.txt"));

    assertEquals("FILE.txt", vFile.getName());
    assertThat(NioFiles.list(home).stream().map(p -> p.getFileName().toString())).containsExactly("FILE.txt");
  }

  @Test
  public void testFileCaseChange() throws IOException {
    IoTestUtil.assumeCaseInsensitiveFS();

    var file = tempDir.newFileNio("file.txt");

    var topDir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(topDir);
    var sourceFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(sourceFile);

    var newName = StringUtil.capitalize(file.getFileName().toString());
    NioFiles.rename(file, newName);
    topDir.refresh(false, true);
    assertFalse(((VirtualDirectoryImpl)topDir).allChildrenLoaded());
    assertTrue(sourceFile.isValid());
    assertEquals(newName, sourceFile.getName());

    topDir.getChildren();
    newName = newName.toLowerCase(Locale.ENGLISH);
    NioFiles.rename(file, newName);
    topDir.refresh(false, true);
    assertTrue(((VirtualDirectoryImpl)topDir).allChildrenLoaded());
    assertTrue(sourceFile.isValid());
    assertEquals(newName, sourceFile.getName());
  }

  @Test
  public void testPartialRefresh() throws IOException {
    doTestPartialRefresh(tempDir.newDirectoryPath("top"));
  }

  @Test
  public void testSymlinkTargetBlink() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    var target = tempDir.newDirectoryPath("target");
    var link = Files.createSymbolicLink(tempDir.getRootPath().resolve("link"), target);

    var vTop = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
    assertNotNull(vTop);
    assertTrue(vTop.isValid());
    var vTarget = myFS.refreshAndFindFileByNioFile(target);
    assertNotNull(vTarget);
    assertTrue(vTarget.isValid());
    var vLink = myFS.refreshAndFindFileByNioFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertTrue(vLink.isDirectory());

    Files.delete(target);
    vTop.refresh(false, true);
    assertFalse(vTarget.isValid());
    assertFalse(vLink.isValid());
    vLink = myFS.refreshAndFindFileByNioFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertFalse(vLink.isDirectory());

    Files.createDirectory(target);
    vTop.refresh(false, true);
    assertFalse(vLink.isValid());
    vLink = myFS.refreshAndFindFileByNioFile(link);
    assertNotNull(vLink);
    assertTrue(vLink.isValid());
    assertTrue(vLink.isDirectory());
  }

  @Test
  public void testInterruptedRefresh() throws IOException {
    doTestInterruptedRefresh(tempDir.newDirectoryPath("top"));
  }

  @Test
  public void testInvalidFileName() {
    runInEdtAndWait(() -> {
      var dir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
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
      var dir = myFS.refreshAndFindFileByNioFile(tempDir.getRootPath());
      assertNotNull(dir);

      var file1 = WriteAction.compute(() -> dir.createChildData(this, "a.txt"));
      Files.delete(file1.toNioPath());

      var file2 = WriteAction.compute(() -> dir.createChildData(this, "b.txt"));
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
    IoTestUtil.assumeSymLinkCreationIsSupported();

    runInEdtAndWait(() -> {
      var srcDir = tempDir.newDirectoryPath("src");
      var link = Files.createSymbolicLink(tempDir.getRootPath().resolve("link"), tempDir.getRootPath().resolve("missing"));
      var dstDir = tempDir.newDirectoryPath("dst");

      var file = myFS.refreshAndFindFileByNioFile(link);
      assertNotNull(file);

      var target = myFS.refreshAndFindFileByNioFile(dstDir);
      assertNotNull(target);

      WriteAction.run(() -> myFS.moveFile(this, file, target));

      assertThat(srcDir).isEmptyDirectory();
      assertThat(NioFiles.list(dstDir).stream().map(p -> p.getFileName())).containsExactly(link.getFileName());
    });
  }

  @Test
  public void testFileContentChangeEvents() throws IOException {
    var file = tempDir.newFileNio("file.txt");
    var stamp = Files.getLastModifiedTime(file);
    var vFile = myFS.refreshAndFindFileByNioFile(file);
    assertNotNull(vFile);

    var updated = new int[]{0};
    var connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (var event : events) {
          if (event instanceof VFileContentChangeEvent && vFile.equals(event.getFile())) {
            updated[0]++;
            break;
          }
        }
      }
    });

    Files.writeString(file, "content");
    Files.setLastModifiedTime(file, stamp);
    vFile.refresh(false, false);
    assertEquals(1, updated[0]);

    Files.writeString(file, "more content");
    Files.setLastModifiedTime(file, stamp);
    vFile.refresh(false, false);
    assertEquals(2, updated[0]);
  }

  @Test
  public void testReadOnly() throws IOException {
    runInEdtAndWait(() -> {
      var file = tempDir.newFileNio("file.txt");
      var vFile = myFS.refreshAndFindFileByNioFile(file);
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

  @Test
  public void testNioPathIsImplementedForDir() {
    var newDir = tempDir.newDirectoryPath("someDir-32");
    var newDirFile = myFS.refreshAndFindFileByPath(newDir.toString());
    assertNotNull(newDirFile);
    assertThat(newDirFile.toNioPath()).isNotNull().isEqualTo(newDir);
  }

  @Test
  public void testNioPathIsImplementedForFile() {
    var newDir = tempDir.newFileNio("someFile-32");
    var newDirFile = myFS.refreshAndFindFileByPath(newDir.toString());
    assertNotNull(newDirFile);
    assertThat(newDirFile.toNioPath()).isNotNull().isEqualTo(newDir);
  }

  @Test
  public void testFindFileByUrlPerformance() {
    var virtualFileManager = VirtualFileManager.getInstance();
    Benchmark.newBenchmark("findFileByUrl", () -> {
      for (var i = 0; i < 10_000_000; i++) {
        assertNull(virtualFileManager.findFileByUrl("temp://"));
      }
    }).start();
  }

  @Test
  public void testFindFileByNioPath() {
    var file = tempDir.newFileNio("some-new-file");

    var nioFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file);
    var nioFile2 = VirtualFileManager.getInstance().findFileByNioPath(file);

    assertNotNull(nioFile);
    assertNotNull(nioFile2);
    assertEquals(nioFile, nioFile2);
  }

  @Test
  public void testFindNioPathConsistency() {
    var file = tempDir.newFileNio("some-new-file");

    var ioFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    var nioFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file);

    assertNotNull(ioFile);
    assertNotNull(nioFile);
    assertEquals(nioFile, ioFile);

    assertEquals(file, nioFile.toNioPath());
    assertEquals(file, ioFile.toNioPath());
  }

  @Test
  public void caseSensitivityNativeAPIMustWorkInSimpleCasesAndIsCachedInVirtualFileFlags() {
    var file = tempDir.newFileNio("dir/0");
    assertFalse(FileSystemUtil.isCaseToggleable(file.getFileName().toString()));

    var dir = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.getParent());
    assertNotNull(dir);
    assertEquals(CaseSensitivity.UNKNOWN, dir.getChildrenCaseSensitivity());

    ((PersistentFSImpl)PersistentFS.getInstance()).determineCaseSensitivityAndPrepareUpdate(dir, file.getFileName().toString());
    var expected = CaseSensitivity.fromBoolean(SystemInfo.isFileSystemCaseSensitive);
    assertEquals(expected, dir.getChildrenCaseSensitivity());
    assertEquals(expected == CaseSensitivity.SENSITIVE, dir.isCaseSensitive());
  }

  @Test(timeout = 30_000)
  public void specialFileDoesNotCauseHangs() {
    IoTestUtil.assumeUnix();

    var fifo = tempDir.getRootPath().resolve("test.fifo");
    IoTestUtil.createFifo(fifo.toString());
    var file = requireNonNull(myFS.refreshAndFindFileByNioFile(fifo));
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
    IoTestUtil.assumeSymLinkCreationIsSupported();

    var dir = tempDir.newFileNio("dir/1").getParent();
    var dirLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("dirLink"), dir);
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(dirLink), "1");

    var file = tempDir.newFileNio("file");
    var fileLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("fileLink"), file);
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(fileLink));

    var missingLink = Files.createSymbolicLink(tempDir.getRootPath().resolve("missingLink"), Path.of("missing"));
    assertDirectoryListing(myFS.refreshAndFindFileByNioFile(missingLink));
  }

  @Test
  public void testFileContentWithAlmostTooLargeLength() throws IOException {
    var expectedContent = new byte[FileSizeLimit.getDefaultContentLoadLimit()];
    Arrays.fill(expectedContent, (byte)'a');
    var file = tempDir.newFileNio("test.txt");
    Files.write(file, expectedContent);

    var actualContent = myFS.refreshAndFindFileByNioFile(file).contentsToByteArray();
    assertArrayEquals(expectedContent, actualContent);
  }

  @Test
  public void canonicallyCasedHardLink() throws IOException {
    assumeTrue("Requires JRE 21+", CurrentJavaVersion.currentJavaVersion().isAtLeast(21));
    var original = tempDir.newFileNio("original");
    var hardLink = Files.createLink(original.resolveSibling("hardLink"), original);
    assertThat(myFS.refreshAndFindFileByNioFile(hardLink).getName()).isEqualTo(hardLink.getFileName().toString());
    assertThat(myFS.refreshAndFindFileByNioFile(original).getName()).isEqualTo(original.getFileName().toString());
  }

  @Test
  public void canonicallyCasedDecomposedName() {
    assumeTrue("Requires JRE 21+", CurrentJavaVersion.currentJavaVersion().isAtLeast(21));
    @SuppressWarnings({"SpellCheckingInspection", "NonAsciiCharacters"}) var name = "schön";
    var nfdName = Normalizer.normalize(name, Normalizer.Form.NFD);
    var nfcName = Normalizer.normalize(name, Normalizer.Form.NFC);
    var nfdFile = tempDir.newFileNio(nfdName);
    var nfcFile = nfdFile.resolveSibling(nfcName);
    assumeTrue("Filesystem does not support normalization", Files.exists(nfcFile));
    assertThat(myFS.refreshAndFindFileByNioFile(nfcFile).getName()).isEqualTo(nfdName);
  }

  @Test
  public void listWithAttributes_ReturnsSameDataAs_GetAttributes() {
    tempDir.newVirtualFile("a/a", "test".getBytes(UTF_8));
    tempDir.newVirtualFile("a/b", "test_1".getBytes(UTF_8));
    tempDir.newVirtualFile("a/c", "test_12".getBytes(UTF_8));

    tempDir.newVirtualFile("b/a/a", "123".getBytes(UTF_8));
    tempDir.newVirtualFile("b/b/b", "12".getBytes(UTF_8));
    tempDir.newVirtualFile("b/c/c", "1".getBytes(UTF_8));

    tempDir.newVirtualFile("c/a/a");
    tempDir.newVirtualFile("c/b/b");
    tempDir.newVirtualFile("c/c/c", "0".getBytes(UTF_8));

    var root = tempDir.getVirtualFileRoot();
    root.refresh(false, true);
    checkAttributesAreEqual(root, (LocalFileSystemImpl)myFS);
  }

  private static void checkAttributesAreEqual(VirtualFile dir, LocalFileSystemImpl lfs) {
    var childrenWithAttributes = lfs.listWithAttributes(dir, null);
    var childrenNames = lfs.list(dir);

    assertThat(childrenWithAttributes.keySet())
      .containsExactlyInAnyOrder(childrenNames);

    for (var childName : childrenNames) {
      var child = dir.findChild(childName);
      var attributes = lfs.getAttributes(child);

      assertThat(attributes)
        .describedAs(child.getPresentableUrl())
        .isEqualTo(childrenWithAttributes.get(childName));

      assertThat(attributes)
        .describedAs(child.getPresentableUrl())
        .isEqualTo(lfs.listWithAttributes(dir, Set.of(childName)).get(childName));

      if (child.isDirectory()) {
        checkAttributesAreEqual(child, lfs);
      }
    }
  }

  private void assertDirectoryListing(VirtualFile vFile, String... expected) {
    var list1 = myFS.list(vFile);
    var list2 = ArrayUtil.toStringArray(((LocalFileSystemImpl)myFS).listWithAttributes(vFile).keySet());
    assertThat(list1).
      containsExactlyInAnyOrder(expected).
      containsExactlyInAnyOrder(list2);
  }

  private static void assertWritable(Path file, VirtualFile vFile, boolean expected) {
    assertEquals(expected, Files.isWritable(file));
    assertEquals(expected, vFile.isWritable());
  }

  public static void doTestPartialRefresh(@NotNull Path top) throws IOException {
    var sub = Files.createDirectories(top.resolve("sub"));
    var file1 = Files.writeString(top.resolve("file1.txt"), ".");
    var file2 = Files.writeString(sub.resolve("file2.txt"), ".");

    var lfs = LocalFileSystem.getInstance();
    var topDir = lfs.refreshAndFindFileByNioFile(top);
    assertNotNull(topDir);
    var subDir = lfs.refreshAndFindFileByNioFile(sub);
    assertNotNull(subDir);
    var vFile1 = lfs.refreshAndFindFileByNioFile(file1);
    assertNotNull(vFile1);
    var vFile2 = lfs.refreshAndFindFileByNioFile(file2);
    assertNotNull(vFile2);
    topDir.refresh(false, true);

    var processed = new HashSet<VirtualFile>();
    var connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      Files.writeString(file1, "++");
      Files.writeString(file2, "++");
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

  public static void doTestRefreshEquality(@NotNull Path tempDir) throws IOException {
    var lfs = LocalFileSystem.getInstance();
    var tempVDir = lfs.refreshAndFindFileByNioFile(tempDir);
    assertNotNull(tempVDir);
    assertEquals(0, tempVDir.getChildren().length);

    Files.writeString(tempDir.resolve("file1.txt"), "hello");
    tempVDir.refresh(false, false);
    assertEquals(1, tempVDir.getChildren().length);
    Files.writeString(tempDir.resolve("file2.txt"), "hello");
    tempVDir.refresh(false, true);
    assertEquals(2, tempVDir.getChildren().length);

    var tempDir1 = Files.createDirectories(tempDir.resolve("sub1"));
    var tempVDir1 = lfs.refreshAndFindFileByNioFile(tempDir1);
    assertNotNull(tempVDir1);
    Files.writeString(tempDir1.resolve("file.txt"), "hello");
    tempVDir1.refresh(false, false);
    assertEquals(1, tempVDir1.getChildren().length);

    var tempDir2 = Files.createDirectories(tempDir.resolve("sub2"));
    var tempVDir2 = lfs.refreshAndFindFileByNioFile(tempDir2);
    assertNotNull(tempVDir2);
    Files.writeString(tempDir2.resolve("file.txt"), "hello");
    tempVDir2.refresh(false, true);
    assertEquals(1, tempVDir2.getChildren().length);
  }

  public static void doTestRefreshAndFindFile(@NotNull Path tempDir) throws IOException {
    var lfs = LocalFileSystem.getInstance();
    var tempVDir = lfs.refreshAndFindFileByNioFile(tempDir);
    assertNotNull(tempVDir);

    var file1 = Files.createDirectories(tempDir.resolve("some/nested/dir")).resolve("hello.txt");
    Files.writeString((file1), "hello");
    assertNotNull(lfs.refreshAndFindFileByNioFile(file1));

    var file2 = Files.createDirectories(tempDir.resolve("another/nested/dir")).resolve("hello.txt");
    Files.writeString(file2, "hello again");
    assertNotNull(lfs.refreshAndFindFileByNioFile(file2));

    tempVDir.getChildren();
    tempVDir.refresh(false, true);
    var file3 = Files.createDirectories(tempDir.resolve("one/more/nested/dir")).resolve("hello.txt");
    Files.writeString(file3, "hello again");
    assertNotNull(lfs.refreshAndFindFileByNioFile(file3));
  }

  public static void doTestInterruptedRefresh(@NotNull Path top) throws IOException {
    for (var i = 1; i <= 3; i++) {
      var sub = Files.createDirectories(top.resolve("sub_" + i));
      for (var j = 1; j <= 3; j++) {
        Files.createDirectories(sub.resolve("sub_" + j));
      }
    }
    Files.walkFileTree(top, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        for (int k = 1; k <= 3; k++) {
          var name = "file_" + k;
          IoTestUtil.unchecked(() -> Files.writeString(dir.resolve(name), "."));
        }
        return FileVisitResult.CONTINUE;
      }
    });

    var topDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(top);
    assertNotNull(topDir);
    var files = new HashSet<VirtualFile>();
    VfsUtilCore.processFilesRecursively(topDir, file -> {
      if (!file.isDirectory()) files.add(file);
      return true;
    });
    assertThat(files).hasSize(39);  // 13 dirs of 3 files
    topDir.refresh(false, true);

    var processed = new HashSet<VirtualFile>();
    var connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        events.forEach(e -> processed.add(e.getFile()));
      }
    });

    try {
      files.forEach(f -> IoTestUtil.unchecked(() -> Files.writeString(f.toNioPath(), "+++")));
      ((NewVirtualFile)topDir).markDirtyRecursively();

      var session = RefreshQueue.getInstance().createSession(false, true, null);
      var stopAt = top.getFileName() + "/sub_2/sub_2";
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
}
