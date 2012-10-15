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
import com.intellij.openapi.vfs.watcher.ChangeKind;
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

  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;
  private static final int MAGIC_PROCESS_LAUNCH_ATTEMPT_COUNT = 88 * MAX_PROCESS_LAUNCH_ATTEMPT_COUNT;

  private final Object LOCK = new Object();

  private List<String> myDirtyPaths = newArrayList();
  private List<String> myDirtyRecursivePaths = newArrayList();
  private List<String> myDirtyDirs = newArrayList();

  private List<String> myManualWatchRoots = newArrayList();
  private List<String> myRecursiveWatchRoots = newArrayList();
  private List<String> myFlatWatchRoots = newArrayList();

  private final List<Pair<String, String>> myMapping = newArrayList();
  private final Collection<String> myAllPaths = newArrayListWithExpectedSize(2);
  private final Collection<String> myWatchedPaths = newArrayListWithExpectedSize(2);

  private File executable;
  private volatile Process notifierProcess;
  private volatile BufferedReader notifierReader;

  private volatile BufferedWriter notifierWriter;
  private boolean myFailureShownToTheUser = false;
  private int attemptCount = 0;
  private boolean isShuttingDown = false;

  private final ManagingFS myManagingFS;
  private static final FileWatcher ourInstance = new FileWatcher();

  public static FileWatcher getInstance() {
    return ourInstance;
  }

  private FileWatcher() {
    // to avoid deadlock (PY-1215), initialize ManagingFS reference in main thread, not in FileWatcher thread
    myManagingFS = ManagingFS.getInstance();

    final boolean explicitlyDisabled = Boolean.parseBoolean(System.getProperty(PROPERTY_WATCHER_DISABLED));
    try {
      if (!explicitlyDisabled) {
        startupProcess(false);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }

    if (notifierProcess != null) {
      LOG.info("Native file watcher is operational.");
      //noinspection CallToThreadStartDuringObjectConstruction
      new WatchForChangesThread().start();

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          isShuttingDown = true;
          shutdownProcess();
        }
      }, "FileWatcher shutdown hook"));
    }
    else {
      String message = explicitlyDisabled ? String.format("File watcher is disabled ('%s' property is set)", PROPERTY_WATCHER_DISABLED)
                                          : "File watcher failed to startup";
      LOG.info(message);
      notifyOnFailure(message, null);
    }
  }

  public List<String> getDirtyPaths() {
    synchronized (LOCK) {
      final List<String> result = myDirtyPaths;
      myDirtyPaths = new ArrayList<String>();
      return result;
    }
  }

  public List<String> getDirtyRecursivePaths() {
    synchronized (LOCK) {
      final List<String> result = myDirtyRecursivePaths;
      myDirtyRecursivePaths = new ArrayList<String>();
      return result;
    }
  }

  public List<String> getDirtyDirs() {
    synchronized (LOCK) {
      final List<String> result = myDirtyDirs;
      myDirtyDirs = new ArrayList<String>();
      return result;
    }
  }

  public List<String> getManualWatchRoots() {
    synchronized (LOCK) {
      return Collections.unmodifiableList(myManualWatchRoots);
    }
  }

  public void setWatchRoots(final List<String> recursive, final List<String> flat) {
    synchronized (LOCK) {
      if (myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) return;

      if (isAlive()) {
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
        }
      }

      myRecursiveWatchRoots = recursive;
      myFlatWatchRoots = flat;
      myMapping.clear();
    }
  }

  private boolean isAlive() {
    if (!isOperational()) return false;

    try {
      final Process process = notifierProcess;
      if (process != null) {
        process.exitValue();
      }
    }
    catch (IllegalThreadStateException e) {
      return true;
    }

    return false;
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private void startupProcess(final boolean restart) throws IOException {
    if (isShuttingDown) return;

    if (attemptCount++ > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      notifyOnFailure("File watcher cannot be started", null);
      throw new IOException("Can't launch process anymore");
    }

    shutdownProcess();

    if (executable == null) {
      executable = getExecutable();

      if (executable == null) {
        myFailureShownToTheUser = true;  // ignore unsupported platforms
        return;
      }

      if (!executable.exists()) {
        notifyOnFailure("File watcher is not found at path: " + executable, null);
        return;
      }

      if (!executable.canExecute()) {
        final String message = "File watcher is not executable: <a href=\"" + executable + "\">" + executable + "</a>";
        final File exec = executable;
        notifyOnFailure(message, new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            ShowFilePathAction.openFile(exec);
          }
        });
        return;
      }
    }

    LOG.info("Starting file watcher: " + executable);
    notifierProcess = Runtime.getRuntime().exec(new String[]{executable.getAbsolutePath()});
    notifierReader = new BufferedReader(new InputStreamReader(notifierProcess.getInputStream()));
    notifierWriter = new BufferedWriter(new OutputStreamWriter(notifierProcess.getOutputStream()));

    synchronized (LOCK) {
      if (restart && myRecursiveWatchRoots.size() + myFlatWatchRoots.size() > 0) {
        final List<String> recursiveWatchRoots = new ArrayList<String>(myRecursiveWatchRoots);
        final List<String> flatWatchRoots = new ArrayList<String>(myFlatWatchRoots);
        myRecursiveWatchRoots.clear();
        myFlatWatchRoots.clear();
        setWatchRoots(recursiveWatchRoots, flatWatchRoots);
      }
    }
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
    if (SystemInfo.isWindows) {
      return (withSubDir ? "win" + File.separator : "") + "fsnotifier.exe";
    }
    else if (SystemInfo.isMac) {
      return (withSubDir ? "mac" + File.separator : "") + "fsnotifier";
    }
    else if (SystemInfo.isLinux) {
      return (withSubDir ? "linux" + File.separator : "") + (SystemInfo.isAMD64 ? "fsnotifier64" : "fsnotifier");
    }

    return null;
  }

  private void notifyOnFailure(String cause, @Nullable NotificationListener listener) {
    if (!myFailureShownToTheUser) {
      myFailureShownToTheUser = true;
      Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "External file sync may be slow",
                                                cause, NotificationType.WARNING, listener));
    }
  }

  private void shutdownProcess() {
    if (notifierProcess != null) {
      if (isAlive()) {
        try {
          writeLine(EXIT_COMMAND);
        }
        catch (IOException ignore) { }
      }

      notifierProcess = null;
      notifierReader = null;
      notifierWriter = null;
    }
  }

  public boolean isOperational() {
    return notifierProcess != null;
  }

  @TestOnly
  public static Logger getLog() { return LOG; }

  @TestOnly
  public void startup(@Nullable final Runnable notifier) throws IOException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myFailureShownToTheUser = true;
    attemptCount = 0;
    startupProcess(false);
    attemptCount = MAGIC_PROCESS_LAUNCH_ATTEMPT_COUNT;
    if (notifierProcess != null) {
      (myThread = new WatchForChangesThread()).start();
    }

    myNotifier = notifier;
  }

  @TestOnly
  public void shutdown() throws InterruptedException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myNotifier = null;

    final Process process = notifierProcess;
    if (process != null) {
      shutdownProcess();
      process.waitFor();
      if (myThread != null && myThread.isAlive()) {
        myThread.join(10000);
        assert !myThread.isAlive() : myThread;
      }
      myThread = null;
    }
  }

  private FileWatcher.WatchForChangesThread myThread = null;
  private volatile Runnable myNotifier = null;

  private void notifyOnEvent() {
    final Runnable notifier = myNotifier;
    if (notifier != null) {
      notifier.run();
    }
  }

  private class WatchForChangesThread extends Thread {
    public WatchForChangesThread() {
      super("WatchForChangesThread");
    }

    @Override
    public void run() {
      try {
        while (true) {
          if (ApplicationManager.getApplication().isDisposeInProgress() || notifierProcess == null || isShuttingDown) return;

          final String command = readLine();
          if (command == null) {
            if (attemptCount == MAGIC_PROCESS_LAUNCH_ATTEMPT_COUNT) {
              LOG.debug("Leaving watcher thread");
              return;
            }

            // Unexpected process exit, relaunch attempt
            startupProcess(true);
            continue;
          }

          if (GIVE_UP_COMMAND.equals(command)) {
            LOG.info("FileWatcher gives up to operate on this platform");
            shutdownProcess();
            return;
          }

          if (RESET_COMMAND.equals(command)) {
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

            synchronized (LOCK) {
              myManualWatchRoots = roots;
            }

            notifyOnEvent();
          }
          else if (MESSAGE_COMMAND.equals(command)) {
            final String message = readLine();
            if (message == null) break;

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

            synchronized (LOCK) {
              myMapping.clear();
              myMapping.addAll(pairs);
            }

            notifyOnEvent();
          }
          else {
            final String path = readLine();
            if (path == null) {
              // Unexpected process exit, relaunch attempt
              startupProcess(true);
              continue;
            }

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

            synchronized (LOCK) {
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
      }
      catch (IOException e) {
        reset();
        shutdownProcess();
        LOG.info("Watcher terminated and attempt to restart has failed. Exiting watching thread.", e);
      }
      finally {
        LOG.debug("Watcher thread finished");
      }
    }
  }

  private static String preparePathForMapping(final String path) {
    final String localPath = FileUtil.toSystemDependentName(path);
    return localPath.endsWith(File.separator) ? localPath : localPath + File.separator;
  }

  private void writeLine(String line) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("<< " + line);
    }

    final Process process = notifierProcess;
    final BufferedWriter writer = notifierWriter;
    try {
      if (writer != null) {
        writer.write(line);
        writer.newLine();
        writer.flush();
      }
    }
    catch (IOException e) {
      try {
        if (process != null) {
          process.exitValue();
        }
      }
      catch (IllegalThreadStateException e1) {
        throw e;
      }
      finally {
        notifierProcess = null;
        notifierWriter = null;
        notifierReader = null;
      }
    }
  }

  @Nullable
  private String readLine() throws IOException {
    final BufferedReader reader = notifierReader;
    if (reader == null) return null;

    final String line = reader.readLine();
    if (LOG.isDebugEnabled()) {
      LOG.debug(">> " + line);
    }
    return line;
  }

  public boolean isWatched(@NotNull final VirtualFile file) {
    if (isOperational()) {
      synchronized (LOCK) {
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

      case RESET:
        reset();
        break;
    }

    notifyOnEvent();
  }

  private void reset() {
    synchronized (LOCK) {
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
}
