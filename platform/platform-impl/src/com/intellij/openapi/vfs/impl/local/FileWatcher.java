/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.watcher.ChangeKind;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author max
 */
public class FileWatcher {
  @NonNls public static final String PROPERTY_WATCHER_DISABLED = "filewatcher.disabled";
  @NonNls private static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  @NonNls private static final String GIVE_UP_COMMAND = "GIVEUP";
  @NonNls private static final String RESET_COMMAND = "RESET";
  @NonNls private static final String UNWATCHABLE_COMMAND = "UNWATCHEABLE";
  @NonNls private static final String ROOTS_COMMAND = "ROOTS";
  @NonNls private static final String REMAP_COMMAND = "REMAP";
  @NonNls private static final String EXIT_COMMAND = "EXIT";
  @NonNls private static final String MESSAGE_COMMAND = "MESSAGE";

  private static final PairFunction<String,String,Boolean> PATH_COMPARATOR = new PairFunction<String, String, Boolean>() {
    @Override
    public Boolean fun(final String s1, final String s2) {
      return SystemInfo.isFileSystemCaseSensitive ? s1.equals(s2) : s1.equalsIgnoreCase(s2);
    }
  };

  private final Object LOCK = new Object();
  private final Lock SET_ROOTS_LOCK = new ReentrantLock(true);

  private List<String> myDirtyPaths = new ArrayList<String>();
  private List<String> myDirtyRecursivePaths = new ArrayList<String>();
  private List<String> myDirtyDirs = new ArrayList<String>();
  private List<String> myManualWatchRoots = new ArrayList<String>();

  private final List<Pair<String, String>> myMapping = new ArrayList<Pair<String, String>>();
  private List<Pair<String, String>> myCanonicalMapping = new ArrayList<Pair<String, String>>();

  private List<String> myRecursiveWatchRoots = new ArrayList<String>();
  private List<String> myFlatWatchRoots = new ArrayList<String>();

  private volatile Process notifierProcess;
  private volatile BufferedReader notifierReader;
  private volatile BufferedWriter notifierWriter;

  private boolean myFailureShownToTheUser = false;
  private int attemptCount = 0;
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;
  private boolean isShuttingDown = false;
  private final ManagingFS myManagingFS;

  private static final FileWatcher ourInstance = new FileWatcher();

  public static FileWatcher getInstance() {
    return ourInstance;
  }

