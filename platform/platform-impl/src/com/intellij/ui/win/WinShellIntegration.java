// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/// WinShellIntegration class provides features allow integrating your application with the Windows Shell.
/// It has an asynchronous interface because most of the methods should be invoked strictly inside the internal thread.
///
/// Typical usage is something like the following:
///
/// ```
///   if (WinShellIntegration.isAvailable) {
///     WinShellIntegration wsi = WinShellIntegration.getInstance();
///     Future<> future = wsi.postShellTask((WinShellIntegration.ShellContext ctx) -> {
///       ctx.someMethod1();
///       ctx.someMethod2();
///     });
///   }
/// ```
@Service(Service.Level.APP)
final class WinShellIntegration implements Disposable {
  static final class ShellContext {
    void clearRecentTasksList() {
      parent.clearRecentTasksList();
    }

    @SuppressWarnings("SSBasedInspection")
    void setRecentTasksList(@NotNull JumpTask @NotNull [] recentTasks) {
      parent.setRecentTasksList(recentTasks);
    }

    private ShellContext(WinShellIntegration parent) {
      this.parent = parent;
    }

    private WinShellIntegration parent;
  }

  @FunctionalInterface
  interface ShellTask {
    void run(@NotNull ShellContext ctx);
  }

  /// Indicates the features provided by this class are available to use.
  /// If `false`, then [#getInstance] will return `null` always.
  static final boolean isAvailable =
    OS.CURRENT == OS.Windows && Boolean.getBoolean("ide.native.launcher") && !Boolean.getBoolean("ide.win.shell.integration.disabled");

  /// @return `null` if ![#isAvailable]
  static @Nullable WinShellIntegration getInstance() {
    return isAvailable ? ApplicationManager.getApplication().getService(WinShellIntegration.class) : null;
  }

  @NotNull Future<?> postShellTask(@NotNull ShellTask shellTask) {
    return bridge.comExecutor.submit(() -> {
      var ctx = new ShellContext(this);
      try {
        shellTask.run(ctx);
      }
      finally {
        // ensure a ShellContext instance will not be used outside the comExecutor's thread
        ctx.parent = null;
      }
    });
  }

  private WinShellIntegration() {
    if (!isAvailable) {
      throw new AssertionError("Feature is not available");
    }

    bridge = new Bridge();
  }

  @Override
  public void dispose() {
    bridge.comExecutor.shutdown();
  }

  private void clearRecentTasksList() {
    bridge.ensureNativeIsInitialized();
    bridge.clearRecentTasksListNative();
  }

  private void setRecentTasksList(JumpTask[] recentTasks) {
    bridge.ensureNativeIsInitialized();
    bridge.setRecentTasksListNative(recentTasks);
  }

  private static final class Bridge {
    private void ensureNativeIsInitialized() {
      if (nativeIsInitialized) {
        return;
      }

      initializeNative();

      nativeIsInitialized = true;
    }

    private final ThreadPoolExecutor comExecutor = ConcurrencyUtil.newSingleThreadExecutor("Windows Shell integration");
    private boolean nativeIsInitialized = false;

    private native void initializeNative();
    private native void clearRecentTasksListNative();
    private native void setRecentTasksListNative(JumpTask[] recentTasks);

    static {
      var lib = PathManager.findBinFile("WinShellIntegrationBridge.dll");
      assert lib != null : "Shell Integration lib missing; bin=" + NioFiles.list(PathManager.getBinDir());
      System.load(lib.toString());
    }
  }

  private final Bridge bridge;
}
