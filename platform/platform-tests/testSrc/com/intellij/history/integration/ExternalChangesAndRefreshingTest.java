// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Revision;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ExternalChangesAndRefreshingTest extends IntegrationTestCase {
  public void testRefreshingSynchronously() {
    doTestRefreshing(false);
  }

  public void testRefreshingAsynchronously() {
    doTestRefreshing(true);
  }

  @Override
  public void setUp() throws Exception {
    if (getName().equals("testRefreshingAsynchronously")) {
      // this methods waits for another thread to finish, that leads
      // to deadlock in swing-thread. Therefore we have to run this test
      // outside of swing-thread
      EdtTestUtil.runInEdtAndWait(ExternalChangesAndRefreshingTest.super::setUp);
    }
    else {
      super.setUp();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    if (getName().equals("testRefreshingAsynchronously")) {
      // this methods waits for another thread to finish, that leads
      // to deadlock in swing-thread. Therefore we have to run this test
      // outside of swing-thread
      EdtTestUtil.runInEdtAndWait(ExternalChangesAndRefreshingTest.super::tearDown);
    }
    else {
      //noinspection SuperTearDownInFinally
      super.tearDown();
    }
  }

  @Override
  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> r) throws Throwable {
    if (getName().equals("testRefreshingAsynchronously")) {
      // this method waits for another thread to finish, that leads
      // to deadlock in swing-thread. Therefore we have to run this test
      // outside of swing-thread
      r.run();
    }
    else {
      super.runBareRunnable(r);
    }
  }

  private void doTestRefreshing(boolean async) {
    int before = getRevisionsFor(myRoot).size();

    createFileExternally("f1.txt");
    createFileExternally("f2.txt");

    assertEquals(before, getRevisionsFor(myRoot).size());

    refreshVFS(async);

    assertEquals(before + 1, getRevisionsFor(myRoot).size());
  }

  public void testChangeSetName() {
    createFileExternally("f.txt");
    refreshVFS();
    Revision r = getRevisionsFor(myRoot).get(1);
    assertEquals("External change", r.getChangeSetName());
  }

  public void testRefreshDuringCommand() {
    // shouldn't throw
    CommandProcessor.getInstance().executeCommand(myProject, ExternalChangesAndRefreshingTest::refreshVFS, "", null);
  }

  public void testCommandDuringRefresh() {
    createFileExternally("f.txt");

    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        executeSomeCommand();
      }
    };

    // shouldn't throw
    addFileListenerDuring(l, ExternalChangesAndRefreshingTest::refreshVFS);
  }

  private void executeSomeCommand() {
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
    }, "", null);
  }

  public void testFileCreationDuringRefresh() throws Exception {
    String path = createFileExternally("f.txt");
    setContentExternally(path, "content");

    String[] content = new String[1];
    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        try {
          if (!e.getFile().getPath().equals(path)) {
            return;
          }
          content[0] = new String(e.getFile().contentsToByteArray(), StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    };

    addFileListenerDuring(l, ExternalChangesAndRefreshingTest::refreshVFS);
    assertEquals("content", content[0]);
  }

  public void testDeletionOfFilteredDirectoryExternallyDoesNotThrowExceptionDuringRefresh() {
    int before = getRevisionsFor(myRoot).size();

    createChildDirectory(myRoot, FILTERED_DIR_NAME);
    String path = Paths.appended(myRoot.getPath(), FILTERED_DIR_NAME);

    FileUtil.delete(new File(path));
    assertEquals(before, getRevisionsFor(myRoot).size());

    refreshVFS();

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testCreationOfExcludedDirWithFilesDuringRefreshShouldNotThrowException() throws Exception {
    // there was a problem with the DirectoryIndex - the files that were created during the refresh
    // were not correctly excluded, thereby causing the LocalHistory to fail during addition of
    // files under the excluded dir.

    File targetDir = createTargetDir();
    FileUtil.copyDir(targetDir, new File(myRoot.getPath(), "target"));
    VirtualFileManager.getInstance().syncRefresh();

    String classesPath = myRoot.getPath() + "/target/classes";
    addExcludedDir(classesPath);
    final VirtualFile classesDir = LocalFileSystem.getInstance().findFileByPath(classesPath);
    assertNotNull(classesDir);
    delete(classesDir.getParent());

    FileUtil.copyDir(targetDir, new File(myRoot.getPath(), "target"));
    VirtualFileManager.getInstance().syncRefresh(); // shouldn't throw
  }

  private File createTargetDir() throws IOException {
    File result = createTempDirectory();
    File classes = new File(result, "classes");
    assertTrue(classes.mkdir());
    assertTrue(new File(classes, "bak.txt").createNewFile());
    return result;
  }

  private static void refreshVFS() {
    refreshVFS(false);
  }

  private static void refreshVFS(boolean async) {
    try {
      final Semaphore s = new Semaphore(1);
      s.acquire();

      VirtualFileManager fm = VirtualFileManager.getInstance();
      if (async) {
        fm.asyncRefresh(s::release);
      }
      else {
        try {
          fm.syncRefresh();
        }
        finally {
          s.release();
        }
      }

      assertTrue(s.tryAcquire(1, TimeUnit.MINUTES));
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