  private FileWatcher() {
    // to avoid deadlock (PY-1215), initialize ManagingFS reference in main thread, not in FileWatcher thread
    myManagingFS = ManagingFS.getInstance();

    try {
      if (!"true".equals(System.getProperty(PROPERTY_WATCHER_DISABLED))) {
        startupProcess(false);
      }
    }
    catch (IOException ignore) { }

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
      LOG.info("Native file watcher failed to startup.");
      notifyOnFailure("File watcher failed to startup", null);
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
    SET_ROOTS_LOCK.lock();
    try {
      synchronized (LOCK) {
        if (myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) return;
      }

      final List<Pair<String, String>> mapping = new ArrayList<Pair<String, String>>();
      long t = System.nanoTime();
      final List<String> checkedRecursive = checkPaths(recursive, mapping);
      final List<String> checkedFlat = checkPaths(flat, mapping);
      t = (System.nanoTime() - t) / 1000;
      LOG.info((recursive.size() + flat.size()) + " paths checked, " + mapping.size() + " mapped, " + t + " mks");

      if (isAlive()) {
        try {
          writeLine(ROOTS_COMMAND);
          for (String path : checkedRecursive) {
            writeLine(path);
          }
          for (String path : checkedFlat) {
            writeLine("|" + path);
          }
          writeLine("#");
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      synchronized (LOCK) {
        myRecursiveWatchRoots = recursive;
        myFlatWatchRoots = flat;
        myMapping.clear();
        myCanonicalMapping = mapping;
      }
    }
    finally {
      SET_ROOTS_LOCK.unlock();
    }
  }

  private static List<String> checkPaths(final List<String> paths, final List<Pair<String, String>> mapping) {
    if (!SystemInfo.areSymLinksSupported) return paths;

    final List<String> checkedPaths = new ArrayList<String>(paths.size());
    for (String path : paths) {
      String watched = path;
      final String canonical = getCanonicalPath(path);
      //noinspection ConstantConditions
      if (!PATH_COMPARATOR.fun(path, canonical)) {
        mapping.add(Pair.create((watched = canonical), path));
      }
      checkedPaths.add(watched);
    }
    return checkedPaths;
  }

  private static String getCanonicalPath(final String path) {
    final String realPath = FileSystemUtil.resolveSymLink(path);
    return realPath != null ? realPath : path;
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

    String execPath = null;

    final String altExecPath = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (altExecPath != null && new File(altExecPath).isFile()) {
      execPath = FileUtil.toSystemDependentName(altExecPath);
    }

    if (execPath == null) {
      final String execName;
      execName = getExecutableName();
      if (execName == null) {
        myFailureShownToTheUser = true;  // ignore unsupported platforms
        return;
      }
      execPath = PathManager.getBinPath() + File.separatorChar + execName;
    }

    final File exec = new File(execPath);
    if (!exec.exists()) {
      notifyOnFailure("File watcher is not found at path: " + execPath, null);
      return;
    }

    if (!exec.canExecute()) {
      notifyOnFailure("File watcher is not executable: <a href=\"" + execPath + "\">" + execPath +"</a>", new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          ShowFilePathAction.open(exec, exec);
        }
      });
      return;
    }

    LOG.info("Starting file watcher: " + execPath);

    notifierProcess = Runtime.getRuntime().exec(new String[]{execPath});

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
  private static String getExecutableName() {
    if (SystemInfo.isWindows) {
      return "fsnotifier.exe";
    }
    else if (SystemInfo.isMac) {
      return "fsnotifier";
    }
    else if (SystemInfo.isLinux) {
      return SystemInfo.isAMD64 ? "fsnotifier64" : "fsnotifier";
    }

    return null;
  }

  private void notifyOnFailure(String cause, @Nullable NotificationListener listener) {
    if (!myFailureShownToTheUser) {
      myFailureShownToTheUser = true;
      Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "External file sync may be slow", cause, NotificationType.WARNING, listener));
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

  private class WatchForChangesThread extends Thread {
    public WatchForChangesThread() {
      //noinspection HardCodedStringLiteral
      super("WatchForChangesThread");
    }

    @Override
    public void run() {
      try {
        while (true) {
          if (ApplicationManager.getApplication().isDisposeInProgress() || notifierProcess == null || isShuttingDown) return;

          final String command = readLine();
          if (command == null) {
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

              pairs.add(new Pair<String, String>(ensureEndsWithSlash(pathA), ensureEndsWithSlash(pathB)));
            }
            while (true);

            synchronized (LOCK) {
              myMapping.clear();
              myMapping.addAll(pairs);
            }
          }
          else {
            final String path = readLine();
            if (path == null) {
              // Unexpected process exit, relaunch attempt
              startupProcess(true);
              continue;
            }

            synchronized (LOCK) {
              final String watchedPath = checkWatchable(path);
              if (watchedPath != null) {
                try {
                  onPathChange(ChangeKind.valueOf(command), watchedPath);
                }
                catch (IllegalArgumentException e) {
                  LOG.error("Illegal watcher command: " + command);
                }
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
    }
  }

  private static String ensureEndsWithSlash(String path) {
    if (path.endsWith("/") || path.endsWith(File.separator)) return path;
    return path + '/';
  }

  private void writeLine(String line) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("to fsnotifier: " + line);
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
      LOG.debug("fsnotifier says: " + line);
    }
    return line;
  }

  public boolean isWatched(VirtualFile file) {
    return isOperational() && checkWatchable(file.getPresentableUrl()) != null;
  }

  @Nullable
  private String checkWatchable(String path) {
    if (path == null) return null;

    for (Pair<String, String> mapping : myCanonicalMapping) {
      if (path.startsWith(mapping.first)) {
        path = mapping.second + path.substring(mapping.first.length());
        break;
      }
    }

    for (String root : myRecursiveWatchRoots) {
      if (FileUtil.startsWith(path, root)) {
        return path;
      }
    }

    for (String root : myFlatWatchRoots) {
      if (FileUtil.pathsEqual(path, root)) {
        return path;
      }
      final File parentFile = new File(path).getParentFile();
      if (parentFile != null && FileUtil.pathsEqual(parentFile.getPath(), root)) {
        return path;
      }
    }

    return null;
  }

  private void onPathChange(final ChangeKind changeKind, final String path) {
    switch (changeKind) {
      case STATS:
      case CHANGE:
        addPath(path, myDirtyPaths);
        break;

      case CREATE:
      case DELETE:
        final File parentFile = new File(path).getParentFile();
        if (parentFile != null) {
          addPath(parentFile.getPath(), myDirtyPaths);
        }
        else {
          addPath(path, myDirtyPaths);
        }
        break;

      case DIRTY:
        addPath(path, myDirtyDirs);
        break;

      case RECDIRTY:
        addPath(path, myDirtyRecursivePaths);
        break;

      case RESET:
        reset();
        break;
    }
  }

  private void addPath(String path, List<String> list) {
    list.add(path);

    for (Pair<String, String> map : myMapping) {
      if (FileUtil.startsWith(path, map.getFirst())) {
        list.add(map.getSecond() + path.substring(map.getFirst().length()));
      }
      else if (FileUtil.startsWith(path, map.getSecond())) {
        list.add(map.getFirst() + path.substring(map.getSecond().length()));
      }
    }
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
  }
}
