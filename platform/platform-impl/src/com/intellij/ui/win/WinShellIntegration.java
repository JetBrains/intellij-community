// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.util.loader.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;


/**
 * @author Denis Fokin
 * @author Nikita Provotorov
 */
public final class WinShellIntegration {

  public static void clearRecentTasksList()
  {
    ensureAll();
    clearRecentTasksListNative();
  }

  /**
   * Use clearRecentTasksList method instead of passing empty array of tasks.
   */
  public static void setRecentTasksList(final @NotNull Task @NotNull[] recentTasks)
  {
    ensureAll();
    setRecentTasksListNative(recentTasks);
  }


  private static void ensureAll()
  {
    ensureLoaderThread();
    ensureInitialized();
  }

  /**
   * All invocations should be made from the same thread.
   */
  private static void ensureLoaderThread()
  {
    final Thread thread = Thread.currentThread();
    if (!thread.equals(loaderThread.get())) {
      throw new RuntimeException("Current thread is '" + thread.getName() + "'; this class should be used from '" + loaderThread + "'");
    }
  }

  private static void ensureInitialized()
  {
    // The double-check idiom
    if (!initialized)
      initialize();
  }

  /**
   * Initialization should be invoked once per process.
   */
  private synchronized static void initialize()
  {
    if (initialized)
      return;

    // TODO: obtain value from win32AppUserModelId configuration variable
    final String appUserModelId = "JetBrains.IntelliJIDEA.2020.3";

    initializeNative(appUserModelId);

    initialized = true;
  }


  native private static void initializeNative(@Nullable String appUserModelId);
  native private static void clearRecentTasksListNative();
  native private static void setRecentTasksListNative(@NotNull Task @NotNull[] recentTasks);


  private static boolean initialized = false;
  private static final WeakReference<Thread> loaderThread;
  private static final String loaderThreadName;

  static {
    Thread thread = Thread.currentThread();
    loaderThread = new WeakReference<>(thread);
    loaderThreadName = thread.getName();
    NativeLibraryLoader.loadPlatformLibrary("winshellintegrationbridge");
  }
}
