/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.watcher.ChangeKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileWatcher {
  @NonNls public static final String PROPERTY_WATCHER_DISABLED = "filewatcher.disabled";
  @NonNls private static final String PROPERTY_WATCHER_EXECUTABLE_PATH = "idea.filewatcher.executable.path";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.FileWatcher");

  @NonNls private static final String GIVEUP_COMMAND = "GIVEUP";
  @NonNls private static final String RESET_COMMAND = "RESET";
  @NonNls private static final String UNWATCHEABLE_COMMAND = "UNWATCHEABLE";
  @NonNls private static final String ROOTS_COMMAND = "ROOTS";
  @NonNls private static final String EXIT_COMMAND = "EXIT";

  private final Object LOCK = new Object();
  private List<String> myDirtyPaths = new ArrayList<String>();
  private List<String> myDirtyRecursivePaths = new ArrayList<String>();
  private List<String> myDirtyDirs = new ArrayList<String>();
  private List<String> myManualWatchRoots = new ArrayList<String>();

  private List<String> myRecursiveWatchRoots = new ArrayList<String>();
  private List<String> myFlatWatchRoots = new ArrayList<String>();

  private Process notifierProcess;
  private BufferedReader notifierReader;
  private BufferedWriter notifierWriter;

  private static final FileWatcher ourInstance = new FileWatcher();
  private int attemptCount = 0;
  private static final int MAX_PROCESS_LAUNCH_ATTEMPT_COUNT = 10;
  private boolean isShuttingDown = false;

  public static FileWatcher getInstance() {
    return ourInstance;
  }

  private FileWatcher() {
    try {
      if (!"true".equals(System.getProperty(PROPERTY_WATCHER_DISABLED))) {
        startupProcess();
      }
    }
    catch (IOException e) {
      // Ignore
    }

    if (notifierProcess != null) {
      LOG.info("Native file watcher is operational.");
      new WatchForChangesThread().start();

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        public void run() {
          isShuttingDown = true;
          shutdownProcess();
        }
      }, "FileWatcher shutdown hook"));
    }
    else {
      LOG.info("Native file watcher failed to startup.");
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

  public void setWatchRoots(List<String> recursive, List<String> flat) {
    synchronized (LOCK) {
      try {
        if (myRecursiveWatchRoots.equals(recursive) && myFlatWatchRoots.equals(flat)) return;

        myRecursiveWatchRoots = recursive;
        myFlatWatchRoots = flat;

        if (isAlive()) {
          writeLine(ROOTS_COMMAND);
          for (String path : recursive) {
            writeLine(path);
          }
          for (String path : flat) {
            writeLine("|" + path);
          }
          writeLine("#");
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private boolean isAlive() {
    if (!isOperational()) return false;

    try {
      notifierProcess.exitValue();
    }
    catch (IllegalThreadStateException e) {
      return true;
    }

    return false;
  }

  private void setManualWatchRoots(List<String> roots) {
    synchronized (LOCK) {
      myManualWatchRoots = roots;
    }
  }

  private void startupProcess() throws IOException {
    if (isShuttingDown) return;

    if (attemptCount++ > MAX_PROCESS_LAUNCH_ATTEMPT_COUNT) {
      throw new IOException("Can't launch process anymore");
    }

    shutdownProcess();

    @NonNls final String executableName = SystemInfo.isWindows ? "fsnotifier.exe" : "fsnotifier";

    String alternatePathToFilewatcherExecutable = System.getProperty(PROPERTY_WATCHER_EXECUTABLE_PATH);
    if (alternatePathToFilewatcherExecutable != null) {
      if (!new File(alternatePathToFilewatcherExecutable).exists()) {
        alternatePathToFilewatcherExecutable = null;
      }
    }
    final String pathToExecutable = alternatePathToFilewatcherExecutable != null? FileUtil.toSystemDependentName(alternatePathToFilewatcherExecutable) : PathManager.getBinPath() + File.separatorChar + executableName;
    notifierProcess = Runtime.getRuntime().exec(new String[]{pathToExecutable});
    
    notifierReader = new BufferedReader(new InputStreamReader(notifierProcess.getInputStream()));
    notifierWriter = new BufferedWriter(new OutputStreamWriter(notifierProcess.getOutputStream()));
  }

  private void shutdownProcess() {
    if (notifierProcess != null) {
      if (isAlive()) {
        try {
          writeLine(EXIT_COMMAND);
        }
        catch (IOException e) {
          // Do nothing
        }
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

    public void run() {
      try {
        while (true) {
          if (ApplicationManager.getApplication().isDisposeInProgress() || notifierProcess == null || isShuttingDown) return;

          final String command = readLine();
          if (command == null) {
            // Unexpected process exit, relaunch attempt
            startupProcess();
            continue;
          }

          if (GIVEUP_COMMAND.equals(command)) {
            LOG.info("Filewatcher gives up to operate on this platform");
            shutdownProcess();
            return;
          }
          
          if (RESET_COMMAND.equals(command)) {
            reset();
          }
          else if (UNWATCHEABLE_COMMAND.equals(command)) {
            List<String> roots = new ArrayList<String>();
            do {
              final String path = readLine();
              if (path == null || "#".equals(path)) break;
              roots.add(path);
            }
            while (true);

            setManualWatchRoots(roots);
          }
          else {
            String path = readLine();
            if (path == null) {
              // Unexpected process exit, relaunch attempt
              startupProcess();
              continue;
            }

            if (isWatcheable(path)) {
              try {
                onPathChange(ChangeKind.valueOf(command), path);
              }
              catch (IllegalArgumentException e) {
                LOG.error("Illegal watcher command: " + command);
              }
            }
            else if (LOG.isDebugEnabled()) {
              LOG.debug("not watcheable, filtered: " + path);
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

  private void writeLine(String line) throws IOException {
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("to fsnotifier: " + line);
      }
      notifierWriter.write(line);
      notifierWriter.newLine();
      notifierWriter.flush();
    }
    catch (IOException e) {
      try {
        notifierProcess.exitValue();
      }
      catch (IllegalThreadStateException e1) {
        throw e;
      }

      notifierProcess = null;
      notifierWriter = null;
      notifierReader = null;
    }
  }

  @Nullable
  private String readLine() throws IOException {
    if (notifierReader == null) return null;

    final String line = notifierReader.readLine();
    if (LOG.isDebugEnabled()) {
      LOG.debug("fsnotifier says: " + line);
    }
    return line;
  }

  private boolean isWatcheable(final String path) {
    if (path == null) return false;

    synchronized (LOCK) {
      for (String root : myRecursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) return true;
      }

      for (String root : myFlatWatchRoots) {
        if (FileUtil.pathsEqual(path, root)) return true;
        final File parentFile = new File(path).getParentFile();
        if (parentFile != null && FileUtil.pathsEqual(parentFile.getPath(), root)) return true;
      }
    }

    return false;
  }

  private void onPathChange(final ChangeKind changeKind, final String path) {
    synchronized (LOCK) {
      switch (changeKind) {
        case STATS:
        case CHANGE:
          myDirtyPaths.add(path);
          break;

        case CREATE:
        case DELETE:
          final File parentFile = new File(path).getParentFile();
          if (parentFile != null) {
            myDirtyPaths.add(parentFile.getPath());
          }
          else {
            myDirtyPaths.add(path);
          }
          break;

        case DIRTY:
          myDirtyDirs.add(path);
          break;

        case RECDIRTY:
          myDirtyRecursivePaths.add(path);
          break;

        case RESET:
          reset();
          break;
      }
    }
  }

  private void reset() {
    final ManagingFS fs = ManagingFS.getInstance();
    synchronized (LOCK) {
      myDirtyPaths.clear();
      myDirtyDirs.clear();
      myDirtyRecursivePaths.clear();

      for (VirtualFile root : fs.getLocalRoots()) {
        ((NewVirtualFile)root).markDirtyRecursively();
      }
    }
  }
}
