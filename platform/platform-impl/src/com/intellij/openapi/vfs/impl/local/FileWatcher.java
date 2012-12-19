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

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;

/**
 * @author max
 */
public class FileWatcher {
  @NonNls public static final String PROPERTY_WATCHER_DISABLED = "idea.filewatcher.disabled";
  @NonNls public static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";

  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull @Override
    protected NotificationGroup compute() {
      return new NotificationGroup("File Watcher Messages", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  @NonNls private static final String ROOTS_COMMAND = "ROOTS";
  @NonNls private static final String EXIT_COMMAND = "EXIT";

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
  private volatile MyProcessHandler myProcessHandler;
  private volatile int myStartAttemptCount = 0;
  private volatile boolean myIsShuttingDown = false;
  private volatile boolean myFailureShownToTheUser = false;

  /** @deprecated use {@linkplain com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl#getFileWatcher()} (to remove in IDEA 13) */
  public static FileWatcher getInstance() {
    return ((LocalFileSystemImpl)LocalFileSystem.getInstance()).getFileWatcher();
  }

  FileWatcher(@NotNull final ManagingFS managingFS) {
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

  private void notifyOnFailure(final String cause, @Nullable final NotificationListener listener) {
    LOG.warn(cause);

    if (!myFailureShownToTheUser) {
      myFailureShownToTheUser = true;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          String title = ApplicationBundle.message("watcher.slow.sync");
          Notifications.Bus.notify(NOTIFICATION_GROUP.getValue().createNotification(title, cause, NotificationType.WARNING, listener));
        }
      });
    }
  }

  private void startupProcess(final boolean restart) throws IOException {
    if (myIsShuttingDown) return;

    if (myStartAttemptCount++ > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      notifyOnFailure(ApplicationBundle.message("watcher.failed.to.start"), null);
      throw new IOException("Can't launch process anymore");
    }

    if (restart) {
      shutdownProcess();
    }

    LOG.info("Starting file watcher: " + myExecutable);
    final Process process = Runtime.getRuntime().exec(new String[]{myExecutable.getAbsolutePath()});  // use array to allow spaces in path
    myProcessHandler = new MyProcessHandler(process);
    myProcessHandler.addProcessListener(new MyProcessAdapter());
    myProcessHandler.startNotify();

    if (restart) {
      synchronized (myLock) {
        if (myRecursiveWatchRoots.size() + myFlatWatchRoots.size() > 0) {
          setWatchRoots(myRecursiveWatchRoots, myFlatWatchRoots, true);
        }
      }
    }
  }

  private void shutdownProcess() {
    final OSProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      if (!processHandler.isProcessTerminated()) {
        try {
          writeLine(EXIT_COMMAND);
        }
        catch (IOException ignore) { }
        processHandler.destroyProcess();
      }

      myProcessHandler = null;
    }
  }

  public boolean isOperational() {
    return myProcessHandler != null;
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
    if (myProcessHandler == null || myProcessHandler.isProcessTerminated()) return;

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

    private void writeLine(final String line) throws IOException {
      myWriter.write(line);
      myWriter.newLine();
      myWriter.flush();
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

  private enum WatcherOp {
    GIVEUP, RESET, UNWATCHEABLE, REMAP, MESSAGE, CREATE, DELETE, STATS, CHANGE, DIRTY, RECDIRTY
  }

  private class MyProcessAdapter extends ProcessAdapter {
    private WatcherOp myLastOp = null;
    private final List<String> myLines = newArrayList();

    @Override
    public void processTerminated(ProcessEvent event) {
      LOG.warn("Watcher terminated.");

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
      if (outputType != ProcessOutputTypes.STDOUT) return;

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
          LOG.info("Native file watcher gives up to operate on this platform");
          myIsShuttingDown = true;
          shutdownProcess();
        }
        else if (watcherOp == WatcherOp.RESET) {
          reset();
        }
        else {
          myLastOp = watcherOp;
        }
      }
      else if (myLastOp == WatcherOp.MESSAGE) {
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "File Watcher", line, NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER)
        );
        myLastOp = null;
      }
      else if (myLastOp == WatcherOp.REMAP || myLastOp == WatcherOp.UNWATCHEABLE) {
        if ("#".equals(line)) {
          if (myLastOp == WatcherOp.REMAP) {
            processRemap();
          }
          else {
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
        processChange(line, myLastOp);
        myLastOp = null;
      }
    }

    private void processRemap() {
      final Set<Pair<String, String>> pairs = newHashSet();
      for (int i = 0; i < myLines.size() - 1; i += 2) {
        final String pathA = preparePathForMapping(myLines.get(i));
        final String pathB = preparePathForMapping(myLines.get(i + 1));
        pairs.add(Pair.create(pathA, pathB));
      }

      synchronized (myLock) {
        myMapping.clear();
        myMapping.addAll(pairs);
      }

      notifyOnEvent();
    }

    private String preparePathForMapping(final String path) {
      final String localPath = FileUtil.toSystemDependentName(path);
      return localPath.endsWith(File.separator) ? localPath : localPath + File.separator;
    }

    private void processUnwatchable() {
      synchronized (myLock) {
        myManualWatchRoots = newArrayList(myLines);
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

    private void processChange(final String path, final WatcherOp op) {
      if (SystemInfo.isWindows && op == WatcherOp.RECDIRTY && path.length() == 3 && Character.isLetter(path.charAt(0))) {
        final VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
        if (root instanceof NewVirtualFile) {
          ((NewVirtualFile)root).markDirtyRecursively();
        }

        notifyOnEvent();
        return;
      }

      synchronized (myLock) {
        final boolean checkParent = !(op == WatcherOp.DIRTY || op == WatcherOp.RECDIRTY);
        final Collection<String> paths = checkWatchable(path, checkParent);

        if (paths.isEmpty()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Not watchable, filtered: " + path);
          }
          return;
        }

        switch (op) {
          case STATS:
          case CHANGE:
            myDirtyPaths.addAll(paths);
            break;

          case CREATE:
          case DELETE:
            for (String p : paths) {
              final File parent = new File(p).getParentFile();
              myDirtyPaths.add(parent != null ? parent.getPath() : p);
            }
            break;

          case DIRTY:
            myDirtyDirs.addAll(paths);
            break;

          case RECDIRTY:
            myDirtyRecursivePaths.addAll(paths);
            break;

          default:
            LOG.error("Unexpected op: " + op);
        }

        notifyOnEvent();
      }
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
