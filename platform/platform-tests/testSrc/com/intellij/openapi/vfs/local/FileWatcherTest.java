/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.*;

@SuppressWarnings("Duplicates")
public class FileWatcherTest extends PlatformTestCase {
  private static final Logger LOG = Logger.getInstance(NativeFileWatcherImpl.class);

  private static final int INTER_RESPONSE_DELAY = 500;  // time to wait for a next event in a sequence
  private static final int NATIVE_PROCESS_DELAY = 60000;  // time to wait for a native watcher response

  @SuppressWarnings("SpellCheckingInspection") private static final String UNICODE_NAME_1 = "Úñíçødê";
  @SuppressWarnings("SpellCheckingInspection") private static final String UNICODE_NAME_2 = "Юникоде";

  private FileWatcher myWatcher;
  private LocalFileSystem myFileSystem;
  private MessageBusConnection myConnection;
  private volatile boolean myAccept = false;
  private Alarm myAlarm;
  private final Object myWaiter = new Object();
  private int myTimeout = NATIVE_PROCESS_DELAY;
  private final List<VFileEvent> myEvents = ContainerUtil.newArrayList();
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    LOG.debug("================== setting up " + getName() + " ==================");

    super.setUp();

    myFileSystem = LocalFileSystem.getInstance();
    assertNotNull(myFileSystem);

