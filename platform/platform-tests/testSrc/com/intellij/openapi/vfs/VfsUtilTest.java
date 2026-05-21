// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.io.Compressor;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.system.OS;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWslPresence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class VfsUtilTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testFixIdeaUrl() {
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file://C:/Temp/README.txt"));
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file:///C:/Temp/README.txt"));
    assertEquals("file:/tmp/foo.bar", VfsUtilCore.fixIDEAUrl("file:///tmp/foo.bar"));
  }

  @Test
  public void testFindFileByUrl() throws IOException, URISyntaxException {
    var testDataDir = Path.of(PathManagerEx.getTestDataPath(), "vfs/findFileByUrl");

    var vDir = VfsUtil.findFileByURL(testDataDir.toUri().toURL());
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());
    var list = VfsUtil.getChildren(vDir, file -> !file.getName().endsWith(".new"));
    assertEquals(2, list.size());

    var zip = testDataDir.resolve("test.zip");
    var jarUrl = new URI("jar", zip.toUri() + "!/", null).toURL();
    var vZipRoot = VfsUtil.findFileByURL(jarUrl);
    assertNotNull(vZipRoot);
    assertTrue(vZipRoot.isDirectory());

    var vZipDir = VfsUtil.findFileByURL(new URI("jar", zip.toUri() + "!/com/intellij/installer", null).toURL());
    assertNotNull(vZipDir);
    assertTrue(vZipDir.isDirectory());

    var file = testDataDir.resolve("1.txt");
    var vFile = VfsUtil.findFileByURL(file.toUri().toURL());
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());
    assertEquals("test text", VfsUtilCore.loadText(vFile));
  }

  @Test
  public void testFindRelativeFile() throws IOException {
    var testDataDir = Path.of(PathManagerEx.getTestDataPath(), "vfs");
    var vDir = LocalFileSystem.getInstance().findFileByNioFile(testDataDir);
    assertNotNull(vDir);
    assertEquals(vDir, VfsUtilCore.findRelativeFile(VfsUtilCore.convertFromUrl(testDataDir.toUri().toURL()), null));
    assertEquals(vDir, VfsUtilCore.findRelativeFile(testDataDir.toAbsolutePath().toString(), null));
    assertEquals(vDir, VfsUtilCore.findRelativeFile("vfs", vDir.getParent()));
  }

  @Test
  public void testFindChildWithTrailingSpace() {
    var tempDir = this.tempDir.newDirectoryPath();
    var vDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    var child = vDir.findChild(" ");
    assertNull(child);

    assertThat(vDir.getChildren()).isEmpty();
  }

  @Test
  public void testDirAttributeRefreshes() throws IOException {
    var file = tempDir.newFileNio("test");
    var vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());

    Files.delete(file);
    Files.createDirectory(file);
    var vFile2 = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(vFile2);
    assertTrue(vFile2.isDirectory());

    Files.delete(file);
    Files.createFile(file);
    var vFile3 = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(vFile3);
    assertFalse(vFile3.isDirectory());
  }

  @Test
  public void testPresentableUrlSurvivesDeletion() throws IOException {
    var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.newFileNio("file.txt"));
    assertNotNull(file);
    var url = file.getPresentableUrl();
    assertNotNull(url);
    WriteAction.runAndWait(() -> file.delete(this));
    assertEquals(url, file.getPresentableUrl());
  }

  @Test
  public void testToUri() {
    var uri = VfsUtil.toUri("file:///asd");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/asd", uri.getPath());

    uri = VfsUtil.toUri("file:///asd/ ads/ad#test");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/asd/ ads/ad", uri.getPath());
    assertEquals("test", uri.getFragment());

    uri = VfsUtil.toUri("file:///asd/ ads/ad#");
    assertNotNull(uri);
    assertEquals("file:///asd/%20ads/ad#", uri.toString());

    uri = VfsUtil.toUri("mailto:someone@example.com");
    assertNotNull(uri);
    assertEquals("someone@example.com", uri.getSchemeSpecificPart());

    if (OS.CURRENT == OS.Windows) {
      uri = VfsUtil.toUri("file://C:/p");
      assertNotNull(uri);
      assertEquals("file", uri.getScheme());
      assertNull(uri.getHost());
      assertEquals("/C:/p", uri.getPath());

      uri = VfsUtil.toUri("FILE://C:/p");
      assertNotNull(uri);
      assertEquals("FILE", uri.getScheme());
      assertNull(uri.getHost());
      assertEquals("/C:/p", uri.getPath());

      uri = VfsUtil.toUri("file://host/path");
      assertNotNull(uri);
      assertEquals("file", uri.getScheme());
      assertEquals("host", uri.getHost());
      assertEquals("/path", uri.getPath());

      uri = VfsUtil.toUri("FILE://host/path");
      assertNotNull(uri);
      assertEquals("FILE", uri.getScheme());
      assertEquals("host", uri.getHost());
      assertEquals("/path", uri.getPath());
    }

    uri = VfsUtil.toUri("file:///Users/S pace");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/Users/S pace", uri.getPath());
    assertEquals("/Users/S%20pace", uri.getRawPath());
    assertEquals("file:///Users/S%20pace", uri.toString());

    uri = VfsUtil.toUri("http://developer.android.com/guide/developing/tools/avd.html");
    assertNotNull(uri);
    assertEquals("http", uri.getScheme());
    assertEquals("/guide/developing/tools/avd.html", uri.getRawPath());
    assertEquals("http://developer.android.com/guide/developing/tools/avd.html", uri.toString());

    uri = VfsUtil.toUri("http://developer.android.com/guide/developing/tools/avd.html?f=23r2ewd");
    assertNotNull(uri);
    assertEquals("http", uri.getScheme());
    assertEquals("/guide/developing/tools/avd.html", uri.getRawPath());
    assertEquals("http://developer.android.com/guide/developing/tools/avd.html?f=23r2ewd", uri.toString());
    assertEquals("f=23r2ewd", uri.getQuery());
  }

  @Test
  public void testIsAncestor() {
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir/file.txt"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir2", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir2"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir2"));
  }

  @Test
  public void testFindRootWithDenormalizedPath() throws IOException {
    var tempJar = tempDir.newFileNio("test.jar");
    try (var jar = new Compressor.Jar(tempJar)) { jar.addManifest(new Manifest()); }
    var jar = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempJar);
    assertNotNull(jar);

    var root1 = ManagingFS.getInstance().findRoot(jar.getPath() + "!/", JarFileSystem.getInstance());
    var root2 = ManagingFS.getInstance().findRoot(jar.getParent().getPath() + "//" + jar.getName() + "!/", JarFileSystem.getInstance());
    assertNotNull(root1);
    assertSame(root1, root2);

    assertNull(LocalFileSystem.getInstance().findFileByPath("//../blah-blah")); // it must not crash in FsRoot("//..")
    assertNull(LocalFileSystem.getInstance().findFileByPath("//./blah-blah")); // it must not crash in FsRoot("//.")
  }

  @Test
  public void testFindRootWithCrazySlashes() {
    for (var i = 0; i < 10; i++) {
      var path = StringUtil.repeat("/", i);
      var root = LocalFileSystem.getInstance().findFileByPathIfCached(path);
      assertTrue(path, root == null || !root.getPath().contains("//"));
      var root2 = LocalFileSystem.getInstance().findFileByPath(path);
      assertTrue(path, root2 == null || !root2.getPath().contains("//"));
    }
  }

  @Test
  public void testNotCanonicallyNamedChild() throws IOException {
    var tempDir = this.tempDir.newDirectoryPath();
    Files.createFile(tempDir.resolve("libFiles"));
    Files.createFile(tempDir.resolve("CssInvalidElement"));
    Files.createFile(tempDir.resolve("extFiles"));

    var vDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    assertNotNull(vDir.findChild("libFiles"));
    assertNotNull(vDir.findChild("CssInvalidElement"));

    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("extFiles"));
    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("/extFiles/"));
    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("extFiles/"));
    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("/extFiles"));
    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("//extFiles"));
    assertNotNull(Arrays.toString(vDir.getChildren()), vDir.findChild("extFiles///"));

    assertNull(vDir.findChild("/xxx/extFiles/"));
    assertNull(vDir.findChild("xxx/extFiles/"));
    assertNull(vDir.findChild("/xxx/extFiles"));
    assertNull(vDir.findChild("xxx/extFiles"));
    assertNull(vDir.findChild("xxx//extFiles"));
  }

  @Test(timeout = 30_000)
  public void testRenameDuringFullRefreshPerformance() throws IOException { doRenameAndRefreshTest(true); }

  @Test(timeout = 120_000)
  public void testRenameDuringPartialRefreshPerformance() throws IOException { doRenameAndRefreshTest(false); }

  private void doRenameAndRefreshTest(boolean full) throws IOException {
    ThreadingAssertions.assertBackgroundThread();

    var testFile = tempDir.newFileNio("test/child.txt");
    var parent = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(testFile.getParent());
    assertNotNull(parent);
    if (full) {
      assertEquals(1, parent.getChildren().length);
    }
    var child = parent.findChild(testFile.getFileName().toString());
    assertNotNull(child);

    var files = List.of(parent);
    var semaphore = new Semaphore();
    for (var i = 0; i < 1000; i++) {
      semaphore.down();
      VfsUtil.markDirty(true, false, parent);
      LocalFileSystem.getInstance().refreshFiles(files, true, true, semaphore::up);

      assertTrue(child.isValid());
      var newName = "name" + i + ".txt";
      WriteAction.runAndWait(() -> child.rename(this, newName));
      assertTrue(child.isValid());

      TimeoutUtil.sleep(1);  // needed to prevent frequent event detector from triggering
    }

    semaphore.waitFor();
  }

  @Test(timeout = 20_000)
  public void testScanNewChildrenMustNotBeRunOutsideOfProjectRoots() throws Exception {
    checkNewDirAndRefresh(_-> {}, getAllExcludedCalled -> assertFalse(getAllExcludedCalled.get()));
  }

  @Test(timeout = 20_000)
  public void testRefreshAndEspeciallyScanChildrenMustBeRunOutsideReadActionToAvoidUILags() throws Exception {
    var project = new AtomicReference<Project>();
    checkNewDirAndRefresh(
      temp -> {
        var p = ProjectUtil.openOrImport(temp);
        Disposer.register(getTestRootDisposable(), () -> PlatformTestUtil.forceCloseProjectWithoutSaving(p));
        project.set(p);
        assertTrue(p.isOpen());
      },
      getAllExcludedCalled -> {
        try {
          assertTrue(getAllExcludedCalled.get());
        }
        finally {
          // this concoction is to ensure close() is called on the mock ProjectManagerImpl
          assertTrue(project.get().isOpen());
        }
      }
    );
  }

  private void checkNewDirAndRefresh(Consumer<? super Path> dirCreatedCallback, Consumer<? super AtomicBoolean> getAllExcludedCalledChecker) throws IOException {
    var getAllExcludedCalled = new AtomicBoolean();
    ((ProjectManagerImpl)ProjectManager.getInstance()).testOnlyGetExcludedUrlsCallback(getTestRootDisposable(), () -> {
      getAllExcludedCalled.set(true);
      assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
    });

    final var temp = tempDir.newDirectoryPath();
    var vTemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByNioFile(temp);
    assertNotNull(vTemp);
    vTemp.getChildren(); //to force full dir refresh?!
    dirCreatedCallback.accept(temp);
    var d = Files.createDirectory(temp.resolve("d"));
    var d1 = Files.createDirectory(d.resolve("d1"));
    Files.createFile(d1.resolve("x.txt"));

    ThreadingAssertions.assertBackgroundThread();
    VfsUtil.markDirty(true, false, vTemp);
    var refreshed = new CountDownLatch(1);
    LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(vTemp), false, true, refreshed::countDown);

    while (refreshed.getCount() != 0) {
      UIUtil.pump();
    }
    getAllExcludedCalledChecker.accept(getAllExcludedCalled);
  }

  @Test
  public void asyncRefreshInModalProgressCompletesWithinIt() {
    EdtTestUtil.runInEdtAndWait(() -> {
      var vTemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.getRootPath());
      assertThat(vTemp.getChildren()).isEmpty();

      tempDir.newFileNio("x.txt");

      //noinspection UsagesOfObsoleteApi
      ProgressManager.getInstance().run(new Task.Modal(null, "", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ThreadingAssertions.assertBackgroundThread();

          var semaphore = new Semaphore(1);
          vTemp.refresh(true, true, semaphore::up);
          assertTrue(semaphore.waitFor(10_000));
          assertThat(vTemp.getChildren()).hasSize(1);
        }
      });
    });
  }

  @Test(timeout = 20_000)
  public void olderRefreshWithLessSpecificModalityDoesNotBlockNewerRefresh_NoWaiting() throws IOException {
    checkNonModalThenModalRefresh(false);
  }

  @Test(timeout = 20_000)
  public void olderRefreshWithLessSpecificModalityDoesNotBlockNewerRefresh_WithWaiting() throws IOException {
    checkNonModalThenModalRefresh(true);
  }

  private void checkNonModalThenModalRefresh(boolean waitForDiskRefreshCompletionBeforeStartingModality) throws IOException {
    EdtTestUtil.runInEdtAndWait(() -> {
      var dir1 = tempDir.newDirectoryPath("dir1");
      var dir2 = tempDir.newDirectoryPath("dir2");
      var vDir = VfsUtil.findFile(tempDir.getRootPath(), true);
      assertThat(Stream.of(vDir.getChildren()).map(VirtualFile::getName))
        .containsExactly(NioFiles.getFileName(dir1), NioFiles.getFileName(dir2));
      var vDir1 = vDir.getChildren()[0];
      var vDir2 = vDir.getChildren()[1];
      assertThat(vDir1.getChildren()).isEmpty();
      assertThat(vDir2.getChildren()).isEmpty();

      Files.createFile(dir1.resolve("a.txt"));
      Files.createFile(dir2.resolve("a.txt"));

      List<String> log = new ArrayList<>();
      var semaphore = new Semaphore(1);

      var nonModalSession = RefreshQueue.getInstance().createSession(true, true, () -> {
        log.add("non-modal finished");
        semaphore.up();
      });
      nonModalSession.addFile(vDir1);
      nonModalSession.launch();

      if (waitForDiskRefreshCompletionBeforeStartingModality) {
        TimeoutUtil.sleep(100);  // hopefully that's enough for refresh thread to see the disk changes
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }

      //noinspection UsagesOfObsoleteApi
      ProgressManager.getInstance().run(new Task.Modal(null, "", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ThreadingAssertions.assertBackgroundThread();

          var local = new Semaphore(1);
          vDir2.refresh(true, true, () -> {
            log.add("modal finished");
            local.up();
          });
          assertTrue(local.waitFor(10_000));
          assertThat(vDir2.getChildren()).hasSize(1);
        }
      });

      for (var i = 0; i < 10_000 && !semaphore.waitFor(1); i++) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      assertTrue(semaphore.waitFor(1));

      assertThat(vDir1.getChildren()).hasSize(1);
      assertThat(vDir2.getChildren()).hasSize(1);

      //todo order should be the same
      if (waitForDiskRefreshCompletionBeforeStartingModality) {
        assertThat(log).containsExactlyInAnyOrder("modal finished", "non-modal finished");
      }
      else {
        assertThat(log).containsExactly("modal finished", "non-modal finished");
      }
    }, false);
  }

  @Test
  public void testVfsUtilCopyMustCopyBOMCorrectly() throws IOException {
    var file = tempDir.newFileNio("test.txt");
    var vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());
    WriteAction.runAndWait(() -> vFile.setBinaryContent(CharsetToolkit.UTF8_BOM));
    assertEquals("", VfsUtilCore.loadText(vFile));
    assertArrayEquals(CharsetToolkit.UTF8_BOM, vFile.getBOM());

    var dir = WriteAction.computeAndWait(() -> vFile.getParent().createChildDirectory(this, "dir"));
    var copy = WriteAction.computeAndWait(() -> VfsUtil.copy(this, vFile, dir));
    assertEquals("", VfsUtilCore.loadText(copy));
    assertArrayEquals(CharsetToolkit.UTF8_BOM, copy.getBOM());
  }

  @Test
  public void testVfsUtilCopyMustCopyBOMCorrectlyForFileUnderProjectRoot() throws IOException {
    var dir1 = tempDir.newDirectoryPath("dir1");
    var project = PlatformTestUtil.loadAndOpenProject(dir1, getTestRootDisposable());
    WriteAction.runAndWait(() -> {
      var root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir1);
      var module1 = PsiTestUtil.addModule(project, ModuleType.EMPTY, "module1", root);
      ModuleRootModificationUtil.addContentRoot(module1, root);
      var f = Files.write(dir1.resolve("file.txt"), CharsetToolkit.UTF8_BOM);
      var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f);
      assertTrue(ProjectFileIndex.getInstance(project).isInContent(file));
      assertEquals("", VfsUtilCore.loadText(file));
      assertEquals("", VfsUtilCore.loadText(file));
      assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());
      var dir2 = root.createChildDirectory(this, "dir2");
      var copy = VfsUtil.copy(this, file, dir2);
      assertEquals("", VfsUtilCore.loadText(copy));
      assertEquals("", VfsUtilCore.loadText(copy));
      assertArrayEquals(CharsetToolkit.UTF8_BOM, copy.getBOM());
    });
  }

  @Test
  public void testVfsUtilCopyMustCopyBOMLessFileCorrectlyWhenEncodingProjectManagerBOMForNewFilesOptionIsSetToTrue() throws IOException {
    var dir1 = tempDir.newDirectoryPath("dir1");
    var project = PlatformTestUtil.loadAndOpenProject(dir1, getTestRootDisposable());
    WriteAction.runAndWait(() -> {
      ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project))
        .setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS);
      var root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir1);
      var module1 = PsiTestUtil.addModule(project, ModuleType.EMPTY, "module1", root);
      ModuleRootModificationUtil.addContentRoot(module1, root);
      var f = Files.writeString(dir1.resolve("file.txt"), "xxx");
      var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f);
      assertTrue(ProjectFileIndex.getInstance(project).isInContent(file));
      assertEquals("xxx", VfsUtilCore.loadText(file));
      assertEquals("xxx", VfsUtilCore.loadText(file));
      assertArrayEquals(null, file.getBOM());
      var dir2 = root.createChildDirectory(this, "dir2");
      var copy = VfsUtil.copy(this, file, dir2);
      assertEquals("xxx", VfsUtilCore.loadText(copy));
      assertEquals("xxx", VfsUtilCore.loadText(copy));
      assertArrayEquals(null, copy.getBOM());
    });
  }

  @Test
  public void refreshAndFindFileMustUpdateParentDirectoryCaseSensitivityToReturnCorrectFile() throws IOException {
    assumeWindows();
    assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    var dir = tempDir.getRootPath().resolve("dir");
    var file = dir.resolve("child.txt");
    assertFalse(Files.exists(file));
    assertEquals(tempDir.getRootPath().toString(), FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(tempDir.getRootPath()));

    Files.createDirectories(dir);

    IoTestUtil.setCaseSensitivity(dir, true);
    Files.createFile(file);
    var file2 = Files.createFile(dir.resolve("CHILD.TXT"));
    var vFile2 = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file2);
    assertNotNull(vFile2);
    assertEquals("CHILD.TXT", vFile2.getName());
    assertTrue(vFile2.isCaseSensitive());
    assertTrue(vFile2.getParent().isCaseSensitive());
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, ((VirtualDirectoryImpl)vFile2.getParent()).getChildrenCaseSensitivity());
  }

  @Test
  public void pathEqualsWorksForLegacyWslPaths() {
    var wslName = IoTestUtil.assumeWorkingWslDistribution();
    var usrBinFile = Path.of("\\\\wsl$\\" + wslName + "\\usr\\bin\\");
    assertThat(usrBinFile).exists();
    var usrBin = LocalFileSystem.getInstance().findFileByNioFile(usrBinFile);
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "\\\\wsl$\\" + wslName + "\\usr\\bin\\"));
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "//wsl$/" + wslName + "/usr/bin"));
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "//wsl$/" + wslName + "/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin, "//xxx$/" + wslName + "/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin, "//wsl$/xxx/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin.getParent(), "//wsl$/xxx/usr"));
  }

  @Test
  public void pathEqualsWorksForWslPaths() {
    var wslName = IoTestUtil.assumeWorkingWslDistribution();
    var usrBinFile = Path.of("\\\\wsl.localhost\\" + wslName + "\\usr\\bin\\");
    assertThat(usrBinFile).exists();
    var usrBin = LocalFileSystem.getInstance().findFileByNioFile(usrBinFile);
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "\\\\wsl.localhost\\" + wslName + "\\usr\\bin\\"));
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "//wsl.localhost/" + wslName + "/usr/bin"));
    assertTrue(VfsUtilCore.pathEqualsTo(usrBin, "//wsl.localhost/" + wslName + "/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin, "//xxx.localhost/" + wslName + "/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin, "//wsl.localhost/xxx/usr/bin/"));
    assertFalse(VfsUtilCore.pathEqualsTo(usrBin.getParent(), "//wsl.localhost/xxx/usr"));
  }

  @Test
  public void testGetPathForVFileCreateEventForJarReturnsNormalizedPathSeparators() throws IOException {
    var jarFile = tempDir.newFileNio("test.jar");
    try (var jar = new Compressor.Jar(jarFile)) { jar.addManifest(new Manifest()); }
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jarFile));
    var jarRoot = VirtualFileManager.getInstance().findFileByUrl("jar://" + jarFile.toString().replace('\\', '/') + "!/");
    assertNotNull(jarRoot);

    var event = new VFileCreateEvent(this, jarRoot, "x.txt", false, null, null, null);
    assertEquals(jarFile.toString().replace('\\', '/') + "!/x.txt", event.getPath());
  }
}
