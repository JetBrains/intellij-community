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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.newArrayListWithExpectedSize;

/**
 * @author max
 */
public class FileWatcher {
  @NonNls public static final String PROPERTY_WATCHER_DISABLED = "idea.filewatcher.disabled";
  @NonNls public static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  @NonNls private static final String GIVE_UP_COMMAND = "GIVEUP";
  @NonNls private static final String RESET_COMMAND = "RESET";
  @NonNls private static final String UNWATCHABLE_COMMAND = "UNWATCHEABLE";
  @NonNls private static final String ROOTS_COMMAND = "ROOTS";
  @NonNls private static final String REMAP_COMMAND = "REMAP";
  @NonNls private static final String EXIT_COMMAND = "EXIT";
  @NonNls private static final String MESSAGE_COMMAND = "MESSAGE";

  private enum ChangeKind {
    CREATE, DELETE, STATS, CHANGE, DIRTY, RECDIRTY
  }

  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;

  private final Object myLock = new Object();

  private List<String> myDirtyPaths = newArrayList();
  private List<String> myDirtyRecursivePaths = newArrayList();
  private List<String> myDirtyDirs = newArrayList();

  private List<String> myManualWatchRoots = newArrayList();
  private List<String> myRecursiveWatchRoots = newArrayList();
  private List<String> myFlatWatchRoots = newArrayList();

  private final List<Pair<String, String>> myMapping = newArrayList();
  private final Collection<String> myAllPaths = newArrayListWithExpectedSize(2);
  private final Collection<String> myWatchedPaths = newArrayListWithExpectedSize(2);

  private final ManagingFS myManagingFS;
  private final File myExecutable;

  private volatile Process myNotifierProcess;
  private volatile BufferedReader myNotifierReader;
  private volatile BufferedWriter myNotifierWriter;

  private volatile int myStartAttemptCount = 0;
  private volatile boolean myIsShuttingDown = false;
  private volatile boolean myFailureShownToTheUser = false;

  /** @deprecated use {@linkplain com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl#getFileWatcher()} (to remove in IDEA 13) */
  public static FileWatcher getInstance() {
    return ((LocalFileSystemImpl)LocalFileSystem.getInstance()).getFileWatcher();
  }

