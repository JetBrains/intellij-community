/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.io.IoTestUtil.*;

@SuppressWarnings("Duplicates")
public class FileWatcherTest extends PlatformTestCase {
  private static final int INTER_RESPONSE_DELAY = 500;  // time to wait for a next event in a sequence
  private static final int NATIVE_PROCESS_DELAY = 60000;  // time to wait for a native watcher response

  private static Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  private FileWatcher myWatcher;
  private LocalFileSystem myFileSystem;
  private MessageBusConnection myConnection;
  private volatile boolean myAccept = false;
  private Alarm myAlarm;
  private final Runnable myNotifier = new Runnable() {
    @Override
    public void run() {
      LOG.debug("-- (event, expected=" + myAccept + ")");
      if (!myAccept) return;
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myAccept = false;
          LOG.debug("** waiting finished");
          synchronized (myWaiter) {
            myWaiter.notifyAll();
          }
        }
      }, INTER_RESPONSE_DELAY);
    }
  };
  private final Object myWaiter = new Object();
  private int myTimeout = NATIVE_PROCESS_DELAY;
  private final List<VFileEvent> myEvents = ContainerUtil.newArrayList();
  private final List<String> myAcceptedDirectories = ContainerUtil.newArrayList();

  @Override
  protected void setUp() throws Exception {
    LOG.debug("================== setting up " + getName() + " ==================");

    super.setUp();

    myFileSystem = LocalFileSystem.getInstance();
    assertNotNull(myFileSystem);

    myWatcher = ((LocalFileSystemImpl)myFileSystem).getFileWatcher();
    assertNotNull(myWatcher);
    assertFalse(myWatcher.isOperational());
    myWatcher.startup(myNotifier);
    assertTrue(myWatcher.isOperational());

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getProject());
    myTimeout = NATIVE_PROCESS_DELAY;

    myConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        synchronized (myEvents) {
          myEvents.addAll(events);
        }
      }
    });

    ((LocalFileSystemImpl)myFileSystem).cleanupForNextTest();

    myAcceptedDirectories.clear();
    myAcceptedDirectories.add(getTempDirectory().getAbsolutePath());

    LOG = FileWatcher.getLog();
    LOG.debug("================== setting up " + getName() + " ==================");
  }

  @Override
  protected void tearDown() throws Exception {
    LOG.debug("================== tearing down " + getName() + " ==================");

    try {
      myConnection.disconnect();
      myWatcher.shutdown();
      assertFalse(myWatcher.isOperational());
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
    File file = createTestFile("test.txt");
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

    File file = createTestFile("test.txt");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
    File fileInTopDir = createTestFile(topDir, "file1.txt");
    File subDir = createTestDir(topDir, "sub");
    File fileInSubDir = createTestFile(subDir, "file2.txt");
    File sideDir = createTestDir("side");
    File fileInSideDir = createTestFile(sideDir, "file3.txt");
    refresh(topDir);
    refresh(sideDir);

    LocalFileSystem.WatchRequest requestForSubDir = watch(subDir);
    LocalFileSystem.WatchRequest requestForSideDir = watch(sideDir);
    try {
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

/*
  public void testSymlinkAboveWatchRoot() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File topLink = IoTestUtil.createTempLink(topDir.getPath(), "link");
    final File subDir = FileUtil.createTempDirectory(topDir, "sub.", null);
    final File file = FileUtil.createTempFile(subDir, "test.", ".txt");
    final File fileLink = new File(new File(topLink, subDir.getName()), file.getName());
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
      myFileSystem.removeWatchedRoot(request);
      delete(topLink);
      delete(topDir);
    }
  }

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

    File targetDir = createTestDir("top");
    File subDir = createTestDir(targetDir, "sub");
    File file = createTestFile(subDir, "test.txt");
    File rootFile = createSubst(targetDir.getPath());
    VfsRootAccess.allowRootAccess(rootFile.getPath());
    VirtualFile vfsRoot = myFileSystem.findFileByIoFile(rootFile);

    try {
      assertNotNull(rootFile.getPath(), vfsRoot);
      File substDir = new File(rootFile, subDir.getName());
      File substFile = new File(substDir, file.getName());
      refresh(targetDir);
      refresh(substDir);
      myAcceptedDirectories.add(substDir.getPath());

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
      VfsRootAccess.disallowRootAccess(rootFile.getPath());
    }
  }

  public void testDirectoryRecreation() throws Exception {
    File rootDir = createTestDir("root");
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
    File rootDir = createTestDir("root");
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
    File topDir = createTestDir("top");
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
    File topDir = createTestDir("top");
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

    File topDir = createTestDir("topDir");
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

    File topDir = createTestDir("topDir");
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

    File topDir = createTestDir("topDir");
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
    File top = createTestDir("top");
    LocalFileSystemTest.doTestPartialRefresh(top);
  }

  public void testInterruptedRefresh() throws Exception {
    // tests the same scenario with an active file watcher (prevents explicit marking of refreshed paths)
    File top = createTestDir("top");
    LocalFileSystemTest.doTestInterruptedRefresh(top);
  }

  public void testUnicodePaths() throws Exception {
    if (!SystemInfo.isUnix || SystemInfo.isMac) {
      System.err.println("Ignored: well-defined FS required");
      return;
    }

    File topDir = createTestDir("top");
    File testDir = createTestDir(topDir, "тест");
    File testFile = createTestFile(testDir, "файл.txt");
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
      assertTrue("Kaboom", new GregorianCalendar(2015, Calendar.SEPTEMBER, 1).getTimeInMillis() > new Date().getTime());
      System.out.println("** skipped");
      return;
    }

    File top = createTestDir("top");
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


  @NotNull
  private LocalFileSystem.WatchRequest watch(File watchFile) {
    return watch(watchFile, true);
  }

  @NotNull
  private LocalFileSystem.WatchRequest watch(final File watchFile, final boolean recursive) {
    final Ref<LocalFileSystem.WatchRequest> request = Ref.create();
    getEvents("events to add watch " + watchFile, new Runnable() {
      @Override
      public void run() {
        request.set(myFileSystem.addRootToWatch(watchFile.getPath(), recursive));
      }
    });
    assertFalse(request.isNull());
    assertFalse(myWatcher.isSettingRoots());
    return request.get();
  }

  private void unwatch(final LocalFileSystem.WatchRequest... requests) {
    getEvents("events to stop watching", new Runnable() {
      @Override
      public void run() {
        myFileSystem.removeWatchedRoots(Arrays.asList(requests));
      }
    });
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

    if (!result.isEmpty()) {
      nextEvent:
      for (Iterator<VFileEvent> iterator = result.iterator(); iterator.hasNext(); ) {
        VFileEvent event = iterator.next();
        VirtualFile file = event.getFile();
        if (file != null) {
          for (String acceptedDirectory : myAcceptedDirectories) {
            if (FileUtil.isAncestor(acceptedDirectory, file.getPath(), false)) {
              continue nextEvent;
            }
          }
          LOG.debug("~~ not accepted: " + event);
          iterator.remove();
        }
      }
    }

    LOG.debug("** events: " + result.size());
    return result;
  }

  private void assertEvent(Class<? extends VFileEvent> type, String... paths) {
    List<VFileEvent> events = getEvents(type.getSimpleName(), null);
    assertEquals(events.toString(), paths.length, events.size());

    Set<String> pathSet = ContainerUtil.map2Set(paths, new Function<String, String>() {
      @Override
      public String fun(final String path) {
        return FileUtil.toSystemIndependentName(path);
      }
    });

    for (VFileEvent event : events) {
      assertTrue(event.toString(), type.isInstance(event));
      VirtualFile eventFile = event.getFile();
      assertNotNull(event.toString(), eventFile);
      assertTrue(eventFile + " not in " + Arrays.toString(paths), pathSet.remove(eventFile.getPath()));
    }
  }
}
