// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class NativeFileWatcherImpl extends PluggableFileWatcher {
  private static final Logger LOG = Logger.getInstance(NativeFileWatcherImpl.class);

  private static final String PROPERTY_WATCHER_DISABLED = "idea.filewatcher.disabled";
  private static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";
  private static final String ROOTS_COMMAND = "ROOTS";
  private static final String EXIT_COMMAND = "EXIT";
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;

  private FileWatcherNotificationSink myNotificationSink;
  private Path myExecutable;

  private volatile MyProcessHandler myProcessHandler;
  private final AtomicInteger myStartAttemptCount = new AtomicInteger(0);
  private volatile boolean myIsShuttingDown;
  private final AtomicInteger mySettingRoots = new AtomicInteger(0);
  private volatile List<String> myRecursiveWatchRoots = Collections.emptyList();
  private volatile List<String> myFlatWatchRoots = Collections.emptyList();
  private volatile List<String> myIgnoredRoots = Collections.emptyList();
  private final String[] myLastChangedPaths = new String[2];
  private int myLastChangedPathIndex;

  @Override
  public void initialize(@NotNull ManagingFS managingFS, @NotNull FileWatcherNotificationSink notificationSink) {
    myNotificationSink = notificationSink;

    boolean disabled = isDisabled();
    myExecutable = getExecutable();

    if (disabled) {
      LOG.info("Native file watcher is disabled");
    }
    else if (myExecutable == null) {
      if (SystemInfo.isWindows || SystemInfo.isMac || SystemInfo.isLinux && CpuArch.isIntel64()) {
        notifyOnFailure(IdeCoreBundle.message("watcher.exe.not.found"), null);
      }
      else if (SystemInfo.isLinux) {
        notifyOnFailure(IdeCoreBundle.message("watcher.exe.compile"), NotificationListener.URL_OPENING_LISTENER);
      }
      else {
        notifyOnFailure(IdeCoreBundle.message("watcher.exe.not.exists"), null);
      }
    }
    else if (!Files.isExecutable(myExecutable)) {
      String message = IdeCoreBundle.message("watcher.exe.not.exe", myExecutable);
      notifyOnFailure(message, (notification, event) -> IdeUiService.getInstance().revealFile(myExecutable));
    }
    else {
      try {
        startupProcess(false);
        LOG.info("Native file watcher is operational.");
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
        notifyOnFailure(IdeCoreBundle.message("watcher.failed.to.start"), null);
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
    if (Boolean.getBoolean(PROPERTY_WATCHER_DISABLED)) return true;
    Application app = ApplicationManager.getApplication();
    return app.isCommandLine() || app.isUnitTestMode();
  }

  /**
   * Subclasses should override this method to provide a custom binary to run.
   */
  protected @Nullable Path getExecutable() {
    return getFSNotifierExecutable();
  }

  public static @Nullable Path getFSNotifierExecutable() {
    String customPath = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (customPath != null) {
      Path customFile = PathManager.findBinFile(customPath);
      return customFile == null ? Path.of(customPath) : customFile;
    }

    String name = null;
    if (SystemInfo.isWindows) {
      name = "fsnotifier.exe";
    }
    else if (SystemInfo.isMac) {
      name = "fsnotifier";
    }
    else if (SystemInfo.isLinux && CpuArch.isIntel64()) {
      name = "fsnotifier";
    }
    if (name != null) {
      Path file = PathManager.findBinFile(name);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  /* internal stuff */

  private void notifyOnFailure(@NlsContexts.NotificationContent String cause, @Nullable NotificationListener listener) {
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

    if (myStartAttemptCount.incrementAndGet() > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      notifyOnFailure(IdeCoreBundle.message("watcher.bailed.out.10x"), null);
      return;
    }

    if (restart) {
      shutdownProcess();
    }

    LOG.info("Starting file watcher: " + myExecutable);
    Process process = new ProcessBuilder(myExecutable.toAbsolutePath().toString()).start();
    myProcessHandler = new MyProcessHandler(process, myExecutable.getFileName().toString());
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
    OSProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      if (!processHandler.isProcessTerminated()) {
        try { writeLine(EXIT_COMMAND); }
        catch (IOException ignore) { }
        if (!processHandler.waitFor(10)) {
          Runnable r = () -> {
            if (!processHandler.waitFor(500)) {
              LOG.warn("File watcher is still alive, doing a force quit.");
              processHandler.destroyProcess();
            }
          };
          if (myIsShuttingDown) {
            new Thread(r, "fsnotifier shutdown").start();
          }
          else {
            ApplicationManager.getApplication().executeOnPooledThread(r);
          }
        }
      }

      myProcessHandler = null;
    }
  }

  private void setWatchRoots(List<String> recursive, List<String> flat, boolean restart) {
    if (myProcessHandler == null || myProcessHandler.isProcessTerminated()) return;

    if (ApplicationManager.getApplication().isDisposed()) {
      recursive = flat = Collections.emptyList();
    }

    if (!restart && myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) {
      myNotificationSink.notifyManualWatchRoots(this, myIgnoredRoots);
      return;
    }

    mySettingRoots.incrementAndGet();
    myRecursiveWatchRoots = recursive;
    myFlatWatchRoots = flat;

    List<String> ignored = new SmartList<>();
    if (SystemInfo.isWindows) {
      recursive = screenUncRoots(recursive, ignored);
      flat = screenUncRoots(flat, ignored);
    }
    myIgnoredRoots = new CopyOnWriteArrayList<>(ignored);
    myNotificationSink.notifyManualWatchRoots(this, ignored);

    try {
      writeLine(ROOTS_COMMAND);
      for (String path : recursive) writeLine(path);
      for (String path : flat) writeLine('|' + path);
      writeLine("#");
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static List<String> screenUncRoots(List<String> roots, List<String> ignored) {
    List<String> filtered = null;
    for (int i = 0; i < roots.size(); i++) {
      String root = roots.get(i);
      if (OSAgnosticPathUtil.isUncPath(root)) {
        if (filtered == null) {
          filtered = new ArrayList<>(roots.subList(0, i));
        }
        ignored.add(root);
      }
      else if (filtered != null) {
        filtered.add(root);
      }
    }
    return filtered != null ? filtered : roots;
  }

  private void writeLine(String line) throws IOException {
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
      Arrays.fill(myLastChangedPaths, null);
    }
  }

  private static final Charset CHARSET =
    SystemInfo.isWindows || SystemInfo.isMac ? StandardCharsets.UTF_8 : CharsetToolkit.getPlatformCharset();

  private static final BaseOutputReader.Options READER_OPTIONS = new BaseOutputReader.Options() {
    @Override public BaseDataReader.SleepingPolicy policy() { return BaseDataReader.SleepingPolicy.BLOCKING; }
    @Override public boolean sendIncompleteLines() { return false; }
    @Override public boolean withSeparators() { return false; }
  };

  @SuppressWarnings("SpellCheckingInspection")
  private enum WatcherOp { GIVEUP, RESET, UNWATCHEABLE, REMAP, MESSAGE, CREATE, DELETE, STATS, CHANGE, DIRTY, RECDIRTY }

  private final class MyProcessHandler extends OSProcessHandler {
    private final BufferedWriter myWriter;
    private WatcherOp myLastOp;
    private final List<String> myLines = new ArrayList<>();

    MyProcessHandler(Process process, String commandLine) {
      super(process, commandLine, CHARSET);
      myWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), CHARSET));
    }

    void writeLine(String line) throws IOException {
      myWriter.write(line);
      myWriter.newLine();
      myWriter.flush();
    }

    @Override
    protected @NotNull BaseOutputReader.Options readerOptions() {
      return READER_OPTIONS;
    }

    @Override
    protected void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);

      String message = "Watcher terminated with exit code " + exitCode;
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
    public void notifyTextAvailable(@NotNull String line, @NotNull Key outputType) {
      if (outputType == ProcessOutputTypes.STDERR) {
        LOG.warn(line);
      }
      if (outputType != ProcessOutputTypes.STDOUT) {
        return;
      }

      if (LOG.isTraceEnabled()) LOG.trace(">> " + line);

      if (myLastOp == null) {
        WatcherOp watcherOp;
        try {
          watcherOp = WatcherOp.valueOf(line);
        }
        catch (IllegalArgumentException e) {
          String message = "Illegal watcher command: '" + line + "'";
          if (line.length() <= 20) message += " " + Arrays.toString(line.chars().toArray());
          LOG.error(message);
          return;
        }

        if (watcherOp == WatcherOp.GIVEUP) {
          notifyOnFailure(IdeCoreBundle.message("watcher.gave.up"), null);
          myIsShuttingDown = true;
        }
        else if (watcherOp == WatcherOp.RESET) {
          myNotificationSink.notifyReset(null);
        }
        else {
          myLastOp = watcherOp;
        }
      }
      else if (myLastOp == WatcherOp.MESSAGE) {
        String localized = Objects.requireNonNullElse(IdeCoreBundle.INSTANCE.messageOrNull(line), line); //NON-NLS
        LOG.warn(localized);
        notifyOnFailure(localized, NotificationListener.URL_OPENING_LISTENER);
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

    private void processRemap() {
      Set<Pair<String, String>> pairs = new HashSet<>();
      for (int i = 0; i < myLines.size() - 1; i += 2) {
        pairs.add(Pair.create(myLines.get(i), myLines.get(i + 1)));
      }
      myNotificationSink.notifyMapping(pairs);
    }

    private void processUnwatchable() {
      myIgnoredRoots.addAll(myLines);
      myNotificationSink.notifyManualWatchRoots(NativeFileWatcherImpl.this, myLines);
    }

    private void processChange(String path, WatcherOp op) {
      if (SystemInfo.isWindows && op == WatcherOp.RECDIRTY) {
        myNotificationSink.notifyReset(path);
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

  protected boolean isRepetition(String path) {
    // collapse subsequent change file change notifications that happen once we copy a large file,
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

  //<editor-fold desc="Test stuff.">
  @Override
  @TestOnly
  public void startup() throws IOException {
    Application app = ApplicationManager.getApplication();
    if (app == null || !app.isUnitTestMode()) throw new IllegalStateException();

    myIsShuttingDown = false;
    myStartAttemptCount.set(0);
    startupProcess(false);
  }

  @Override
  @TestOnly
  public void shutdown() throws InterruptedException {
    Application app = ApplicationManager.getApplication();
    if (app == null || !app.isUnitTestMode()) throw new IllegalStateException();

    MyProcessHandler processHandler = myProcessHandler;
    if (processHandler != null) {
      myIsShuttingDown = true;
      shutdownProcess();

      long t = System.currentTimeMillis();
      while (!processHandler.isProcessTerminated()) {
        if (System.currentTimeMillis() - t > 15000) {
          throw new InterruptedException("Timed out waiting watcher process to terminate");
        }
        TimeoutUtil.sleep(100);
      }
    }
  }
  //</editor-fold>
}
