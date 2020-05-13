// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class VfsUtilTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void testFixIdeaUrl() {
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file://C:/Temp/README.txt"));
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file:///C:/Temp/README.txt"));
    assertEquals("file:/tmp/foo.bar", VfsUtilCore.fixIDEAUrl("file:///tmp/foo.bar"));
  }

  @Test
  public void testFindFileByUrl() throws IOException {
    File testDataDir = new File(PathManagerEx.getTestDataPath(), "vfs/findFileByUrl");

    VirtualFile vDir = VfsUtil.findFileByURL(testDataDir.toURI().toURL());
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());
    List<VirtualFile> list = VfsUtil.getChildren(vDir, file -> !file.getName().endsWith(".new"));
    assertEquals(2, list.size());

    File zip = new File(testDataDir, "test.zip");
    URL jarUrl = new URL("jar", "", zip.toURI().toURL().toExternalForm() + "!/");
    VirtualFile vZipRoot = VfsUtil.findFileByURL(jarUrl);
    assertNotNull(vZipRoot);
    assertTrue(vZipRoot.isDirectory());

    VirtualFile vZipDir = VfsUtil.findFileByURL(new URL(jarUrl, "com/intellij/installer"));
    assertNotNull(vZipDir);
    assertTrue(vZipDir.isDirectory());

    File file = new File(testDataDir, "1.txt");
    VirtualFile vFile = VfsUtil.findFileByURL(file.toURI().toURL());
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());
    assertEquals("test text", VfsUtilCore.loadText(vFile));
  }

  @Test
  public void testFindRelativeFile() throws IOException {
    File testDataDir = new File(PathManagerEx.getTestDataPath(), "vfs");
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(testDataDir);
    assertNotNull(vDir);
    assertEquals(vDir, VfsUtilCore.findRelativeFile(VfsUtilCore.convertFromUrl(testDataDir.toURI().toURL()), null));
    assertEquals(vDir, VfsUtilCore.findRelativeFile(testDataDir.getAbsolutePath(), null));
    assertEquals(vDir, VfsUtilCore.findRelativeFile("vfs", vDir.getParent()));
  }

  @Test
  public void testFindChildWithTrailingSpace() {
    File tempDir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    VirtualFile child = vDir.findChild(" ");
    assertNull(child);

    assertThat(vDir.getChildren()).isEmpty();
  }

  @Test
  public void testDirAttributeRefreshes() throws IOException {
    File file = myTempDir.newFile("test");
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());

    assertTrue(file.delete());
    assertTrue(file.mkdir());
    VirtualFile vFile2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile2);
    assertTrue(vFile2.isDirectory());

    assertTrue(file.delete());
    assertTrue(file.createNewFile());
    VirtualFile vFile3 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile3);
    assertFalse(vFile3.isDirectory());
  }

  @Test
  public void testPresentableUrlSurvivesDeletion() throws IOException {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir.newFile("file.txt"));
    assertNotNull(file);
    String url = file.getPresentableUrl();
    assertNotNull(url);
    WriteAction.runAndWait(() -> file.delete(this));
    assertEquals(url, file.getPresentableUrl());
  }

  @Test
  public void testToUri() {
    if (!SystemInfo.isWindows) {
      assertEquals("file:///asd", VfsUtil.toUri(new File("/asd")).toASCIIString());
      assertEquals("file:///asd%20/sd", VfsUtil.toUri(new File("/asd /sd")).toASCIIString());
    }

    URI uri = VfsUtil.toUri("file:///asd");
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

    if (SystemInfo.isWindows) {
      uri = VfsUtil.toUri("file://C:/p");
      assertNotNull(uri);
      assertEquals("file", uri.getScheme());
      assertEquals("/C:/p", uri.getPath());
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
  public void testFindRootWithDenormalizedPath() {
    File tempJar = IoTestUtil.createTestJar(myTempDir.newFile("test.jar"));
    VirtualFile jar = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempJar);
    assertNotNull(jar);

    JarFileSystem fs = JarFileSystem.getInstance();
    NewVirtualFile root1 = ManagingFS.getInstance().findRoot(jar.getPath() + "!/", fs);
    NewVirtualFile root2 = ManagingFS.getInstance().findRoot(jar.getParent().getPath() + "//" + jar.getName() + "!/", fs);
    assertNotNull(root1);
    assertSame(root1, root2);
  }

  @Test
  public void testFindRootWithCrazySlashes() {
    for (int i = 0; i < 10; i++) {
      String path = StringUtil.repeat("/", i);
      VirtualFile root = LocalFileSystem.getInstance().findFileByPathIfCached(path);
      assertTrue(path, root == null || !root.getPath().contains("//"));
      VirtualFile root2 = LocalFileSystem.getInstance().findFileByPath(path);
      assertTrue(path, root2 == null || !root2.getPath().contains("//"));
    }
  }

  @Test
  public void testNotCanonicallyNamedChild() throws IOException {
    File tempDir = myTempDir.newDirectory();
    assertTrue(new File(tempDir, "libFiles").createNewFile());
    assertTrue(new File(tempDir, "CssInvalidElement").createNewFile());
    assertTrue(new File(tempDir, "extFiles").createNewFile());

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
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

  @Test
  public void testRenameDuringFullRefresh() throws IOException { doRenameAndRefreshTest(true); }

  @Test
  public void testRenameDuringPartialRefresh() throws IOException { doRenameAndRefreshTest(false); }

  private void doRenameAndRefreshTest(boolean full) throws IOException {
    assertFalse(ApplicationManager.getApplication().isDispatchThread());

    File tempDir = myTempDir.newDirectory();
    assertTrue(new File(tempDir, "child").createNewFile());

    VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(parent);
    if (full) {
      assertEquals(1, parent.getChildren().length);
    }
    VirtualFile child = parent.findChild("child");
    assertNotNull(child);

    List<VirtualFile> files = Collections.singletonList(parent);

    Semaphore semaphore = new Semaphore();
    for (int i = 0; i < 1000; i++) {
      semaphore.down();
      VfsUtil.markDirty(true, false, parent);
      LocalFileSystem.getInstance().refreshFiles(files, true, true, semaphore::up);

      assertTrue(child.isValid());
      String newName = "name" + i;
      WriteAction.runAndWait(() -> child.rename(this, newName));
      assertTrue(child.isValid());

      TimeoutUtil.sleep(1);  // needed to prevent frequent event detector from triggering
    }

    assertTrue(semaphore.waitFor(60000));
  }

  @Test(timeout = 20_000)
  public void testScanNewChildrenMustNotBeRunOutsideOfProjectRoots() throws Exception {
    checkNewDirAndRefresh(__-> {}, getAllExcludedCalled->assertFalse(getAllExcludedCalled.get()));
  }

  @Test(timeout = 20_000)
  public void testRefreshAndEspeciallyScanChildrenMustBeRunOutsideOfReadActionToAvoidUILags() throws Exception {
    AtomicReference<Project> project = new AtomicReference<>();
    checkNewDirAndRefresh(temp ->
        WriteCommandAction.runWriteCommandAction(null, ()->{
          project.set(HeavyPlatformTestCase.createProject(temp));
          assertTrue(ProjectManagerEx.getInstanceEx().openProject(project.get()));
          assertTrue(project.get().isOpen());
        }),
    getAllExcludedCalled -> {
      try {
        assertTrue(getAllExcludedCalled.get());
      }
      finally {
        // this concoction is to ensure close() is called on the mock ProjectManagerImpl
        assertTrue(project.get().isOpen());
        ApplicationManager.getApplication().invokeAndWait(() -> ProjectUtil.closeAndDispose(project.get()));
      }
    });
  }

  private void checkNewDirAndRefresh(Consumer<? super Path> dirCreatedCallback, Consumer<? super AtomicBoolean> getAllExcludedCalledChecker) throws IOException {
    AtomicBoolean getAllExcludedCalled = new AtomicBoolean();
    ProjectManagerImpl test = new ProjectManagerImpl() {
      @Override
      public String @NotNull [] getAllExcludedUrls() {
        getAllExcludedCalled.set(true);
        assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
        return super.getAllExcludedUrls();
      }
    };

    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ProjectManager.class, test, test);
    assertSame(test, ProjectManager.getInstance());

    try {
      final File temp = myTempDir.newDirectory();
      VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
      assertNotNull(vTemp);
      vTemp.getChildren(); //to force full dir refresh?!
      dirCreatedCallback.accept(temp.toPath());
      File d = new File(temp, "d");
      assertTrue(d.mkdir());
      File d1 = new File(d, "d1");
      assertTrue(d1.mkdir());
      File x = new File(d1, "x.txt");
      assertTrue(x.createNewFile());

      assertFalse(ApplicationManager.getApplication().isDispatchThread());
      VfsUtil.markDirty(true, false, vTemp);
      CountDownLatch refreshed = new CountDownLatch(1);
      LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(vTemp), false, true, refreshed::countDown);

      while (refreshed.getCount() != 0) {
        UIUtil.pump();
      }
      getAllExcludedCalledChecker.accept(getAllExcludedCalled);
    }
    finally {
      WriteAction.runAndWait(() -> Disposer.dispose(test));
    }
  }

  @Test
  public void asyncRefreshInModalProgressCompletesWithinIt() {
    EdtTestUtil.runInEdtAndWait(() -> {
      VirtualDirectoryImpl vTemp = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir.getRoot());
      assertThat(vTemp.getChildren()).isEmpty();

      myTempDir.newFile("x.txt");

      ProgressManager.getInstance().run(new Task.Modal(null, "", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          assertFalse(ApplicationManager.getApplication().isDispatchThread());

          Semaphore semaphore = new Semaphore(1);
          vTemp.refresh(true, true, semaphore::up);
          assertTrue(semaphore.waitFor(10_000));
          assertThat(vTemp.getChildren()).hasSize(1);
        }
      });
    });
  }

  @Test(timeout = 20_000)
  public void olderRefreshWithLessSpecificModalityDoesNotBlockNewerRefresh_NoWaiting() {
    checkNonModalThenModalRefresh(false);
  }

  @Test(timeout = 20_000)
  public void olderRefreshWithLessSpecificModalityDoesNotBlockNewerRefresh_WithWaiting() {
    checkNonModalThenModalRefresh(true);
  }

  private void checkNonModalThenModalRefresh(boolean waitForDiskRefreshCompletionBeforeStartingModality) {
    EdtTestUtil.runInEdtAndWait(() -> {
      File dir1 = myTempDir.newDirectory("dir1");
      File dir2 = myTempDir.newDirectory("dir2");
      VirtualFile vDir = VfsUtil.findFileByIoFile(myTempDir.getRoot(), true);
      assertThat(Stream.of(vDir.getChildren()).map(VirtualFile::getName)).containsExactly(dir1.getName(), dir2.getName());
      VirtualFile vDir1 = vDir.getChildren()[0];
      VirtualFile vDir2 = vDir.getChildren()[1];
      assertThat(vDir1.getChildren()).isEmpty();
      assertThat(vDir2.getChildren()).isEmpty();

      assertTrue(new File(dir1, "a.txt").createNewFile());
      assertTrue(new File(dir2, "a.txt").createNewFile());

      List<String> log = new ArrayList<>();
      Semaphore semaphore = new Semaphore(1);

      RefreshSession nonModalSession = RefreshQueue.getInstance().createSession(true, true, () -> {
        log.add("non-modal finished");
        semaphore.up();
      });
      nonModalSession.addFile(vDir1);
      nonModalSession.launch();

      if (waitForDiskRefreshCompletionBeforeStartingModality) {
        TimeoutUtil.sleep(100);  // hopefully that's enough for refresh thread to see the disk changes
        UIUtil.dispatchAllInvocationEvents();
      }

      ProgressManager.getInstance().run(new Task.Modal(null, "", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          assertFalse(ApplicationManager.getApplication().isDispatchThread());

          Semaphore local = new Semaphore(1);
          vDir2.refresh(true, true, () -> {
            log.add("modal finished");
            local.up();
          });
          assertTrue(local.waitFor(10_000));
          assertThat(vDir2.getChildren()).hasSize(1);
        }
      });

      for (int i = 0; i < 10_000 && !semaphore.waitFor(1); i++) {
        UIUtil.dispatchAllInvocationEvents();
      }
      assertTrue(semaphore.waitFor(1));

      assertThat(vDir1.getChildren()).hasSize(1);
      assertThat(vDir2.getChildren()).hasSize(1);

      //todo order should be the same
      if (waitForDiskRefreshCompletionBeforeStartingModality) {
        assertThat(log).containsExactlyInAnyOrder("modal finished", "non-modal finished");
      } else {
        assertThat(log).containsExactly("modal finished", "non-modal finished");
      }
    });
  }
}