  FileWatcher(@NotNull final ManagingFS managingFS) {
    myManagingFS = managingFS;

    final boolean disabled = Boolean.parseBoolean(System.getProperty(PROPERTY_WATCHER_DISABLED));
    myExecutable = getExecutable();

    if (disabled) {
      LOG.info("Native file watcher is disabled");
    }
    else if (myExecutable == null) {
      LOG.info("Native file watcher is not supported on this platform");
    }
    else if (!myExecutable.exists()) {
      final String message = "Native file watcher executable not found";
      notifyOnFailure(message, null);
    }
    else if (!myExecutable.canExecute()) {
      final String message = "Native file watcher is not executable: <a href=\"" + myExecutable + "\">" + myExecutable + "</a>";
      notifyOnFailure(message, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          ShowFilePathAction.openFile(myExecutable);
        }
      });
    }
    else {
      try {
        startupProcess(false);
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
      }

      if (myNotifierProcess != null) {
        LOG.info("Native file watcher is operational.");
        //noinspection CallToThreadStartDuringObjectConstruction
        new WatchForChangesThread().start();
      }
      else {
        final String message = "File watcher failed to startup";
        notifyOnFailure(message, null);
      }
    }
  }

  public void dispose() {
    myIsShuttingDown = true;
    shutdownProcess();
  }

  @Nullable
  private static File getExecutable() {
    String execPath = null;

    final String altExecPath = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (altExecPath != null && new File(altExecPath).isFile()) {
      execPath = FileUtil.toSystemDependentName(altExecPath);
    }

    if (execPath == null) {
      final String execName = getExecutableName(false);
      if (execName == null) {
        return null;
      }
      execPath = FileUtil.join(PathManager.getBinPath(), execName);
    }

    File exec = new File(execPath);
    if (!exec.exists()) {
      String homePath = PathManager.getHomePath();
      if (new File(homePath, "community").exists()) {
        homePath += File.separator + "community";
      }
      exec = new File(FileUtil.join(homePath, "bin", getExecutableName(true)));
    }
    return exec;
  }

  @Nullable
  private static String getExecutableName(final boolean withSubDir) {
    if (SystemInfo.isWindows) return (withSubDir ? "win" + File.separator : "") + "fsnotifier.exe";
    else if (SystemInfo.isMac) return (withSubDir ? "mac" + File.separator : "") + "fsnotifier";
    else if (SystemInfo.isLinux) return (withSubDir ? "linux" + File.separator : "") + (SystemInfo.isAMD64 ? "fsnotifier64" : "fsnotifier");
    return null;
  }

  private void notifyOnFailure(String cause, @Nullable NotificationListener listener) {
    LOG.warn(cause);

    if (!myFailureShownToTheUser) {
      myFailureShownToTheUser = true;
      final Notification notification = new Notification(
        Notifications.SYSTEM_MESSAGES_GROUP_ID, "External file sync may be slow", cause, NotificationType.WARNING, listener);
      Notifications.Bus.notify(notification);
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void startupProcess(final boolean restart) throws IOException {
    if (myIsShuttingDown) return;

    if (myStartAttemptCount++ > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      notifyOnFailure("File watcher cannot be started", null);
      throw new IOException("Can't launch process anymore");
    }

    if (restart) {
      shutdownProcess();
    }

    LOG.info("Starting file watcher: " + myExecutable);
    myNotifierProcess = Runtime.getRuntime().exec(new String[]{myExecutable.getAbsolutePath()});  // use array to allow spaces in path
    myNotifierReader = new BufferedReader(new InputStreamReader(myNotifierProcess.getInputStream()));
    myNotifierWriter = new BufferedWriter(new OutputStreamWriter(myNotifierProcess.getOutputStream()));

    if (restart) {
      synchronized (myLock) {
        if (myRecursiveWatchRoots.size() + myFlatWatchRoots.size() > 0) {
          setWatchRoots(myRecursiveWatchRoots, myFlatWatchRoots, true);
        }
      }
    }
  }

  private void shutdownProcess() {
    if (myNotifierProcess != null) {
      if (isAlive()) {
        try {
          writeLine(EXIT_COMMAND);
        }
        catch (IOException ignore) { }
      }
    }

    myNotifierProcess = null;
    myNotifierReader = null;
    myNotifierWriter = null;
  }

  private boolean isAlive() {
    try {
      final Process process = myNotifierProcess;
      if (process != null) {
        process.exitValue();
      }
    }
    catch (IllegalThreadStateException e) {
      return true;
    }

    return false;
  }

  public boolean isOperational() {
    return myNotifierProcess != null;
  }

  public static class DirtyPaths {
    public final List<String> dirtyPaths;
    public final List<String> dirtyPathsRecursive;
    public final List<String> dirtyDirectories;

    private DirtyPaths(List<String> dirtyPaths, List<String> dirtyPathsRecursive, List<String> dirtyDirectories) {
      this.dirtyPaths = dirtyPaths;
      this.dirtyPathsRecursive = dirtyPathsRecursive;
      this.dirtyDirectories = dirtyDirectories;
    }
  }

  public DirtyPaths getDirtyPaths() {
    synchronized (myLock) {
      final DirtyPaths dirtyPaths = new DirtyPaths(myDirtyPaths, myDirtyRecursivePaths, myDirtyDirs);
      myDirtyPaths = new ArrayList<String>();
      myDirtyRecursivePaths = new ArrayList<String>();
      myDirtyDirs = new ArrayList<String>();
      return dirtyPaths;
    }
  }

  public List<String> getManualWatchRoots() {
    synchronized (myLock) {
      return Collections.unmodifiableList(myManualWatchRoots);
    }
  }

  public void setWatchRoots(final List<String> recursive, final List<String> flat) {
    setWatchRoots(recursive, flat, false);
  }

  private void setWatchRoots(List<String> recursive, List<String> flat, final boolean restart) {
    if (!isAlive()) return;

    if (ApplicationManager.getApplication().isDisposeInProgress()) {
      recursive = flat = Collections.emptyList();
    }

    synchronized (myLock) {
      if (!restart && myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) {
        return;
      }

      myMapping.clear();

      try {
        writeLine(ROOTS_COMMAND);
        for (String path : recursive) {
          writeLine(path);
        }
        for (String path : flat) {
          writeLine("|" + path);
        }
        writeLine("#");
      }
      catch (IOException e) {
        LOG.error(e);
        shutdownProcess();
      }

      myRecursiveWatchRoots = recursive;
      myFlatWatchRoots = flat;
    }
  }

  private void writeLine(final String line) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("<< " + line);
    }

    final BufferedWriter writer = myNotifierWriter;
    if (writer != null) {
      writer.write(line);
      writer.newLine();
      writer.flush();
    }
  }

  @NotNull
  private String readLine() throws IOException {
    final BufferedReader reader = myNotifierReader;
    if (reader == null) {
      throw new EOFException("Process terminated");
    }

    final String line = reader.readLine();
    if (LOG.isDebugEnabled()) {
      LOG.debug(">> " + line);
    }
    if (line == null) {
      throw new EOFException();
    }
    return line;
  }

  private class WatchForChangesThread extends Thread {
    public WatchForChangesThread() {
      super("WatchForChangesThread");
    }

    @Override
    public void run() {
      try {
        while (true) {
          if (myIsShuttingDown) {
            LOG.info("Shutting down - leaving watcher thread");
            return;
          }

          if (myNotifierProcess == null) {
            TimeoutUtil.sleep(1000);
            continue;
          }

          try {
            final String command = readLine();

            if (GIVE_UP_COMMAND.equals(command)) {
              LOG.info("Native file watcher gives up to operate on this platform");
              shutdownProcess();
              return;
            }
            else if (RESET_COMMAND.equals(command)) {
              reset();
            }
            else if (UNWATCHABLE_COMMAND.equals(command)) {
              List<String> roots = new ArrayList<String>();
              do {
                final String path = readLine();
                if (path == null || "#".equals(path)) break;
                roots.add(path);
              }
              while (true);

              synchronized (myLock) {
                myManualWatchRoots = roots;
              }

              notifyOnEvent();
            }
            else if (MESSAGE_COMMAND.equals(command)) {
              final String message = readLine();
              Notifications.Bus.notify(
                new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "File Watcher", message, NotificationType.WARNING,
                                 NotificationListener.URL_OPENING_LISTENER));
            }
            else if (REMAP_COMMAND.equals(command)) {
              Set<Pair<String, String>> pairs = new HashSet<Pair<String, String>>();
              do {
                final String pathA = readLine();
                if (pathA == null || "#".equals(pathA)) break;
                final String pathB = readLine();
                if (pathB == null || "#".equals(pathB)) break;

                pairs.add(Pair.create(preparePathForMapping(pathA), preparePathForMapping(pathB)));
              }
              while (true);

              synchronized (myLock) {
                myMapping.clear();
                myMapping.addAll(pairs);
              }

              notifyOnEvent();
            }
            else {
              final String path = readLine();

              final ChangeKind kind;
              try {
                kind = ChangeKind.valueOf(command);
              }
              catch (IllegalArgumentException e) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Illegal watcher command: " + command);
                }
                else {
                  LOG.error("Illegal watcher command: " + command);
                }
                continue;
              }

              synchronized (myLock) {
                if (isWindowsOverflow(path, kind)) {
                  resetRoot(path);
                  continue;
                }
                final Collection<String> watchedPaths = checkWatchable(path, !(kind == ChangeKind.DIRTY || kind == ChangeKind.RECDIRTY));
                if (!watchedPaths.isEmpty()) {
                  onPathChange(kind, watchedPaths);
                }
                else if (LOG.isDebugEnabled()) {
                  LOG.debug("Not watchable, filtered: " + path);
                }
              }
            }
          }
          catch (IOException e) {
            LOG.warn("Watcher terminated", e);
            startupProcess(true);
          }
        }
      }
      catch (IOException e) {
        shutdownProcess();
        LOG.warn("Watcher terminated and attempt to restart has failed. Exiting watching thread.", e);
      }
      catch (Throwable t) {
        shutdownProcess();
        LOG.error("Watcher thread stopped unexpectedly", t);
      }
      finally {
        LOG.debug("Watcher thread finished");
      }
    }

    private String preparePathForMapping(final String path) {
      final String localPath = FileUtil.toSystemDependentName(path);
      return localPath.endsWith(File.separator) ? localPath : localPath + File.separator;
    }
  }

  public boolean isWatched(@NotNull final VirtualFile file) {
    if (isOperational()) {
      synchronized (myLock) {
        return !checkWatchable(file.getPresentableUrl(), true).isEmpty();
      }
    }
    return false;
  }

  @NotNull
  private Collection<String> checkWatchable(final String reportedPath, final boolean checkParent) {
    if (reportedPath == null) return Collections.emptyList();

    myAllPaths.clear();
    myAllPaths.add(reportedPath);
    for (Pair<String, String> map : myMapping) {
      if (FileUtil.startsWith(reportedPath, map.first)) {
        myAllPaths.add(map.second + reportedPath.substring(map.first.length()));
      }
      else if (FileUtil.startsWith(reportedPath, map.second)) {
        myAllPaths.add(map.first + reportedPath.substring(map.second.length()));
      }
    }

    myWatchedPaths.clear();
    ext:
    for (String path : myAllPaths) {
      for (String root : myRecursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) {
          myWatchedPaths.add(path);
          continue ext;
        }
      }

      for (String root : myFlatWatchRoots) {
        if (FileUtil.pathsEqual(path, root)) {
          myWatchedPaths.add(path);
          continue ext;
        }
        if (checkParent) {
          final File parentFile = new File(path).getParentFile();
          if (parentFile != null && FileUtil.pathsEqual(parentFile.getPath(), root)) {
            myWatchedPaths.add(path);
            continue ext;
          }
        }
      }
    }
    return myWatchedPaths;
  }

  private void onPathChange(final ChangeKind changeKind, final Collection<String> paths) {
    switch (changeKind) {
      case STATS:
      case CHANGE:
        myDirtyPaths.addAll(paths);
        break;

      case CREATE:
      case DELETE:
        for (String path : paths) {
          final File parent = new File(path).getParentFile();
          myDirtyPaths.add(parent != null ? parent.getPath() : path);
        }
        break;

      case DIRTY:
        myDirtyDirs.addAll(paths);
        break;

      case RECDIRTY:
        myDirtyRecursivePaths.addAll(paths);
        break;
    }

    notifyOnEvent();
  }

  private void reset() {
    synchronized (myLock) {
      myDirtyPaths.clear();
      myDirtyDirs.clear();
      myDirtyRecursivePaths.clear();

      for (VirtualFile root : myManagingFS.getLocalRoots()) {
        ((NewVirtualFile)root).markDirtyRecursively();
      }
    }

    notifyOnEvent();
  }

  private static boolean isWindowsOverflow(final String path, final ChangeKind changeKind) {
    return SystemInfo.isWindows && changeKind == ChangeKind.RECDIRTY && path.length() == 3 && Character.isLetter(path.charAt(0));
  }

  private void resetRoot(final String path) {
    final VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
    if (root instanceof NewVirtualFile) {
      ((NewVirtualFile)root).markDirtyRecursively();
    }

    notifyOnEvent();
  }

  /* test data and methods */

  private FileWatcher.WatchForChangesThread myThread = null;
  private volatile Runnable myNotifier = null;

  @TestOnly
  public static Logger getLog() { return LOG; }

  @TestOnly
  public void startup(@Nullable final Runnable notifier) throws IOException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myIsShuttingDown = false;
    myStartAttemptCount = 0;
    startupProcess(false);
    if (myNotifierProcess != null) {
      (myThread = new WatchForChangesThread()).start();
    }

    myNotifier = notifier;
  }

  @TestOnly
  public void shutdown() throws InterruptedException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myNotifier = null;

    final Process process = myNotifierProcess;
    if (process != null) {
      myIsShuttingDown = true;
      shutdownProcess();
      process.waitFor();
      if (myThread != null && myThread.isAlive()) {
        myThread.join(10000);
        assert !myThread.isAlive() : myThread;
      }
      myThread = null;
    }
  }

  private void notifyOnEvent() {
    final Runnable notifier = myNotifier;
    if (notifier != null) {
      notifier.run();
    }
  }
}
