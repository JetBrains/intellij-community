/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FileWatcherTest extends PlatformLangTestCase {
  private static final int INTER_RESPONSE_DELAY = 500;  // time to wait for a next event in a sequence
  private static final int NATIVE_PROCESS_DELAY = 60000;  // time to wait for a native watcher response

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

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
  private final List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  @Override
  protected void setUp() throws Exception {
    LOG.debug("================== setting up " + getName() + " ==================");

    super.setUp();

    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, getProject());
    myWatcher = FileWatcher.getInstance();
    assertNotNull(myWatcher);
    assertFalse(myWatcher.isOperational());
    myWatcher.startup(myNotifier);
    assertTrue(myWatcher.isOperational());

    myFileSystem = LocalFileSystem.getInstance();
    assertNotNull(myFileSystem);

    myConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        myEvents.addAll(events);
      }
    });

    LOG.debug("================== setting up " + getName() + " ==================");
  }

  @Override
  protected void tearDown() throws Exception {
    LOG.debug("================== tearing down " + getName() + " ==================");

    try {
      myConnection.disconnect();
      myWatcher.shutdown();
    }
    finally {
      myFileSystem = null;
      myWatcher = null;
      super.tearDown();
    }

    LOG.debug("================== tearing down " + getName() + " ==================");
  }


  public void testFileRoot() throws Exception {
    final File file = FileUtil.createTempFile("test.", ".txt");
    refresh(file);
    final LocalFileSystem.WatchRequest request = watch(file);
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getAbsolutePath());
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

    final File file = FileUtil.createTempFile("test.", ".txt");
    refresh(file);

    final String watchRoot = file.getAbsolutePath().toUpperCase(Locale.US);
    final LocalFileSystem.WatchRequest request = watch(new File(watchRoot));
    try {
      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getAbsolutePath());
    }
    finally {
      unwatch(request);
      delete(file);
    }
  }

  public void testDirectoryRecursive() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    refresh(topDir);

    final LocalFileSystem.WatchRequest request = watch(topDir);
    try {
      myAccept = true;
      final File subDir = FileUtil.createTempDirectory(topDir, "sub.", null);
      assertEvent(VFileCreateEvent.class, subDir.getAbsolutePath());
      refresh(subDir);

      myAccept = true;
      final File file = FileUtil.createTempFile(subDir, "test.", ".txt", true, false);
      assertEvent(VFileCreateEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, file.getAbsolutePath());

      myAccept = true;
      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, file.getAbsolutePath());
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryFlat() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File watchedFile = FileUtil.createTempFile(topDir, "test.", ".txt", true, false);
    final File subDir = FileUtil.createTempDirectory(topDir, "sub.", null);
    final File unwatchedFile = FileUtil.createTempFile(subDir, "test.", ".txt", true, false);
    refresh(topDir);

    final LocalFileSystem.WatchRequest request = watch(topDir, false);
    try {
      myAccept = true;
      FileUtil.writeToFile(watchedFile, "new content");
      assertEvent(VFileContentChangeEvent.class, watchedFile.getAbsolutePath());

      myAccept = true;
      try {
        myTimeout = 10 * INTER_RESPONSE_DELAY;
        FileUtil.writeToFile(unwatchedFile, "new content");
        assertEvent(VFileEvent.class);
      }
      finally {
        myTimeout = NATIVE_PROCESS_DELAY;
      }
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryMixed() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File watchedFile1 = FileUtil.createTempFile(topDir, "test.", ".txt", true, false);
    final File sub1Dir = FileUtil.createTempDirectory(topDir, "sub1.", null);
    final File unwatchedFile = FileUtil.createTempFile(sub1Dir, "test.", ".txt", true, false);
    final File sub2Dir = FileUtil.createTempDirectory(topDir, "sub2.", null);
    final File sub2subDir = FileUtil.createTempDirectory(sub2Dir, "sub2.", null);
    final File watchedFile2 = FileUtil.createTempFile(sub2subDir, "test.", ".txt", true, false);
    refresh(topDir);

    final LocalFileSystem.WatchRequest topRequest = watch(topDir, false);
    final LocalFileSystem.WatchRequest subRequest = watch(sub2Dir);
    try {
      myAccept = true;
      FileUtil.writeToFile(watchedFile1, "new content");
      FileUtil.writeToFile(watchedFile2, "new content");
      FileUtil.writeToFile(unwatchedFile, "new content");
      assertEvent(VFileContentChangeEvent.class, watchedFile1.getAbsolutePath(), watchedFile2.getAbsolutePath());
    }
    finally {
      unwatch(subRequest, topRequest);
      delete(topDir);
    }
  }

  public void testDirectoryNonExisting() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File subDir = new File(topDir, "subDir");
    final File file = new File(subDir, "file.txt");
    refresh(topDir);

    final LocalFileSystem.WatchRequest request = watch(subDir);
    try {
      myAccept = true;
      assertTrue(subDir.toString(), subDir.mkdir());
      assertEvent(VFileCreateEvent.class, subDir.getAbsolutePath());
      refresh(subDir);

      myAccept = true;
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileCreateEvent.class, file.getAbsolutePath());
    }
    finally {
      unwatch(request);
      delete(topDir);
    }
  }

  public void testDirectoryOverlapping() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File file1 = FileUtil.createTempFile(topDir, "file1.", ".txt", true, false);
    final File subDir = FileUtil.createTempDirectory(topDir, "sub.", null);
    final File file2 = FileUtil.createTempFile(subDir, "file2.", ".txt", true, false);
    final File sideDir = FileUtil.createTempDirectory("side.", null);
    final File file3 = FileUtil.createTempFile(sideDir, "file3.", ".txt", true, false);
    refresh(topDir);
    refresh(sideDir);

    final LocalFileSystem.WatchRequest request1 = watch(subDir);
    final LocalFileSystem.WatchRequest request2 = watch(sideDir);
    try {
      myAccept = true;
      FileUtil.writeToFile(file1, "new content");
      FileUtil.writeToFile(file2, "new content");
      FileUtil.writeToFile(file3, "new content");
      assertEvent(VFileContentChangeEvent.class, file2.getAbsolutePath(), file3.getAbsolutePath());

      final LocalFileSystem.WatchRequest request3 = watch(topDir);
      try {
        myAccept = true;
        FileUtil.writeToFile(file1, "newer content");
        FileUtil.writeToFile(file2, "newer content");
        FileUtil.writeToFile(file3, "newer content");
        assertEvent(VFileContentChangeEvent.class, file1.getAbsolutePath(), file2.getAbsolutePath(), file3.getAbsolutePath());
      }
      finally {
        unwatch(request3);
      }

      myAccept = true;
      FileUtil.writeToFile(file1, "newest content");
      FileUtil.writeToFile(file2, "newest content");
      FileUtil.writeToFile(file3, "newest content");
      assertEvent(VFileContentChangeEvent.class, file2.getAbsolutePath(), file3.getAbsolutePath());

      myAccept = true;
      FileUtil.delete(file1);
      FileUtil.delete(file2);
      FileUtil.delete(file3);
      assertEvent(VFileDeleteEvent.class, file1.getAbsolutePath(), file2.getAbsolutePath(), file3.getAbsolutePath());
    }
    finally {
      unwatch(request1, request2);
      delete(topDir);
    }
  }

