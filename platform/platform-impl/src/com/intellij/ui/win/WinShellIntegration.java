// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.loader.NativeLibraryLoader;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public final class WinShellIntegration implements Disposable {

  public static final class ShellContext {
    public void clearRecentTasksList() {
      parent.clearRecentTasksList();
    }

    public void setRecentTasksList(@NotNull Task @NotNull [] recentTasks) {
      parent.setRecentTasksList(recentTasks);
    }

    /**
     * Finds among passed Shell links ({@code shellLinksPaths}) a Shell link pointing to {@code shellLinkTargetPath}
     *  and if such Shell link found, sets the value of its AppUserModelID property to {@code newAppUserModelId}
     *
     * @param shellLinkTargetPath path to a file to which the searched Shell link must be resolved.
     * @param newAppUserModelId new value of AppUserModelID property; if set to null then this property will contain no value.
     * @param shellLinksPaths paths to Shell links (.lnk files) among which this method will search a Shell link pointing to {@code shellLinkTargetPath}.
     * @returns path to the Shell link which value of AppUserModelID property has been updated;
     *          null otherwise (if {@code shellLinksPaths} contains no Shell links pointing to {@code shellLinkTargetPath}}.
     */
    public String findAndPatchShellLink(@NotNull String shellLinkTargetPath,
                                        @Nullable String newAppUserModelId,
                                        @NotNull String @NotNull ... shellLinksPaths) {
      return parent.findAndPatchShellLink(shellLinkTargetPath, shellLinksPaths, newAppUserModelId);
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


  public void updateAppUserModelId() {
    if (appUserModelIdProperty != null)
      bridge.setAppUserModelIdNative(appUserModelIdProperty);
  }


  public LauncherSystemShellLinksPatcher getLauncherSystemShellLinksPatcher() {
    return new LauncherSystemShellLinksPatcher(this);
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

    log = Logger.getInstance(WinShellIntegration.class);
    bridge = new Bridge();
    //noinspection SpellCheckingInspection
    thisIdeCacheDirPath = Paths.get(PathManager.getSystemPath(), "winshellintegration");
  }

  @ApiStatus.Internal
  @Override
  public void dispose() {
    bridge.comExecutor.shutdown();
  }


  /** @returns null if the caching should be disabled */
  @Nullable WinShellIntegrationCache obtainCache() {
    try {
      return WinShellIntegrationCache.loadFromStorage(thisIdeCacheDirPath);
    }
    catch (JDOMException e) {
      log.warn(e);
      return WinShellIntegrationCache.createEmpty();
    }
    catch (FileNotFoundException ignored) {
      return WinShellIntegrationCache.createEmpty();
    }
    catch (IOException e) {
      log.warn(e);
      return null;
    }
  }

  void saveCacheToStorage(@NotNull WinShellIntegrationCache cache) {
    try {
      if (!Files.exists(thisIdeCacheDirPath, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(thisIdeCacheDirPath);

      cache.saveToStorage(thisIdeCacheDirPath);
    }
    catch (IOException e) {
      log.warn(e);
    }
  }


  private void clearRecentTasksList() {
    bridge.ensureNativeIsInitialized();
    bridge.clearRecentTasksListNative();
  }

  private void setRecentTasksList(@NotNull Task @NotNull [] recentTasks) {
    bridge.ensureNativeIsInitialized();
    bridge.setRecentTasksListNative(recentTasks);
  }

  private String findAndPatchShellLink(@NotNull String targetPath,
                                       @NotNull String @NotNull [] shellLinksPaths,
                                       @Nullable String newAppUserModelId) {
    if (shellLinksPaths.length < 1) {
      return null;
    }

    bridge.ensureNativeIsInitialized();
    return bridge.findAndPatchShellLinkNative(targetPath, shellLinksPaths, newAppUserModelId);
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
    native private String findAndPatchShellLinkNative(@NotNull String targetPath,
                                                      @NotNull String @NotNull [] shellLinksPaths,
                                                      @Nullable String newAppUserModelId);

    static {
      //noinspection SpellCheckingInspection
      NativeLibraryLoader.loadPlatformLibrary("winshellintegrationbridge");
    }
  }


  @SuppressWarnings("NonConstantLogger")
  private final @NotNull Logger log;
  private final @NotNull Bridge bridge;
  private final @NotNull Path thisIdeCacheDirPath;


  static final String appUserModelIdProperty;

  static {
    final var appInfo = ApplicationInfoEx.getInstanceEx();
    final var app = ApplicationManagerEx.getApplicationEx();

    final boolean shouldBeDisabled = app.isHeadlessEnvironment() || app.isLightEditMode();

    appUserModelIdProperty = appInfo.getWin32AppUserModelId();
    isAvailable = SystemInfo.isWin8OrNewer && !shouldBeDisabled && !StringUtilRt.isEmptyOrSpaces(appUserModelIdProperty);
  }
}
