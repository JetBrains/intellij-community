// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.loader.NativeLibraryLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
@Service
final class WinShellIntegration implements Disposable {

  public static final class ShellContext {
    public void clearRecentTasksList() {
      parent.clearRecentTasksList();
    }

    public void setRecentTasksList(@NotNull Task @NotNull [] recentTasks) {
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
  public static final boolean isAvailable;

  /**
   * @returns null if !{@link #isAvailable}
   */
  @Nullable
  public static WinShellIntegration getInstance() {
    return isAvailable ? ServiceManager.getService(WinShellIntegration.class) : null;
  }


  public <R> @NotNull Future<R> postShellTask(@NotNull final ShellTask<? extends R> shellTask) {
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

  public @NotNull Future<?> postShellTask(@NotNull final VoidShellTask shellTask) {
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

  private void setRecentTasksList(@NotNull Task @NotNull [] recentTasks) {
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
    native private void setAppUserModelIdNative(@NotNull String appUserModelId);

    native private void initializeNative();
    native private void clearRecentTasksListNative();
    native private void setRecentTasksListNative(@NotNull Task @NotNull [] recentTasks);

    static {
      NativeLibraryLoader.loadPlatformLibrary("WinShellIntegrationBridge");
    }
  }


  private final @NotNull Bridge bridge;

  static {
    final boolean ideIsLaunchedViaDLL = Boolean.getBoolean("ide.native.launcher");

    isAvailable = SystemInfo.isWin8OrNewer
                  && ideIsLaunchedViaDLL
                  && !Boolean.getBoolean("ide.win.shell.integration.disabled");
  }
}
