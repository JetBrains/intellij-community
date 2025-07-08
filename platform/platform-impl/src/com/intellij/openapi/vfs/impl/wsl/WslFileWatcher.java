// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.wsl;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class WslFileWatcher extends PluggableFileWatcher {
  private static Logger logger(@Nullable String vm) {
    return vm == null ? Logger.getInstance(WslFileWatcher.class) : Logger.getInstance('#' + WslFileWatcher.class.getName() + '.' + vm);
  }

  private static final String FSNOTIFIER_WSL = "fsnotifier-wsl";
  private static final String ROOTS_COMMAND = "ROOTS";
  private static final String EXIT_COMMAND = "EXIT";
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;
  private static final int EXIT_TIMEOUT_MS = 500;

  private FileWatcherNotificationSink myNotificationSink;
  private Path myExecutable;
  private final Map<String, VmData> myVMs = new ConcurrentHashMap<>();
  private final AtomicInteger mySettingRoots = new AtomicInteger(0);
  private volatile boolean myShuttingDown = false;
  private volatile boolean myTestStarted = false;

  @Override
  public void initialize(@NotNull FileWatcherNotificationSink notificationSink) {
    if (Registry.is("use.eel.file.watcher", false)) {
      myExecutable = null;
      return;
    }

    myNotificationSink = notificationSink;
    if (SystemInfo.isWin10OrNewer && PathEnvironmentVariableUtil.findInPath("wsl.exe") != null) {
      myExecutable = PathManager.findBinFile(FSNOTIFIER_WSL);
      if (myExecutable != null) {
        logger(null).info("WSL file watcher: " + myExecutable);
      }
    }
  }

  private void notifyOnFailure(@NlsSafe String vm, @NlsContexts.NotificationContent String cause, @Nullable NotificationListener listener) {
    myNotificationSink.notifyUserOnFailure("[" + vm + "] " + cause, listener);
  }

  @Override
  public void dispose() {
    myShuttingDown = true;
    for (Map.Entry<String, VmData> entry : myVMs.entrySet()) {
      shutdownProcess(entry.getValue(), true);
    }
  }

  @Override
  public boolean isOperational() {
    if (myExecutable == null || Registry.is("use.eel.file.watcher", false)) return false;
    var app = ApplicationManager.getApplication();
    return !(app.isCommandLine() || app.isUnitTestMode()) || myTestStarted;
  }

  @Override
  public boolean isSettingRoots() {
    return isOperational() && mySettingRoots.get() > 0;
  }

  @Override
  public void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat, boolean shuttingDown) {
    if (shuttingDown) {
      myShuttingDown = true;
      for (Map.Entry<String, VmData> entry : myVMs.entrySet()) {
        shutdownProcess(entry.getValue(), false);
      }
    }
    if (myShuttingDown) return;

    Map<String, VmData> newVMs = new HashMap<>();
    List<String> ignored = new ArrayList<>();
    sortRoots(recursive, newVMs, ignored, true);
    sortRoots(flat, newVMs, ignored, false);
    myNotificationSink.notifyManualWatchRoots(this, ignored);

    for (Map.Entry<String, VmData> entry : newVMs.entrySet()) {
      VmData upcoming = entry.getValue(), vm = myVMs.computeIfAbsent(entry.getKey(), k -> upcoming);
      assert vm != null : entry;
      if (vm == upcoming) {
        setupProcess(vm);
      }
      else if (!vm.recursive.equals(upcoming.recursive) || !vm.flat.equals(upcoming.flat)) {
        vm.reload(upcoming);
        setupProcess(vm);
      }
      else {
        myNotificationSink.notifyManualWatchRoots(this, vm.ignored);
      }
    }

    for (Iterator<Map.Entry<String, VmData>> iterator = myVMs.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, VmData> entry = iterator.next();
      if (!newVMs.containsKey(entry.getKey())) {
        iterator.remove();
        shutdownProcess(entry.getValue(), true);
      }
    }
  }

  private static void sortRoots(List<String> roots, Map<String, VmData> vms, List<? super String> ignored, boolean recursive) {
    for (String root : roots) {
      WslPath wslPath = WslPath.parseWindowsUncPath(root);
      if (wslPath != null) {
        VmData vm = vms.computeIfAbsent(wslPath.getDistributionId(), k -> new VmData(k, wslPath.getWslRoot()));
        (recursive ? vm.recursive : vm.flat).add(wslPath.getLinuxPath());
      }
      else {
        ignored.add(root);
      }
    }
  }

  private void setupProcess(VmData vm) {
    if (myShuttingDown || vm.shuttingDown) return;

    MyProcessHandler handler = vm.handler;
    if (handler == null) {
      if (vm.startAttemptCount.incrementAndGet() > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
        notifyOnFailure(vm.name, IdeCoreBundle.message("watcher.bailed.out.10x"), null);
        return;
      }

      try {
        Path toolName = myExecutable.getFileName(), toolDir = myExecutable.getParent();
        Process process = new ProcessBuilder("wsl", "-d", vm.name, "-e", "./" + toolName).directory(toolDir.toFile()).start();
        vm.handler = handler = new MyProcessHandler(process, vm);
        handler.startNotify();
      }
      catch (IOException e) {
        vm.logger.error(e);
        vm.startAttemptCount.set(MAX_PROCESS_LAUNCH_ATTEMPT_COUNT);
        notifyOnFailure(vm.name, IdeCoreBundle.message("watcher.failed.to.start"), null);
        return;
      }
    }

    mySettingRoots.incrementAndGet();

    try {
      handler.writeLine(ROOTS_COMMAND);
      for (String path : vm.recursive) handler.writeLine(path);
      for (String path : vm.flat) handler.writeLine('|' + path);
      handler.writeLine("#");
    }
    catch (IOException e) {
      vm.logger.error(e);
    }
  }

  private static void shutdownProcess(VmData vm, boolean await) {
    var processHandler = vm.handler;
    if (processHandler == null || processHandler.isProcessTerminated()) {
      vm.handler = null;
      return;
    }

    vm.shuttingDown = true;
    try { processHandler.writeLine(EXIT_COMMAND); }
    catch (IOException ignore) { }

    if (await) {
      var timeout = TimeUnit.MILLISECONDS.toNanos(EXIT_TIMEOUT_MS) + System.nanoTime();
      while (!processHandler.isProcessTerminated()) {
        if (System.nanoTime() > timeout) {
          vm.logger.warn("WSL file watcher is still alive, doing a force quit.");
          processHandler.destroyProcess();
          break;
        }
        processHandler.waitFor(10);
      }
      vm.handler = null;
    }
  }

  private static final class VmData {
    final String name;
    final String prefix;
    final List<String> recursive = new ArrayList<>();
    final List<String> flat = new ArrayList<>();
    final Logger logger;
    final AtomicInteger startAttemptCount = new AtomicInteger(0);
    volatile MyProcessHandler handler;
    volatile List<String> ignored = Collections.emptyList();
    volatile boolean shuttingDown;

    VmData(String name, String prefix) {
      this.name = name;
      this.prefix = prefix;
      this.logger = logger(name);
    }

    void reload(VmData other) {
      recursive.clear();
      recursive.addAll(other.recursive);
      flat.clear();
      flat.addAll(other.flat);
      ignored = Collections.emptyList();
    }
  }

  private static final BaseOutputReader.Options READER_OPTIONS = new BaseOutputReader.Options() {
    @Override public BaseDataReader.SleepingPolicy policy() { return BaseDataReader.SleepingPolicy.BLOCKING; }
    @Override public boolean sendIncompleteLines() { return false; }
    @Override public boolean withSeparators() { return false; }
  };

  @SuppressWarnings("SpellCheckingInspection")
  private enum WatcherOp {GIVEUP, RESET, UNWATCHEABLE, MESSAGE, CREATE, DELETE, STATS, CHANGE}

  private final class MyProcessHandler extends OSProcessHandler {
    private final BufferedWriter myWriter;
    private final VmData myVm;
    private WatcherOp myLastOp;
    private final List<String> myLines = new ArrayList<>();

    MyProcessHandler(Process process, VmData vm) {
      super(process, FSNOTIFIER_WSL + " @ " + vm.name, StandardCharsets.UTF_8);
      myWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
      myVm = vm;
    }

    void writeLine(String line) throws IOException {
      if (myVm.logger.isTraceEnabled()) myVm.logger.trace("<< " + line);
      myWriter.write(line);
      myWriter.write('\n');
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
      if (myShuttingDown || myVm.shuttingDown) myVm.logger.info(message); else myVm.logger.warn(message);

      myVm.handler = null;
      setupProcess(myVm);
    }

    @Override
    public void notifyTextAvailable(@NotNull String line, @NotNull Key outputType) {
      if (outputType == ProcessOutputTypes.STDERR) {
        myVm.logger.warn(line);
      }
      if (outputType != ProcessOutputTypes.STDOUT || myShuttingDown) {
        return;
      }

      if (myVm.logger.isTraceEnabled()) myVm.logger.trace(">> " + line);

      if (myLastOp == null) {
        WatcherOp watcherOp;
        try {
          watcherOp = WatcherOp.valueOf(line);
        }
        catch (IllegalArgumentException e) {
          // `wsl.exe` own error messages are coming in UTF16LE, accompanied by a couple of short tails (decoding artifacts)
          byte[] raw = line.getBytes(StandardCharsets.UTF_8);
          if (raw.length > 3 && raw[0] != 0 && raw[2] != 0 && raw[1] + raw[3] == 0) {
            myVm.logger.warn(new String(raw, StandardCharsets.UTF_16LE));
          }
          else if (!(raw.length == 1 && raw[0] == 0 || raw.length == 2 && raw[0] + raw[1] == 0)) {
            myVm.logger.error("Illegal watcher command: '" + line + "'", (Throwable)null);
          }
          return;
        }

        if (watcherOp == WatcherOp.GIVEUP) {
          notifyOnFailure(myVm.name, IdeCoreBundle.message("watcher.gave.up"), null);
        }
        else if (watcherOp == WatcherOp.RESET) {
          myNotificationSink.notifyReset(Strings.trimEnd(myVm.prefix, '\\'));
        }
        else {
          myLastOp = watcherOp;
        }
      }
      else if (myLastOp == WatcherOp.MESSAGE) {
        String localized = Objects.requireNonNullElse(IdeCoreBundle.INSTANCE.messageOrNull(line), line); //NON-NLS
        myVm.logger.warn(localized);
        notifyOnFailure(myVm.name, localized, NotificationListener.URL_OPENING_LISTENER);
        myLastOp = null;
      }
      else if (myLastOp == WatcherOp.UNWATCHEABLE) {
        if ("#".equals(line)) {
          mySettingRoots.decrementAndGet();
          processUnwatchable();
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

    private void processUnwatchable() {
      List<String> roots = new ArrayList<>(myLines.size());
      for (String line : myLines) roots.add(myVm.prefix + line.replace('/', '\\'));
      myVm.ignored = new CopyOnWriteArrayList<>(roots);
      myNotificationSink.notifyManualWatchRoots(WslFileWatcher.this, roots);
    }

    private void processChange(String path, WatcherOp op) {
      String root = myVm.prefix + path.replace('/', '\\');
      if (op == WatcherOp.STATS || op == WatcherOp.CHANGE) {
        myNotificationSink.notifyDirtyPath(root);
      }
      else if (op == WatcherOp.CREATE || op == WatcherOp.DELETE) {
        myNotificationSink.notifyPathCreatedOrDeleted(root);
      }
      else {
        myVm.logger.error("unexpected op: " + op);
      }
    }
  }

  //<editor-fold desc="Test stuff.">
  @Override
  @TestOnly
  public void startup() {
    Application app = ApplicationManager.getApplication();
    if (app == null || !app.isUnitTestMode()) throw new IllegalStateException();
    myTestStarted = true;
    myShuttingDown = false;
  }

  @Override
  @TestOnly
  public void shutdown() {
    Application app = ApplicationManager.getApplication();
    if (app == null || !app.isUnitTestMode()) throw new IllegalStateException();
    myTestStarted = false;
    myShuttingDown = true;
    myVMs.forEach((__, vm) -> shutdownProcess(vm, true));
    myVMs.clear();
  }
  //</editor-fold>
}
