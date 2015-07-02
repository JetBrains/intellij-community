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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.containers.ContainerUtil.*;

/**
 * @author max
 */
public class FileWatcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull @Override
    protected NotificationGroup compute() {
      return new NotificationGroup("File Watcher Messages", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  public static class DirtyPaths {
    public final List<String> dirtyPaths = newArrayList();
    public final List<String> dirtyPathsRecursive = newArrayList();
    public final List<String> dirtyDirectories = newArrayList();

    private static final DirtyPaths EMPTY = new DirtyPaths();

    private boolean isEmpty() {
      return dirtyPaths.isEmpty() && dirtyPathsRecursive.isEmpty() && dirtyDirectories.isEmpty();
    }
  }

  private static final String PROPERTY_WATCHER_DISABLED = "idea.filewatcher.disabled";
  private static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";
  private static final String ROOTS_COMMAND = "ROOTS";
  private static final String EXIT_COMMAND = "EXIT";
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;

  private final ManagingFS myManagingFS;
  private final File myExecutable;
  private volatile MyProcessHandler myProcessHandler;
  private volatile int myStartAttemptCount = 0;
  private volatile boolean myIsShuttingDown = false;
  private final AtomicBoolean myFailureShownToTheUser = new AtomicBoolean(false);
  private final AtomicInteger mySettingRoots = new AtomicInteger(0);

  private volatile List<String> myRecursiveWatchRoots = emptyList();
  private volatile List<String> myFlatWatchRoots = emptyList();
  private volatile List<String> myManualWatchRoots = emptyList();
  private volatile List<Pair<String, String>> myMapping = emptyList();

  private final Object myLock = new Object();
  private DirtyPaths myDirtyPaths = new DirtyPaths();
  private final String[] myLastChangedPaths = new String[2];
  private int myLastChangedPathIndex;

  FileWatcher(@NotNull ManagingFS managingFS) {
    myManagingFS = managingFS;

    boolean disabled = Boolean.parseBoolean(System.getProperty(PROPERTY_WATCHER_DISABLED));
    myExecutable = getExecutable();

    if (disabled) {
      LOG.info("Native file watcher is disabled");
    }
    else if (myExecutable == null) {
      LOG.info("Native file watcher is not supported on this platform");
    }
    else if (!myExecutable.exists()) {
      notifyOnFailure(ApplicationBundle.message("watcher.exe.not.found"), null);
    }
    else if (!myExecutable.canExecute()) {
      notifyOnFailure(ApplicationBundle.message("watcher.exe.not.exe", myExecutable), new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          ShowFilePathAction.openFile(myExecutable);
        }
      });
    }
    else {
      try {
        startupProcess(false);
        LOG.info("Native file watcher is operational.");
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
        notifyOnFailure(ApplicationBundle.message("watcher.failed.to.start"), null);
      }
    }
  }

  public void dispose() {
    myIsShuttingDown = true;
    shutdownProcess();
  }

  public boolean isOperational() {
    return myProcessHandler != null;
  }

  public boolean isSettingRoots() {
    return isOperational() && mySettingRoots.get() > 0;
  }

  @NotNull
  public DirtyPaths getDirtyPaths() {
    synchronized (myLock) {
      if (!myDirtyPaths.isEmpty()) {
        DirtyPaths dirtyPaths = myDirtyPaths;
        myDirtyPaths = new DirtyPaths();
        myLastChangedPathIndex = 0;
        for (int i = 0; i < myLastChangedPaths.length; ++i) myLastChangedPaths[i] = null;
        return dirtyPaths;
      }
      else {
        return DirtyPaths.EMPTY;
      }
    }
  }

  @NotNull
  public List<String> getManualWatchRoots() {
    return myManualWatchRoots;
  }

  public void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat) {
    setWatchRoots(recursive, flat, false);
  }

  public boolean isWatched(@NotNull VirtualFile file) {
    return isOperational() && !checkWatchable(file.getPresentableUrl(), true, true).isEmpty();
  }

  /* internal stuff */

  @Nullable
  private static File getExecutable() {
    String execPath = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (execPath != null) return new File(execPath);

    String execName = getExecutableName(false);
    if (execName == null) return null;

    return FileUtil.findFirstThatExist(
      FileUtil.join(PathManager.getBinPath(), execName),
      FileUtil.join(PathManager.getHomePath(), "community", "bin", getExecutableName(true)),
      FileUtil.join(PathManager.getBinPath(), getExecutableName(true)));
  }

  @Nullable
  private static String getExecutableName(final boolean withSubDir) {
    if (SystemInfo.isWindows) return (withSubDir ? "win" + File.separator : "") + "fsnotifier.exe";
    else if (SystemInfo.isMac) return (withSubDir ? "mac" + File.separator : "") + "fsnotifier";
    else if (SystemInfo.isLinux) return (withSubDir ? "linux" + File.separator : "") +
                                        ("arm".equals(SystemInfo.OS_ARCH) ? (SystemInfo.is32Bit ? "fsnotifier-arm" : null)
                                                                          : (SystemInfo.isAMD64 ? "fsnotifier64" : "fsnotifier"));
    return null;
  }

  public void notifyOnFailure(final String cause, @Nullable final NotificationListener listener) {
    LOG.warn(cause);

    if (myFailureShownToTheUser.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          String title = ApplicationBundle.message("watcher.slow.sync");
          Notifications.Bus.notify(NOTIFICATION_GROUP.getValue().createNotification(title, cause, NotificationType.WARNING, listener));
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private void startupProcess(boolean restart) throws IOException {
    if (myIsShuttingDown) {
      return;
    }
    if (ShutDownTracker.isShutdownHookRunning()) {
      myIsShuttingDown = true;
      return;
    }

    if (myStartAttemptCount++ > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      notifyOnFailure(ApplicationBundle.message("watcher.failed.to.start"), null);
      return;
    }

    if (restart) {
      shutdownProcess();
    }

    LOG.info("Starting file watcher: " + myExecutable);
    ProcessBuilder processBuilder = new ProcessBuilder(myExecutable.getAbsolutePath());
    Process process = processBuilder.start();
    myProcessHandler = new MyProcessHandler(process);
    myProcessHandler.addProcessListener(new MyProcessAdapter());
    myProcessHandler.startNotify();

    if (restart) {
      List<String> recursive = myRecursiveWatchRoots;
      List<String> flat = myFlatWatchRoots;
      if (recursive.size() + flat.size() > 0) {
        setWatchRoots(recursive, flat, true);
      }
    }
  }

  private void shutdownProcess() {
    final OSProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      if (!processHandler.isProcessTerminated()) {
        boolean forceQuite = true;
        try {
          writeLine(EXIT_COMMAND);
          forceQuite = !processHandler.waitFor(500);
          if (forceQuite) {
            LOG.warn("File watcher is still alive. Doing a force quit.");
          }
        }
        catch (IOException ignore) { }
        if (forceQuite) {
          processHandler.destroyProcess();
        }
      }

      myProcessHandler = null;
    }
  }

  private synchronized void setWatchRoots(List<String> recursive, List<String> flat, boolean restart) {
    if (myProcessHandler == null || myProcessHandler.isProcessTerminated()) return;

    if (ApplicationManager.getApplication().isDisposeInProgress()) {
      recursive = flat = Collections.emptyList();
    }

    if (!restart && myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) {
      return;
    }

    mySettingRoots.incrementAndGet();
    myMapping = emptyList();
    myRecursiveWatchRoots = recursive;
    myFlatWatchRoots = flat;

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
      LOG.warn(e);
    }
  }

  private void writeLine(final String line) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("<< " + line);
    }

    final MyProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      processHandler.writeLine(line);
    }
  }

  private static class MyProcessHandler extends OSProcessHandler {
    private final BufferedWriter myWriter;

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private MyProcessHandler(@NotNull Process process) {
      super(process, null, null);  // do not access EncodingManager here
      myWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    }

    private void writeLine(String line) throws IOException {
      myWriter.write(line);
      myWriter.newLine();
      myWriter.flush();
    }

    @Override
    protected boolean useNonBlockingRead() {
      return false;
    }
  }

  @NotNull
  private Collection<String> checkWatchable(String reportedPath, boolean isExact, boolean fastPath) {
    if (reportedPath == null) return Collections.emptyList();

    List<String> flatWatchRoots = myFlatWatchRoots;
    List<String> recursiveWatchRoots = myRecursiveWatchRoots;
    if (flatWatchRoots.isEmpty() && recursiveWatchRoots.isEmpty()) return Collections.emptyList();

    List<Pair<String, String>> mapping = myMapping;
    Collection <String> affectedPaths = new SmartList<String>(reportedPath);
    for (Pair<String, String> map : mapping) {
      if (FileUtil.startsWith(reportedPath, map.first)) {
        affectedPaths.add(map.second + reportedPath.substring(map.first.length()));
      }
      else if (FileUtil.startsWith(reportedPath, map.second)) {
        affectedPaths.add(map.first + reportedPath.substring(map.second.length()));
      }
    }

    Collection<String> changedPaths = new SmartList<String>();
    ext:
    for (String path : affectedPaths) {
      if (fastPath && !changedPaths.isEmpty()) break;

      for (String root : flatWatchRoots) {
        if (FileUtil.namesEqual(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (isExact) {
          String parentPath = new File(path).getParent();
          if (parentPath != null && FileUtil.namesEqual(parentPath, root)) {
            changedPaths.add(path);
            continue ext;
          }
        }
      }

      for (String root : recursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (!isExact) {
          String parentPath = new File(root).getParent();
          if (parentPath != null && FileUtil.namesEqual(path, parentPath)) {
            changedPaths.add(root);
            continue ext;
          }
        }
      }
    }

    return changedPaths;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private enum WatcherOp {
    GIVEUP, RESET, UNWATCHEABLE, REMAP, MESSAGE, CREATE, DELETE, STATS, CHANGE, DIRTY, RECDIRTY
  }

  private class MyProcessAdapter extends ProcessAdapter {
    private WatcherOp myLastOp = null;
    private final List<String> myLines = newArrayList();

    @Override
    public void processTerminated(ProcessEvent event) {
      LOG.warn("Watcher terminated with exit code " + event.getExitCode());

      myProcessHandler = null;

      try {
        startupProcess(true);
      }
      catch (IOException e) {
        shutdownProcess();
        LOG.warn("Watcher terminated and attempt to restart has failed. Exiting watching thread.", e);
      }
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      if (outputType == ProcessOutputTypes.STDERR) {
        LOG.warn(event.getText().trim());
      }
      if (outputType != ProcessOutputTypes.STDOUT) {
        return;
      }

      final String line = event.getText().trim();
      if (LOG.isDebugEnabled()) {
        LOG.debug(">> " + line);
      }

      if (myLastOp == null) {
        final WatcherOp watcherOp;
        try {
          watcherOp = WatcherOp.valueOf(line);
        }
        catch (IllegalArgumentException e) {
          final String message = "Illegal watcher command: " + line;
          if (ApplicationManager.getApplication().isUnitTestMode()) LOG.debug(message); else LOG.error(message);
          return;
        }

        if (watcherOp == WatcherOp.GIVEUP) {
          notifyOnFailure(ApplicationBundle.message("watcher.gave.up"), null);
          myIsShuttingDown = true;
        }
        else if (watcherOp == WatcherOp.RESET) {
          reset();
        }
        else {
          myLastOp = watcherOp;
        }
      }
      else if (myLastOp == WatcherOp.MESSAGE) {
        notifyOnFailure(line, NotificationListener.URL_OPENING_LISTENER);
        myLastOp = null;
      }
      else if (myLastOp == WatcherOp.REMAP || myLastOp == WatcherOp.UNWATCHEABLE) {
        if ("#".equals(line)) {
          if (myLastOp == WatcherOp.REMAP) {
            processRemap();
          }
          else {
            mySettingRoots.decrementAndGet();
            processUnwatchable();
          }
          myLines.clear();
          myLastOp = null;
        }
        else {
          myLines.add(line);
        }
      }
      else {
        String path = line.replace('\0', '\n');  // unescape
        processChange(path, myLastOp);
        myLastOp = null;
      }
    }

    private void processRemap() {
      Set<Pair<String, String>> pairs = newHashSet();
      for (int i = 0; i < myLines.size() - 1; i += 2) {
        String pathA = preparePathForMapping(myLines.get(i));
        String pathB = preparePathForMapping(myLines.get(i + 1));
        pairs.add(Pair.create(pathA, pathB));
      }
      myMapping = newArrayList(pairs);
      notifyOnEvent();
    }

    private String preparePathForMapping(String path) {
      String localPath = FileUtil.toSystemDependentName(path);
      return localPath.endsWith(File.separator) ? localPath : localPath + File.separator;
    }

    private void processUnwatchable() {
      myManualWatchRoots = Collections.unmodifiableList(newArrayList(myLines));
      notifyOnEvent();
    }

    private void reset() {
      VirtualFile[] localRoots = myManagingFS.getLocalRoots();
      synchronized (myLock) {
        for (VirtualFile root : localRoots) {
          myDirtyPaths.dirtyPathsRecursive.add(root.getPresentableUrl());
        }
      }
      notifyOnEvent();
    }

    private void processChange(String path, WatcherOp op) {
      if (SystemInfo.isWindows && op == WatcherOp.RECDIRTY && path.length() == 3 && Character.isLetter(path.charAt(0))) {
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
        if (root != null) {
          synchronized (myLock) {
            myDirtyPaths.dirtyPathsRecursive.add(root.getPresentableUrl());
          }
        }
        notifyOnEvent();
        return;
      }

      if (op == WatcherOp.CHANGE) {
        // collapse subsequent change file change notifications that happen once we copy large file,
        // this allows reduction of path checks at least 20% for Windows
        synchronized (myLock) {
          for (int i = 0; i < myLastChangedPaths.length; ++i) {
            int last = myLastChangedPathIndex - i - 1;
            if (last < 0) last += myLastChangedPaths.length;
            String lastChangedPath = myLastChangedPaths[last];
            if (lastChangedPath != null && lastChangedPath.equals(path)) {
              return;
            }
          }
          myLastChangedPaths[myLastChangedPathIndex++] = path;
          if (myLastChangedPathIndex == myLastChangedPaths.length) myLastChangedPathIndex = 0;
        }
      }

      int length = path.length();
      if (length > 1 && path.charAt(length - 1) == '/') path = path.substring(0, length - 1);
      boolean exactPath = op != WatcherOp.DIRTY && op != WatcherOp.RECDIRTY;
      Collection<String> paths = checkWatchable(path, exactPath, false);

      if (paths.isEmpty()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Not watchable, filtered: " + path);
        }
        return;
      }

      synchronized (myLock) {
        switch (op) {
          case STATS:
          case CHANGE:
            myDirtyPaths.dirtyPaths.addAll(paths);
            break;

          case CREATE:
          case DELETE:
            for (String p : paths) {
              myDirtyPaths.dirtyPathsRecursive.add(p);
              String parentPath = new File(p).getParent();
              if (parentPath != null) {
                myDirtyPaths.dirtyPaths.add(parentPath);
              }
            }
            break;

          case DIRTY:
            myDirtyPaths.dirtyDirectories.addAll(paths);
            break;

          case RECDIRTY:
            myDirtyPaths.dirtyPathsRecursive.addAll(paths);
            break;

          default:
            LOG.error("Unexpected op: " + op);
        }
      }

      notifyOnEvent();
    }
  }

  /* test data and methods */

  private volatile Runnable myNotifier = null;

  private void notifyOnEvent() {
    final Runnable notifier = myNotifier;
    if (notifier != null) {
      notifier.run();
    }
  }

  @TestOnly
  public static Logger getLog() { return LOG; }

  @TestOnly
  public void startup(@Nullable final Runnable notifier) throws IOException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myIsShuttingDown = false;
    myStartAttemptCount = 0;
    startupProcess(false);
    myNotifier = notifier;
  }

  @TestOnly
  public void shutdown() throws InterruptedException {
    final Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myNotifier = null;

    final MyProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      myIsShuttingDown = true;
      shutdownProcess();

      long t = System.currentTimeMillis();
      while (!processHandler.isProcessTerminated()) {
        if ((System.currentTimeMillis() - t) > 5000) {
          throw new InterruptedException("Timed out waiting watcher process to terminate");
        }
        TimeoutUtil.sleep(100);
      }
    }
  }
}
