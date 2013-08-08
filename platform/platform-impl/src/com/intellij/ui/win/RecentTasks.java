/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.win;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecentTasks {

  private static AtomicBoolean initialiazed =
    new AtomicBoolean(false);

  private final static WeakReference<Thread> openerThread =
    new WeakReference<Thread>(Thread.currentThread());

  static {
    final String libraryName = (System.getProperty("sun.arch.data.model").contains("64"))?
                               "jumplistbridge64.dll":
                               "jumplistbridge.dll";

    final String binPath = PathManager.getBinPath();
    final String communityBinPath = PathManager.getHomePath() + File.separatorChar + "community" + File.separatorChar + "bin";

    final String [] libraryPaths = {
      binPath,
      binPath + File.separatorChar + "win",
      communityBinPath,
      communityBinPath + File.separatorChar + "win",
    };

    for (String path : libraryPaths) {
      final File candidate = new File(path + File.separatorChar + libraryName);
      if (candidate.exists()) {
        System.load(candidate.getAbsolutePath());
        break;
      }
    }
  }

  private synchronized static void init() {
    if (initialiazed.get()) return;

    initialize("JetBrains.JetBrainsNativeAppID");
    initialiazed.set(true);
  }

  /**
   * Com initialization should be invoked once per process.
   * All invocation should be made from the same thread.
   * @param applicationId
   */
  native private static void initialize (String applicationId);
  native private static void addTaskNative (String location, String args, String description);
  native private static void clearNative();

  public synchronized static void clear() {
    init();
    checkThread();
    clearNative();
  }

  public synchronized static void addTask(File location, String args, String description) {
    init();
    checkThread();
    if (!location.exists()) throw new IllegalArgumentException("Task should be a valid path");
    addTaskNative(location.getAbsolutePath(), args, description);
  }

  private static void checkThread() {
    Thread t = openerThread.get();
    if (t == null || !t.equals(Thread.currentThread()))
      throw new RuntimeException("This class has to be used from the same thread");
  }
}

