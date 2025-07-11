// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>WinShellIntegration class provides features allow to integrate you application into Windows Shell.
 * It has asynchronous interface because most of the methods should be invoked strictly inside the internal thread.</p>
 *
 * <p>Typical usage is something like the following:<pre>
 *   if (WinShellIntegration.isAvailable) {
 *     final WinShellIntegration wsi = WinShellIntegration.getInstance();
 *     final Future<> future = wsi.postShellTask((final WinShellIntegration.ShellContext ctx) -> {
 *       ctx.someMethod1();
 *       ctx.someMethod2();
 *     });
 *   }
 * </pre></p>
 *
 * @author Nikita Provotorov
 */
@Service(Service.Level.APP)
final class WinShellIntegration implements Disposable {
  public static final class ShellContext {
    public void clearRecentTasksList() {
      parent.clearRecentTasksList();
    }

    public void setRecentTasksList(@NotNull JumpTask @NotNull [] recentTasks) {
      parent.setRecentTasksList(recentTasks);
    }

    private ShellContext(@NotNull WinShellIntegration parent) {
      this.parent = parent;
    }

    private WinShellIntegration parent;
  }

  public interface ShellTask<R> {
    R run(@NotNull ShellContext ctx);
  }

  public interface VoidShellTask {
    void run(@NotNull ShellContext ctx);
  }

  /**
   * Indicates the features provided by this class are available to use.
   * If false then {@link #getInstance} will return null always.
   */
  public static final boolean isAvailable =
    OS.CURRENT == OS.Windows &&
    Boolean.getBoolean("ide.native.launcher") &&
    !Boolean.getBoolean("ide.win.shell.integration.disabled");

  /**
   * @return null if !{@link #isAvailable}
   */
  public static @Nullable WinShellIntegration getInstance() {
    return isAvailable ? ApplicationManager.getApplication().getService(WinShellIntegration.class) : null;
  }

  public <R> @NotNull Future<R> postShellTask(final @NotNull ShellTask<? extends R> shellTask) {
    final ShellContext ctx = new ShellContext(this);

    return bridge.comExecutor.submit(() -> {
      try {
        return shellTask.run(ctx);
      }
      finally {
        // ensure a ShellContext instance will not be used outside the comExecutor's thread
        ctx.parent = null;
      }
    });
  }

  public @NotNull Future<?> postShellTask(final @NotNull VoidShellTask shellTask) {
    final ShellContext ctx = new ShellContext(this);

    return bridge.comExecutor.submit(() -> {
      try {
        shellTask.run(ctx);
      }
      finally {
        // ensure a ShellContext instance will not be used outside the comExecutor's thread
        ctx.parent = null;
      }
    });
  }

  /**
   * Please use {@link #getInstance}
   */
  private WinShellIntegration() {
    if (!isAvailable) {
      throw new AssertionError("Feature is not available");
    }

    bridge = new Bridge();
  }

  @ApiStatus.Internal
  @Override
  public void dispose() {
    bridge.comExecutor.shutdown();
  }

  private void clearRecentTasksList() {
    bridge.ensureNativeIsInitialized();
    bridge.clearRecentTasksListNative();
  }

  private void setRecentTasksList(@NotNull JumpTask @NotNull [] recentTasks) {
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

    // this is the only native method does not require native is to be initialized
    private native void setAppUserModelIdNative(@NotNull String appUserModelId);

    private native void initializeNative();
    private native void clearRecentTasksListNative();
    private native void setRecentTasksListNative(@NotNull JumpTask @NotNull [] recentTasks);

    static {
      Path lib = PathManager.findBinFile("WinShellIntegrationBridge.dll");
      assert lib != null : "Shell Integration lib missing; bin=" + NioFiles.list(Path.of(PathManager.getBinPath()));
      System.load(lib.toString());
    }
  }

  private final @NotNull Bridge bridge;
}