/*
  public void testSymlinkAboveWatchRoot() throws Exception {
    final File topDir = FileUtil.createTempDirectory("top.", null);
    final File topLink = SymlinkHandlingTest.createTempLink(topDir.getAbsolutePath(), "link");
    final File subDir = FileUtil.createTempDirectory(topDir, "sub.", null);
    final File file = FileUtil.createTempFile(subDir, "test.", ".txt", true, false);
    final File fileLink = new File(new File(topLink, subDir.getName()), file.getName());
    refresh(topDir);
    refresh(topLink);

    final LocalFileSystem.WatchRequest request = watch(topLink);
    try {
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, fileLink.getAbsolutePath());

      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, fileLink.getAbsolutePath());

      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, fileLink.getAbsolutePath());
    }
    finally {
      myFileSystem.removeWatchedRoot(request);
      delete(topLink);
      delete(topDir);
    }
  }

  public void testSymlinkBelowWatchRoot() throws Exception {
    final File targetDir = FileUtil.createTempDirectory("top.", null);
    final File file = FileUtil.createTempFile(targetDir, "test.", ".txt", true, false);
    final File linkDir = FileUtil.createTempDirectory("link.", null);
    final File link = new File(linkDir, "link");
    SymlinkHandlingTest.createTempLink(targetDir.getAbsolutePath(), link.getAbsolutePath());
    final File fileLink = new File(link, file.getName());
    refresh(targetDir);
    refresh(linkDir);

    final LocalFileSystem.WatchRequest request = watch(linkDir);
    try {
      FileUtil.writeToFile(file, "new content");
      assertEvent(VFileContentChangeEvent.class, fileLink.getAbsolutePath());

      FileUtil.delete(file);
      assertEvent(VFileDeleteEvent.class, fileLink.getAbsolutePath());

      FileUtil.writeToFile(file, "re-creation");
      assertEvent(VFileCreateEvent.class, fileLink.getAbsolutePath());
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

    final Set<Character> roots = ContainerUtil.map2Set(File.listRoots(),  new Function<File, Character>() {
      @Override
      public Character fun(File root) {
        return root.getPath().toLowerCase(Locale.US).charAt(0);
      }
    });
    char subst = 0;
    for (char c = 'e'; c <= 'z'; c++) {
      if (!roots.contains(c)) {
        subst = c;
        break;
      }
    }
    assertFalse("Occupied: " + roots.toString(), subst == 0);

    final File targetDir = FileUtil.createTempDirectory("top.", null);
    final File subDir = FileUtil.createTempDirectory(targetDir, "sub.", null);
    final File file = FileUtil.createTempFile(subDir, "test.", ".txt", true, false);
    final int rv = new GeneralCommandLine("subst", subst + ":", targetDir.getAbsolutePath()).createProcess().waitFor();
    assertEquals(0, rv);

    final String substRoot = (subst + ":").toUpperCase(Locale.US);
    VirtualDirectoryImpl.allowRootAccess(substRoot);

    final VirtualFile vfsRoot = myFileSystem.findFileByPath(substRoot);
    assertNotNull(substRoot, vfsRoot);

    try {
      final File substDir = new File(substRoot, subDir.getName());
      final File substFile = new File(substDir, file.getName());
      refresh(targetDir);
      refresh(substDir);

      final LocalFileSystem.WatchRequest request = watch(substDir);
      try {
        myAccept = true;
        FileUtil.writeToFile(file, "new content");
        assertEvent(VFileContentChangeEvent.class, substFile.getAbsolutePath());

        final LocalFileSystem.WatchRequest request2 = watch(targetDir);
        try {
          myAccept = true;
          FileUtil.delete(file);
          assertEvent(VFileDeleteEvent.class, file.getAbsolutePath(), substFile.getAbsolutePath());
        }
        finally {
          unwatch(request2);
        }

        myAccept = true;
        FileUtil.writeToFile(file, "re-creation");
        assertEvent(VFileCreateEvent.class, substFile.getAbsolutePath());
      }
      finally {
        unwatch(request);
      }
    }
    finally {
      delete(targetDir);
      new GeneralCommandLine("subst", subst + ":", "/d").createProcess().waitFor();
      ((NewVirtualFile)vfsRoot).markDirty();
      myFileSystem.refresh(false);
      VirtualDirectoryImpl.disallowRootAccess(substRoot);
    }
  }


  @NotNull
  private LocalFileSystem.WatchRequest watch(final File watchFile) {
    return watch(watchFile, true);
  }

  @NotNull
  private LocalFileSystem.WatchRequest watch(final File watchFile, final boolean recursive) {
    final Ref<LocalFileSystem.WatchRequest> request = Ref.create();
    getEvents(new Runnable() {
      @Override
      public void run() {
        request.set(myFileSystem.addRootToWatch(watchFile.getAbsolutePath(), recursive));
      }
    });
    assertFalse(request.isNull());
    return request.get();
  }

  private void unwatch(final LocalFileSystem.WatchRequest... requests) {
    getEvents(new Runnable() {
      @Override
      public void run() {
        myFileSystem.removeWatchedRoots(Arrays.asList(requests));
      }
    });
  }

  private VirtualFile refresh(final File file) {
    final VirtualFile vFile = myFileSystem.refreshAndFindFileByIoFile(file);
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

  private void delete(@NotNull final File file) throws IOException {
    final VirtualFile vFile = myFileSystem.findFileByIoFile(file);
    if (vFile != null) {
      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
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

  private void assertEvent(final Class<? extends VFileEvent> type, final String... paths) {
    final List<VFileEvent> events = getEvents(null);
    assertEquals(events.toString(), paths.length, events.size());

    final Set<String> pathSet = ContainerUtil.map2Set(paths, new Function<String, String>() {
      @Override
      public String fun(final String path) {
        return FileUtil.toSystemIndependentName(path);
      }
    });

    for (final VFileEvent event : events) {
      assertTrue(event.toString(), type.isInstance(event));

      final VirtualFile eventFile = event.getFile();
      assertNotNull(event.toString(), eventFile);

      assertTrue(eventFile + " not in " + Arrays.toString(paths), pathSet.remove(eventFile.getPath()));
    }
  }

  private List<VFileEvent> getEvents(@Nullable final Runnable action) {
    LOG.debug("** waiting...");
    myAccept = true;

    if (action != null) {
      action.run();
    }

    int timeout = myTimeout;
    synchronized (myWaiter) {
      try {
        //noinspection WaitNotInLoop
        myWaiter.wait(timeout);
      }
      catch (InterruptedException e) {
        LOG.warn(e);
      }
    }

    myFileSystem.refresh(false);
    final ArrayList<VFileEvent> result = new ArrayList<VFileEvent>(myEvents);
    myEvents.clear();
    LOG.debug("** events: " + result.size());
    return result;
  }
}