    myWatcher = ((LocalFileSystemImpl)myFileSystem).getFileWatcher();
    assertNotNull(myWatcher);
    assertFalse(myWatcher.isOperational());
    myWatcher.startup(() -> {
      LOG.debug("-- (event, expected=" + myAccept + ")");
      if (!myAccept) return;
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> {
        myAccept = false;
        LOG.debug("** waiting finished");
        synchronized (myWaiter) {
          myWaiter.notifyAll();
        }
      }, INTER_RESPONSE_DELAY);
    });
    assertTrue(myWatcher.isOperational());

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getProject());
    myTimeout = NATIVE_PROCESS_DELAY;

    myConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        synchronized (myEvents) {
          events.stream().filter(VFileEvent::isFromRefresh).forEach(myEvents::add);
        }
      }
    });

    ((LocalFileSystemImpl)myFileSystem).cleanupForNextTest();

    myTempDirectory = createTestDir(getName());

    LOG.debug("================== setting up " + getName() + " ==================");
  }

  @Override
  protected void tearDown() throws Exception {
    LOG.debug("================== tearing down " + getName() + " ==================");

    try {
      myAccept = false;
      myAlarm.cancelAllRequests();
      myConnection.disconnect();
      myWatcher.shutdown();
      assertFalse(myWatcher.isOperational());
      FileUtil.delete(myTempDirectory);
    }
    finally {
      myFileSystem = null;
      myWatcher = null;
      super.tearDown();
    }

    LOG.debug("================== tearing down " + getName() + " ==================");
  }


  public void testWatchRequestConvention() {
    File dir = createTestDir("top");

    LocalFileSystem.WatchRequest r1 = myFileSystem.addRootToWatch(dir.getPath(), true);
    LocalFileSystem.WatchRequest r2 = myFileSystem.addRootToWatch(dir.getPath(), true);
    assertNotNull(r1);
    assertNotNull(r2);
    assertFalse(r1.equals(r2));

    myFileSystem.removeWatchedRoots(ContainerUtil.immutableList(r1, r2));
    FileUtil.delete(dir);
  }

  public void testFileRoot() throws Exception {
    File file = createTestFile(myTempDirectory, "test.txt");
    refresh(file);

    LocalFileSystem.WatchRequest request = watch(file);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getPath());
    }
    finally {
      unwatch(request);
      delete(file);
    }
  }

  public void testNonCanonicallyNamedFileRoot() throws Exception {
    if (SystemInfo.isFileSystemCaseSensitive) {
      System.err.println("Ignored: case-insensitive FS required");
      return;
    }

    File file = createTestFile(myTempDirectory, "test.txt");
    refresh(file);

    String watchRoot = file.getPath().toUpperCase(Locale.US);
    LocalFileSystem.WatchRequest request = watch(new File(watchRoot));
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getPath());
    }
    finally {
      unwatch(request);
      delete(file);
    }
  }

  public void testDirectoryRecursive() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      File subDir = createTestDir(topDir, "sub");
      assertEvent(VFileCreateEvent.class, subDir.getPath());
      refresh(subDir);

      myAccept = true;
      File file = createTestFile(subDir, "test.txt");
      assertEvent(VFileCreateEvent.class, file.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getPath());
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryFlat() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File watchedFile = createTestFile(topDir, "test.txt");
    File subDir = createTestDir(topDir, "sub");
    File unwatchedFile = createTestFile(subDir, "test.txt");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir, false);
    try {
      myAccept = true;
      FileUtil.writeToFile(watchedFile, "new content");
      assertEvent(VFileContentChangeEvent.class, watchedFile.getPath());

      myTimeout = 10 * INTER_RESPONSE_DELAY;
      myAccept = true;
      FileUtil.writeToFile(unwatchedFile, "new content");
      assertEvent(VFileEvent.class);
      myTimeout = NATIVE_PROCESS_DELAY;
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryMixed() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File watchedFile1 = createTestFile(topDir, "test.txt");
    File sub1Dir = createTestDir(topDir, "sub1");
    File unwatchedFile = createTestFile(sub1Dir, "test.txt");
    File sub2Dir = createTestDir(topDir, "sub2");
    File sub2subDir = createTestDir(sub2Dir, "sub");
    File watchedFile2 = createTestFile(sub2subDir, "test.txt");
    refresh(topDir);

    LocalFileSystem.WatchRequest topRequest = watch(topDir, false);
    LocalFileSystem.WatchRequest subRequest = watch(sub2Dir);
    try {
      myAccept = true;
      FileUtil.writeToFile(watchedFile1, "new content");
      FileUtil.writeToFile(watchedFile2, "new content");
      FileUtil.writeToFile(unwatchedFile, "new content");
      assertEvent(VFileContentChangeEvent.class, watchedFile1.getPath(), watchedFile2.getPath());
    }
    finally {
      unwatch(subRequest, topRequest);
      delete(topDir);
    }
  }

  public void testDirectoryNonExisting() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File subDir = new File(topDir, "subDir");
    File file = new File(subDir, "file.txt");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(subDir);
    try {
      myAccept = true;
      assertTrue(subDir.toString(), subDir.mkdir());
      assertEvent(VFileCreateEvent.class, subDir.getPath());
      refresh(subDir);

      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileCreateEvent.class, file.getPath());
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testIncorrectPath() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File file = createTestFile(topDir, "file.zip");
    File subDir = new File(file, "sub/zip");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(subDir, false);
    try {
      myTimeout = 10 * INTER_RESPONSE_DELAY;
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileEvent.class);
      myTimeout = NATIVE_PROCESS_DELAY;
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryOverlapping() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File fileInTopDir = createTestFile(topDir, "file1.txt");
    File subDir = createTestDir(topDir, "sub");
    File fileInSubDir = createTestFile(subDir, "file2.txt");
    File sideDir = createTestDir(myTempDirectory, "side");
    File fileInSideDir = createTestFile(sideDir, "file3.txt");
    refresh(topDir);
    refresh(sideDir);

    LocalFileSystem.WatchRequest requestForSubDir = watch(subDir);
    LocalFileSystem.WatchRequest requestForSideDir = watch(sideDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(fileInSubDir, "first content");
      FileUtil.writeToFile(fileInSideDir, "first content");
      assertEvent(VFileContentChangeEvent.class, fileInSubDir.getPath(), fileInSideDir.getPath());

      myAccept = true;
      FileUtil.writeToFile(fileInTopDir, "new content");
      FileUtil.writeToFile(fileInSubDir, "new content");
      FileUtil.writeToFile(fileInSideDir, "new content");
      assertEvent(VFileContentChangeEvent.class, fileInSubDir.getPath(), fileInSideDir.getPath());

      LocalFileSystem.WatchRequest requestForTopDir = watch(topDir);
      try {
        myAccept = true;
        FileUtil.writeToFile(fileInTopDir, "newer content");
        FileUtil.writeToFile(fileInSubDir, "newer content");
        FileUtil.writeToFile(fileInSideDir, "newer content");
        assertEvent(VFileContentChangeEvent.class, fileInTopDir.getPath(), fileInSubDir.getPath(), fileInSideDir.getPath());
      }
      finally {
        unwatch(requestForTopDir);
      }

      myAccept = true;
      FileUtil.writeToFile(fileInTopDir, "newest content");
      FileUtil.writeToFile(fileInSubDir, "newest content");
      FileUtil.writeToFile(fileInSideDir, "newest content");
      assertEvent(VFileContentChangeEvent.class, fileInSubDir.getPath(), fileInSideDir.getPath());

      myAccept = true;
      FileUtil.delete(fileInTopDir);
      FileUtil.delete(fileInSubDir);
      FileUtil.delete(fileInSideDir);
      assertEvent(VFileDeleteEvent.class, fileInTopDir.getPath(), fileInSubDir.getPath(), fileInSideDir.getPath());
    }
    finally {
      unwatch(requestForSubDir, requestForSideDir);
      delete(topDir);
    }
  }

  // ensure that flat roots set via symbolic paths behave correctly and do not report dirty files returned from other recursive roots
  public void testSymbolicLinkIntoFlatRoot() throws Exception {
    File rootDir = createTestDir("root");
    File aDir = createTestDir(rootDir, "A");
    File bDir = createTestDir(aDir, "B");
    File cDir = createTestDir(bDir, "C");
    File aLink = createSymLink(aDir.getPath(), rootDir.getPath() + "/aLink");
    File flatWatchedFile = createTestFile(aLink, "test.txt");
    File fileOutsideFlatWatchRoot = createTestFile(cDir, "test.txt");
    refresh(rootDir);

    LocalFileSystem.WatchRequest aLinkRequest = watch(aLink, false), cDirRequest = watch(cDir, false);
    try {
      myAccept = true;
      FileUtil.writeToFile(flatWatchedFile, "new content");
      assertEvent(VFileContentChangeEvent.class, flatWatchedFile.getPath());

      myAccept = true;
      FileUtil.writeToFile(fileOutsideFlatWatchRoot, "new content");
      assertEvent(VFileContentChangeEvent.class, fileOutsideFlatWatchRoot.getPath());
    }
    finally {
      unwatch(aLinkRequest);
      unwatch(cDirRequest);
      delete(rootDir);
    }
  }

  public void testMultipleSymbolicLinkPathsToFile() throws Exception {
    File rootDir = createTestDir("root");
    File aDir = createTestDir(rootDir, "A");
    File bDir = createTestDir(aDir, "B");
    File cDir = createTestDir(bDir, "C");
    File file = createTestFile(cDir, "test.txt");
    File bLink = createSymLink(bDir.getPath(), rootDir.getPath() + "/bLink");
    File cLink = createSymLink(cDir.getPath(), rootDir.getPath() + "/cLink");
    refresh(rootDir);

    String bFilePath = bLink.getPath() + "/" + cDir.getName() + "/" + file.getName();
    String cFilePath = cLink.getPath() + "/" + file.getName();

    LocalFileSystem.WatchRequest bRequest = watch(bLink), cRequest = watch(cLink);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, bFilePath, cFilePath);

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, bFilePath, cFilePath);

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, bFilePath, cFilePath);
    }
    finally {
      unwatch(bRequest);
      unwatch(cRequest);
      delete(rootDir);
    }
  }

  public void testSymbolicLinkAboveWatchRoot() throws Exception {
    File topDir = createTestDir("top");
    File topLink = createSymLink(topDir.getPath(), "link");
    File subDir = createTestDir(topDir, "sub");
    File file = createTestFile(subDir, "test.txt");
    File fileLink = new File(new File(topLink, subDir.getName()), file.getName());
    refresh(topDir);
    refresh(topLink);

    final LocalFileSystem.WatchRequest request = watch(topLink);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, fileLink.getPath());
    }
    finally {
      unwatch(request);
      delete(topLink);
      delete(topDir);
    }
  }

