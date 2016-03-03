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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author dslomov
 */
public class NativeFileWatcherImpl extends PluggableFileWatcher {
  private static final Logger LOG = Logger.getInstance(NativeFileWatcherImpl.class);

  private static final String PROPERTY_WATCHER_DISABLED = "idea.filewatcher.disabled";
  private static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";
  private static final String ROOTS_COMMAND = "ROOTS";
  private static final String EXIT_COMMAND = "EXIT";
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;

  private ManagingFS myManagingFS;
  private FileWatcherNotificationSink myNotificationSink;
  private File myExecutable;

  private volatile MyProcessHandler myProcessHandler;
  private volatile int myStartAttemptCount = 0;
  private volatile boolean myIsShuttingDown = false;
  private final AtomicInteger mySettingRoots = new AtomicInteger(0);
  private volatile List<String> myRecursiveWatchRoots = Collections.emptyList();
  private volatile List<String> myFlatWatchRoots = Collections.emptyList();
  private final String[] myLastChangedPaths = new String[2];
  private int myLastChangedPathIndex;

  @Override
  public void initialize(@NotNull ManagingFS managingFS, @NotNull FileWatcherNotificationSink notificationSink) {
    myManagingFS = managingFS;
    myNotificationSink = notificationSink;

    boolean disabled = isDisabled();
    myExecutable = getExecutable();

    if (disabled) {
      LOG.info("Native file watcher is disabled");
    }
    else if (myExecutable == null) {
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

  @Override
  public void dispose() {
    myIsShuttingDown = true;
    shutdownProcess();
  }

  @Override
  public boolean isOperational() {
    return myProcessHandler != null;
  }

  @Override
  public boolean isSettingRoots() {
    return isOperational() && mySettingRoots.get() > 0;
  }

  @Override
  public void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat) {
    setWatchRoots(recursive, flat, false);
  }

  /**
   * Subclasses should override this method if they want to use custom logic to disable their file watcher.
   */
  protected boolean isDisabled() {
    return Boolean.parseBoolean(System.getProperty(PROPERTY_WATCHER_DISABLED));
  }

  /**
   * Subclasses should override this method to provide a custom binary to run.
   */
  @Nullable
  protected File getExecutable() {
    String execPath = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (execPath != null) return new File(execPath);

    String[] names = null;
    String prefix = null;
    if (SystemInfo.isWindows) {
      names = SystemInfo.is64Bit ? new String[]{"fsnotifier64.exe", "fsnotifier.exe"} : new String[]{"fsnotifier.exe"};
      prefix = "win";
    }
    else if (SystemInfo.isMac) {
      names = new String[]{"fsnotifier"};
      prefix = "mac";
    }
    else if (SystemInfo.isLinux) {
      String arch = "arm".equals(SystemInfo.OS_ARCH) ? "-arm" : "ppc".equals(SystemInfo.OS_ARCH) ? "-ppc" : "";
      String bits = SystemInfo.is64Bit ? "64" : "";
      names = new String[]{"fsnotifier" + arch + bits};
      prefix = "linux";
    }
    if (names == null) return null;

    String[] dirs = {PathManager.getBinPath(), PathManager.getHomePath() + "/community/bin/" + prefix, PathManager.getBinPath() + '/' + prefix};
    for (String dir : dirs) {
      for (String name : names) {
        File candidate = new File(dir, name);
        if (candidate.exists()) return candidate;
      }
    }

    return null;
  }

  /* internal stuff */

  private void notifyOnFailure(String cause, @Nullable NotificationListener listener) {
    myNotificationSink.notifyUserOnFailure(cause, listener);
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
    myProcessHandler = new MyProcessHandler(process, myExecutable.getName());
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
        boolean killProcess = true;
        try {
          writeLine(EXIT_COMMAND);
          killProcess = !processHandler.waitFor(500);
          if (killProcess) {
            LOG.warn("File watcher is still alive. Doing a force quit.");
          }
        }
        catch (IOException ignore) { }
        if (killProcess) {
          processHandler.destroyProcess();
        }
      }

      myProcessHandler = null;
    }
  }

  private void setWatchRoots(List<String> recursive, List<String> flat, boolean restart) {
    if (myProcessHandler == null || myProcessHandler.isProcessTerminated()) return;

    if (ApplicationManager.getApplication().isDisposeInProgress()) {
      recursive = flat = Collections.emptyList();
    }

    if (!restart && myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) {
      return;
    }

    mySettingRoots.incrementAndGet();
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
    if (LOG.isTraceEnabled()) LOG.trace("<< " + line);
    MyProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      processHandler.writeLine(line);
    }
  }

  @Override
  public void resetChangedPaths() {
    synchronized (myLastChangedPaths) {
      myLastChangedPathIndex = 0;
      for (int i = 0; i < myLastChangedPaths.length; ++i) myLastChangedPaths[i] = null;
    }
  }

  private static class MyProcessHandler extends OSProcessHandler {
    private static final Charset CHARSET = SystemInfo.isWindows | SystemInfo.isMac ? CharsetToolkit.UTF8_CHARSET : null;

    private final BufferedWriter myWriter;

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private MyProcessHandler(@NotNull Process process, @NotNull String commandLine) {
      super(process, commandLine, CHARSET);
      myWriter = new BufferedWriter(writer(process.getOutputStream()));
    }

    private static OutputStreamWriter writer(OutputStream stream) {
      return CHARSET != null ? new OutputStreamWriter(stream, CHARSET) :  new OutputStreamWriter(stream);
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

  @SuppressWarnings("SpellCheckingInspection")
  private enum WatcherOp {
    GIVEUP, RESET, UNWATCHEABLE, REMAP, MESSAGE, CREATE, DELETE, STATS, CHANGE, DIRTY, RECDIRTY
  }

  private class MyProcessAdapter extends ProcessAdapter {
    private WatcherOp myLastOp = null;
    private final List<String> myLines = ContainerUtil.newArrayList();

    @Override
    public void processTerminated(ProcessEvent event) {
      String message = "Watcher terminated with exit code " + event.getExitCode();
      if (myIsShuttingDown) LOG.info(message); else LOG.warn(message);

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

      String line = event.getText().trim();
      if (LOG.isTraceEnabled()) LOG.trace(">> " + line);

      if (myLastOp == null) {
        final WatcherOp watcherOp;
        try {
          watcherOp = WatcherOp.valueOf(line);
        }
        catch (IllegalArgumentException e) {
          LOG.error("Illegal watcher command: " + line);
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
        String path = StringUtil.trimEnd(line.replace('\0', '\n'), File.separator);  // unescape
        processChange(path, myLastOp);
        myLastOp = null;
      }
    }

    private void reset() {
      for (VirtualFile root : myManagingFS.getLocalRoots()) {
        myNotificationSink.notifyDirtyPathRecursive(root.getPresentableUrl());
      }
    }

    private void processRemap() {
      Set<Pair<String, String>> pairs = ContainerUtil.newHashSet();
      for (int i = 0; i < myLines.size() - 1; i += 2) {
        pairs.add(Pair.create(myLines.get(i), myLines.get(i + 1)));
      }
      myNotificationSink.notifyMapping(pairs);
    }

    private void processUnwatchable() {
      myNotificationSink.notifyManualWatchRoots(myLines);
    }

    private void processChange(String path, WatcherOp op) {
      if (SystemInfo.isWindows && op == WatcherOp.RECDIRTY) {
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
        if (root != null) {
          myNotificationSink.notifyDirtyPathRecursive(path);
        }
        return;
      }

      if ((op == WatcherOp.CHANGE || op == WatcherOp.STATS) && isRepetition(path)) {
        if (LOG.isTraceEnabled()) LOG.trace("repetition: " + path);
        return;
      }

      if (SystemInfo.isMac) {
        path = Normalizer.normalize(path, Normalizer.Form.NFC);
      }

      switch (op) {
        case STATS:
        case CHANGE:
          myNotificationSink.notifyDirtyPath(path);
          break;

        case CREATE:
        case DELETE:
          myNotificationSink.notifyPathCreatedOrDeleted(path);
          break;

        case DIRTY:
          myNotificationSink.notifyDirtyDirectory(path);
          break;

        case RECDIRTY:
          myNotificationSink.notifyDirtyPathRecursive(path);
          break;

        default:
          LOG.error("Unexpected op: " + op);
      }
    }
  }

  private boolean isRepetition(String path) {
    // collapse subsequent change file change notifications that happen once we copy large file,
    // this allows reduction of path checks at least 20% for Windows
    synchronized (myLastChangedPaths) {
      for (int i = 0; i < myLastChangedPaths.length; ++i) {
        int last = myLastChangedPathIndex - i - 1;
        if (last < 0) last += myLastChangedPaths.length;
        String lastChangedPath = myLastChangedPaths[last];
        if (lastChangedPath != null && lastChangedPath.equals(path)) {
          return true;
        }
      }

      myLastChangedPaths[myLastChangedPathIndex++] = path;
      if (myLastChangedPathIndex == myLastChangedPaths.length) myLastChangedPathIndex = 0;
    }

    return false;
  }

  @Override
  @TestOnly
  public void startup() throws IOException {
    Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    myIsShuttingDown = false;
    myStartAttemptCount = 0;
    startupProcess(false);
  }

  @Override
  @TestOnly
  public void shutdown() throws InterruptedException {
    Application app = ApplicationManager.getApplication();
    assert app != null && app.isUnitTestMode() : app;

    MyProcessHandler processHandler = myProcessHandler;
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