/*
  public void testSymlinkBelowWatchRoot() throws Exception {
    final File targetDir = FileUtil.createTempDirectory("top.", null);
    final File file = FileUtil.createTempFile(targetDir, "test.", ".txt");
    final File linkDir = FileUtil.createTempDirectory("link.", null);
    final File link = new File(linkDir, "link");
    IoTestUtil.createTempLink(targetDir.getPath(), link.getPath());
    final File fileLink = new File(link, file.getName());
    refresh(targetDir);
    refresh(linkDir);

    final LocalFileSystem.WatchRequest request = watch(linkDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, fileLink.getPath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, fileLink.getPath());
    }
    finally {
      myFileSystem.removeWatchedRoot(request);
      delete(linkDir);
      delete(targetDir);
    }
  }
*/

  public void testSubst() throws Exception {
    if (!SystemInfo.isWindows) {
      System.err.println("Ignored: Windows required");
      return;
    }

    File targetDir = createTestDir(myTempDirectory, "top");
    File subDir = createTestDir(targetDir, "sub");
    File file = createTestFile(subDir, "test.txt");
    File rootFile = createSubst(targetDir.getPath());
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), rootFile.getPath());
    VirtualFile vfsRoot = myFileSystem.findFileByIoFile(rootFile);

    try {
      assertNotNull(rootFile.getPath(), vfsRoot);
      File substDir = new File(rootFile, subDir.getName());
      File substFile = new File(substDir, file.getName());
      refresh(targetDir);
      refresh(substDir);

      LocalFileSystem.WatchRequest request = watch(substDir);
      try {
        myAccept = true;
        FileUtil.writeToFile(file, "new content");
        assertEvent(VFileContentChangeEvent.class, substFile.getPath());

        LocalFileSystem.WatchRequest request2 = watch(targetDir);
        try {
          myAccept = true;
          FileUtil.delete(file);
          assertEvent(VFileDeleteEvent.class, file.getPath(), substFile.getPath());
        }
        finally {
          unwatch(request2);
        }

        myAccept = true;
        FileUtil.writeToFile(file, "re-creation");
        assertEvent(VFileCreateEvent.class, substFile.getPath());
      }
      finally {
        unwatch(request);
      }
    }
    finally {
      delete(targetDir);
      deleteSubst(rootFile.getPath());
      if (vfsRoot != null) {
        ((NewVirtualFile)vfsRoot).markDirty();
        myFileSystem.refresh(false);
      }
    }
  }

  public void testDirectoryRecreation() throws Exception {
    File rootDir = createTestDir(myTempDirectory, "root");
    File topDir = createTestDir(rootDir, "top");
    File file1 = createTestFile(topDir, "file1.txt", "abc");
    File file2 = createTestFile(topDir, "file2.txt", "123");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(rootDir);
    try {
      myAccept = true;
      assertTrue(FileUtil.delete(topDir));
      assertTrue(topDir.mkdir());
      TimeoutUtil.sleep(100);
      assertTrue(file1.createNewFile());
      assertTrue(file2.createNewFile());
      assertEvent(VFileContentChangeEvent.class, file1.getPath(), file2.getPath());
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testWatchRootRecreation() throws Exception {
    File rootDir = createTestDir(myTempDirectory, "root");
    File file1 = createTestFile(rootDir, "file1.txt", "abc");
    File file2 = createTestFile(rootDir, "file2.txt", "123");
    refresh(rootDir);

    LocalFileSystem.WatchRequest request = watch(rootDir);
    try {
      myAccept = true;
      assertTrue(FileUtil.delete(rootDir));
      assertTrue(rootDir.mkdir());
      if (SystemInfo.isLinux) TimeoutUtil.sleep(1500);  // implementation specific
      assertTrue(file1.createNewFile());
      assertTrue(file2.createNewFile());
      assertEvent(VFileContentChangeEvent.class, file1.getPath(), file2.getPath());
    }
    finally {
      unwatch(request);
      delete(rootDir);
    }
  }

  public void testWatchRootRenameRemove() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File rootDir = createTestDir(topDir, "root");
    File rootDir2 = new File(topDir, "_" + rootDir.getName());
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(rootDir);
    try {
      myAccept = true;
      assertTrue(rootDir.renameTo(rootDir2));
      assertEvent(VFileEvent.class, rootDir.getPath(), rootDir2.getPath());

      myAccept = true;
      assertTrue(rootDir2.renameTo(rootDir));
      assertEvent(VFileEvent.class, rootDir.getPath(), rootDir2.getPath());

      myAccept = true;
      assertTrue(FileUtil.delete(rootDir));
      assertEvent(VFileDeleteEvent.class, rootDir.getPath());

      myAccept = true;
      assertTrue(rootDir.mkdirs());
      assertEvent(VFileCreateEvent.class, rootDir.getPath());

      myAccept = true;
      assertTrue(FileUtil.delete(topDir));
      assertEvent(VFileDeleteEvent.class, topDir.getPath());

      // todo[r.sh] current VFS implementation loses watch root once it's removed; this probably should be fixed
      myAccept = true;
      assertTrue(rootDir.mkdirs());
      assertEvent(VFileCreateEvent.class);
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testSwitchingToFsRoot() throws Exception {
    File topDir = createTestDir(myTempDirectory, "top");
    File rootDir = createTestDir(topDir, "root");
    File file1 = createTestFile(topDir, "1.txt");
    File file2 = createTestFile(rootDir, "2.txt");
    refresh(topDir);

    File fsRoot = new File(SystemInfo.isUnix ? "/" : topDir.getPath().substring(0, topDir.getPath().indexOf(File.separatorChar)) + "\\");
    assertTrue("can't guess root of " + topDir, fsRoot.exists());

    LocalFileSystem.WatchRequest request = watch(rootDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(file1, "abc");
      FileUtil.writeToFile(file2, "abc");
      assertEvent(VFileContentChangeEvent.class, file2.getPath());

      LocalFileSystem.WatchRequest rootRequest = watch(fsRoot);
      try {
        myTimeout = 10 * INTER_RESPONSE_DELAY;
        myAccept = true;
        FileUtil.writeToFile(file1, "12345");
        FileUtil.writeToFile(file2, "12345");
        assertEvent(VFileContentChangeEvent.class, file1.getPath(), file2.getPath());
        myTimeout = NATIVE_PROCESS_DELAY;
      }
      finally {
        unwatch(rootRequest);
      }

      myAccept = true;
      FileUtil.writeToFile(file1, "");
      FileUtil.writeToFile(file2, "");
      assertEvent(VFileContentChangeEvent.class, file2.getPath());
    }
    finally {
      unwatch(request);
    }

    myTimeout = 10 * INTER_RESPONSE_DELAY;
    myAccept = true;
    FileUtil.writeToFile(file1, "xyz");
    FileUtil.writeToFile(file2, "xyz");
    assertEvent(VFileEvent.class);
    myTimeout = NATIVE_PROCESS_DELAY;
  }

  public void testLineBreaksInName() throws Exception {
    if (!SystemInfo.isUnix) {
      System.err.println("Ignored: Unix required");
      return;
    }

    File topDir = createTestDir(myTempDirectory, "topDir");
    File testDir = createTestDir(topDir, "weird\ndir\nname");
    File testFile = createTestFile(testDir, "weird\nfile\nname");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(testFile, "abc");
      assertEvent(VFileContentChangeEvent.class, testFile.getPath());
    }
    finally {
      unwatch(request);
    }
  }

  public void testHiddenFiles() throws Exception {
    if (!SystemInfo.isWindows) {
      System.err.println("Ignored: Windows required");
      return;
    }

    File topDir = createTestDir(myTempDirectory, "topDir");
    File testDir = createTestDir(topDir, "dir");
    File testFile = createTestFile(testDir, "file", "123");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      setHidden(testFile.getPath(), true);
      assertEvent(VFilePropertyChangeEvent.class, testFile.getPath());
    }
    finally {
      unwatch(request);
    }
  }

  public void testFileCaseChange() throws Exception {
    if (SystemInfo.isFileSystemCaseSensitive) {
      System.err.println("Ignored: case-insensitive FS required");
      return;
    }

    File topDir = createTestDir(myTempDirectory, "topDir");
    File testFile = createTestFile(topDir, "file.txt", "123");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      File newFile = new File(testFile.getParent(), StringUtil.capitalize(testFile.getName()));
      FileUtil.rename(testFile, newFile);
      assertEvent(VFilePropertyChangeEvent.class, newFile.getPath());
    }
    finally {
      unwatch(request);
    }
  }

  public void testPartialRefresh() throws Exception {
    // tests the same scenario with an active file watcher (prevents explicit marking of refreshed paths)
    File top = createTestDir(myTempDirectory, "top");
    LocalFileSystemTest.doTestPartialRefresh(top);
  }

  public void testInterruptedRefresh() throws Exception {
    // tests the same scenario with an active file watcher (prevents explicit marking of refreshed paths)
    File top = createTestDir(myTempDirectory, "top");
    LocalFileSystemTest.doTestInterruptedRefresh(top);
  }

  public void testUnicodePaths() throws Exception {
    File topDir = createTestDir(myTempDirectory, UNICODE_NAME_1);
    File testFile = createTestFile(topDir, UNICODE_NAME_2 + ".txt");
    refresh(topDir);

    LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(testFile, "abc");
      assertEvent(VFileContentChangeEvent.class, testFile.getPath());
    }
    finally {
      unwatch(request);
    }
  }

  public void testDisplacementByIsomorphicTree() throws Exception {
    if (SystemInfo.isMac) {
      System.out.println("** skipped");
      return;
    }

    File top = createTestDir(myTempDirectory, "top");
    File up = createTestDir(top, "up");
    File middle = createTestDir(up, "middle");
    File file = createTestFile(middle, "file.txt", "original content");
    File up_copy = new File(top, "up_copy");
    FileUtil.copyDir(up, up_copy);
    FileUtil.writeToFile(file, "new content");

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    assertEquals("new content", VfsUtilCore.loadText(vFile));

    LocalFileSystem.WatchRequest request = watch(up);
    try {
      myAccept = true;
      FileUtil.rename(up, new File(top, "up.bak"));
      FileUtil.rename(up_copy, up);
      assertEvent(VFileContentChangeEvent.class, file.getPath());
      assertTrue(vFile.isValid());
      assertEquals("original content", VfsUtilCore.loadText(vFile));
    }
    finally {
      unwatch(request);
    }
  }

  public void testWatchRootReplacement() throws IOException {
    File top = createTestDir(myTempDirectory, "top");
    File dir1 = createTestDir(top, "dir1");
    File dir2 = createTestDir(top, "dir2");
    File f1 = createTestFile(dir1, "f.txt");
    File f2 = createTestFile(dir2, "f.txt");
    refresh(f1);
    refresh(f2);

    Ref<LocalFileSystem.WatchRequest> request = Ref.create(watch(dir1));
    try {
      myAccept = true;
      FileUtil.writeToFile(f1, "data");
      FileUtil.writeToFile(f2, "data");
      assertEvent(VFileContentChangeEvent.class, f1.getPath());

      String newRoot = dir2.getPath();
      getEvents("events to replace watch root", () -> request.set(myFileSystem.replaceWatchedRoot(request.get(), newRoot, true)));

      myAccept = true;
      FileUtil.writeToFile(f1, "more data");
      FileUtil.writeToFile(f2, "more data");
      assertEvent(VFileContentChangeEvent.class, f2.getPath());
    }
    finally {
      unwatch(request.get());
    }
  }

  public void testPermissionUpdate() throws IOException {
    File file = createTestFile(myTempDirectory, "test.txt", "some content");
    VirtualFile vFile = refresh(file);
    assertTrue(vFile.isWritable());
    boolean win = SystemInfo.isWindows;

    LocalFileSystem.WatchRequest request = watch(file);
    try {
      myAccept = true;
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine(win ? "attrib" : "chmod", win ? "+R" : "500", file.getPath()));
      assertEvent(VFilePropertyChangeEvent.class, file.getPath());
      assertFalse(vFile.isWritable());

      myAccept = true;
      PlatformTestUtil.assertSuccessful(new GeneralCommandLine(win ? "attrib" : "chmod", win ? "-R" : "700", file.getPath()));
      assertEvent(VFilePropertyChangeEvent.class, file.getPath());
      assertTrue(vFile.isWritable());
    }
    finally {
      unwatch(request);
      delete(file);
    }
  }


  @NotNull
  private LocalFileSystem.WatchRequest watch(File watchFile) {
    return watch(watchFile, true);
  }

  @NotNull
  private LocalFileSystem.WatchRequest watch(File watchFile, boolean recursive) {
    Ref<LocalFileSystem.WatchRequest> request = Ref.create();
    getEvents("events to add watch " + watchFile, () -> request.set(myFileSystem.addRootToWatch(watchFile.getPath(), recursive)));
    assertFalse(request.isNull());
    assertFalse(myWatcher.isSettingRoots());
    return request.get();
  }


  private void unwatch(LocalFileSystem.WatchRequest... requests) {
    getEvents("events to stop watching", () -> myFileSystem.removeWatchedRoots(Arrays.asList(requests)));
  }

  private VirtualFile refresh(File file) {
    VirtualFile vFile = myFileSystem.refreshAndFindFileByIoFile(file);
    assertNotNull(file.toString(), vFile);
    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        file.getChildren();
        return true;
      }
    });
    return vFile;
  }

  private void delete(File file) throws IOException {
    VirtualFile vFile = myFileSystem.findFileByIoFile(file);
    if (vFile != null) {
      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        vFile.delete(this);
      }
      finally {
        token.finish();
      }
    }
    if (file.exists()) {
      FileUtil.delete(file);
    }
  }

  private List<VFileEvent> getEvents(String msg, @Nullable Runnable action) {
    LOG.debug("** waiting for " + msg);
    myAccept = true;

    if (action != null) {
      action.run();
    }

    long timeout = myTimeout, start = System.currentTimeMillis();
    try {
      synchronized (myWaiter) {
        //noinspection WaitNotInLoop
        myWaiter.wait(timeout);
      }
    }
    catch (InterruptedException e) {
      LOG.warn(e);
    }

    LOG.debug("** waited for " + (System.currentTimeMillis() - start) + " of " + timeout);
    myFileSystem.refresh(false);

    List<VFileEvent> result;
    synchronized (myEvents) {
      result = ContainerUtil.newArrayList(myEvents);
      myEvents.clear();
    }

    LOG.debug("** events: " + result.size());
    return result;
  }

  private void assertEvent(Class<? extends VFileEvent> type, String... paths) {
    List<VFileEvent> events = getEvents(type.getSimpleName(), null);
    assertEquals(events.toString(), paths.length, events.size());

    Set<String> pathSet = Stream.of(paths).map(FileUtil::toSystemIndependentName).collect(Collectors.toSet());

    for (VFileEvent event : events) {
      assertTrue(event.toString(), type.isInstance(event));
      VirtualFile eventFile = event.getFile();
      assertNotNull(event.toString(), eventFile);
      assertTrue(eventFile + " not in " + Arrays.toString(paths), pathSet.remove(eventFile.getPath()));
    }
  }
}
