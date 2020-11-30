// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.loader.NativeLibraryLoader;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
     * Finds among passed shortcuts ({@code shortcutsPaths}) a shortcut pointing to {@code shortcutTargetPath}
     *  and if such shortcut found, sets the value of its AppUserModelID property to {@code newAppUserModelId}
     *
     * @param shortcutTargetPath path to a file to which the searched shortcut must be resolved.
     * @param newAppUserModelId new value of AppUserModelID property; if set to null then this property will contain no value.
     * @param shortcutsPaths paths to shortcuts (.lnk files) among which this method will search a shortcut pointing to {@code shortcutTargetPath}.
     * @returns path to the shortcut which value of AppUserModelID property has been updated;
     *          null otherwise (if {@code shortcutsPaths} contains no shortcuts pointing to {@code shortcutTargetPath}}.
     */
    public String findAndPatchShortcut(@NotNull String shortcutTargetPath,
                                       @Nullable String newAppUserModelId,
                                       @NotNull String @NotNull ... shortcutsPaths) {
      return parent.findAndPatchShortcut(shortcutTargetPath, shortcutsPaths, newAppUserModelId);
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


  // TODO: documentation
  public void patchSystemShortcuts() {
    try {
      final @NotNull String launcherExe = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName())
                                          + (SystemInfo.is64Bit ? "64" : "")
                                          + ".exe";
      final @NotNull Path launcherPath = Paths.get(PathManager.getBinPath(), launcherExe).normalize();

      final var cache = obtainCache();
      if (cache == null) {
        patchSystemShortcutsNoCache(launcherPath);
      } else {
        patchSystemShortcutsCached(cache, launcherPath);
      }
    }
    catch (Throwable e) {
      log.error(e);
    }
  }

  public void updateAppUserModelId() {
    if (appUserModelIdProperty != null)
      bridge.setAppUserModelIdNative(appUserModelIdProperty);
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
      throw new AssertionError("Class is not available");
    }

    log = Logger.getInstance(WinShellIntegration.class);
    bridge = new Bridge();
    thisIdeCacheDirPath = Paths.get(PathManager.getSystemPath(), "winshellintegration");
  }

  @ApiStatus.Internal
  @Override
  public void dispose() {
    bridge.comExecutor.shutdown();
  }


  /** @returns null if the caching should be disabled */
  private @Nullable WinShellIntegrationCache obtainCache() {
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

  private void patchSystemShortcutsCached(@NotNull WinShellIntegrationCache cache, @NotNull Path shortcutsTargetPath) {
    final var thisIdeCacheEntryOld = cache.pullThisIdeEntry();

    final WinShellIntegrationCache.IdeEntry thisIdeCacheEntryNew;
    if (thisIdeCacheEntryOld == null) {
      thisIdeCacheEntryNew = patchSystemShortcutsCacheMissed(cache, shortcutsTargetPath);
    } else {
      thisIdeCacheEntryNew = patchSystemShortcutsCacheHit(cache, thisIdeCacheEntryOld, shortcutsTargetPath);
    }

    cache.insertThisIdeEntry(thisIdeCacheEntryNew);

    saveCacheToStorage(cache);
  }

  private @NotNull WinShellIntegrationCache.IdeEntry patchSystemShortcutsCacheMissed(@NotNull WinShellIntegrationCache cache,
                                                                                     @NotNull Path shortcutsTargetPath) {
    final var startmenuShortcutSearchHints = new ArrayList<Path>(cache.getSize());
    final var taskbarShortcutSearchHints = new ArrayList<Path>(cache.getSize());

    cache.forEach(
      (path, entry) -> {
        if (entry == null) return;
        if (entry.startMenuShortcutPath != null) startmenuShortcutSearchHints.add(entry.startMenuShortcutPath);
        if (entry.taskbarShortcutPath != null) taskbarShortcutSearchHints.add(entry.taskbarShortcutPath);
      }
    );

    final var startMenuShortcutPath = findAndPatchStartmenuShortcut(
      shortcutsTargetPath,
      appUserModelIdProperty,
      startmenuShortcutSearchHints.toArray(Path[]::new)
    );

    final var taskbarShortcutPath = findAndPatchTaskbarShortcut(
      shortcutsTargetPath,
      (startMenuShortcutPath == null) ? appUserModelIdProperty : null,
      taskbarShortcutSearchHints.toArray(Path[]::new)
    );

    return new WinShellIntegrationCache.IdeEntry(appUserModelIdProperty, startMenuShortcutPath, taskbarShortcutPath);
  }

  private @NotNull WinShellIntegrationCache.IdeEntry patchSystemShortcutsCacheHit(@NotNull WinShellIntegrationCache cache,
                                                                                  @NotNull WinShellIntegrationCache.IdeEntry thisIdeCacheEntryOld,
                                                                                  @NotNull Path shortcutsTargetPath) {
    final @Nullable Path startMenuShortcutPath;

    if (!appUserModelIdProperty.equals(thisIdeCacheEntryOld.appUserModelId)) {
      // appUserModelIdProperty's value was changed; it may be caused for example by upgrading a major version of the IDE.
      // We have to re-patch shortcuts installed in Start Menu/Taskbar directories.

      startMenuShortcutPath = findAndPatchStartmenuShortcut(
        shortcutsTargetPath,
        appUserModelIdProperty,
        thisIdeCacheEntryOld.startMenuShortcutPath
      );

    } else if (thisIdeCacheEntryOld.startMenuShortcutPath != null) {
      // There was a shortcut installed in Start Menu.
      // We should ensure that it still exists and has a correct value of the AppUserModelID property.

      final var shortcutIsUpdated = compareUpdateShortcut(
        thisIdeCacheEntryOld.startMenuShortcutPath,
        shortcutsTargetPath,
        appUserModelIdProperty
      );

      if (shortcutIsUpdated)
        startMenuShortcutPath = thisIdeCacheEntryOld.startMenuShortcutPath;
      else
        startMenuShortcutPath = findAndPatchStartmenuShortcut(shortcutsTargetPath, appUserModelIdProperty);

    } else {
      // The cache told us a shortcut is not installed in Start Menu.
      // Let's believe it :).

      startMenuShortcutPath = null;
    }

    final @Nullable Path taskbarShortcutPath = findAndPatchTaskbarShortcut(
      shortcutsTargetPath,
      (startMenuShortcutPath == null) ? appUserModelIdProperty : null,
      thisIdeCacheEntryOld.taskbarShortcutPath
    );

    return new WinShellIntegrationCache.IdeEntry(appUserModelIdProperty, startMenuShortcutPath, taskbarShortcutPath);
  }

  private void patchSystemShortcutsNoCache(@NotNull Path shortcutsTargetPath) {
    final @Nullable var startMenuShortcutPath = findAndPatchStartmenuShortcut(shortcutsTargetPath, appUserModelIdProperty);

    findAndPatchTaskbarShortcut(shortcutsTargetPath, (startMenuShortcutPath == null) ? appUserModelIdProperty : null);
  }

  /**
   * @apiNote this method can take a long time for execution (perhaps > 10 sec).
   * @param newAppUserModelId new value of the AppUserModelId property of the shortcut; if is null then the value will be cleared;
   * @param searchHints if not null then the files specified by it will be checked first.
   * @returns null if no shortcuts pointed to this IDE's executable were found;
   *          otherwise {@code result.getKey()} will return path of the found shortcut
   *          and {@code result.getValue()} will return PREVIOUS value of the AppUserModelID property.
  */
  private @Nullable Path findAndPatchStartmenuShortcut(@NotNull Path shortcutTargetPath,
                                                       @Nullable String newAppUserModelId,
                                                       @Nullable Path @NotNull... searchHints) {
    Path foundShortcut = null;
    Path searchRoot = findThisUserStartMenuShortcutsRootDirectory();

    if (searchRoot == null) {
      log.warn("Failed to find the directory contains Start Menu's shortcuts of the current user. No such shortcuts will be scanned.");
    } else {
      foundShortcut = findAndPatchSystemShortcutImpl(searchRoot, shortcutTargetPath, newAppUserModelId, searchHints);
    }

    if (foundShortcut != null) {
      return foundShortcut;
    }

    searchRoot = findSystemStartMenuShortcutsRootDirectory();
    if (searchRoot == null) {
      log.warn("Failed to find the system directory contains Start Menu's shortcuts. No such shortcuts will be scanned.");
    } else {
      foundShortcut = findAndPatchSystemShortcutImpl(searchRoot, shortcutTargetPath, newAppUserModelId, searchHints);
    }

    return foundShortcut;
  }

  /**
   * @apiNote this method can take a long time for execution (perhaps > 10 sec).
   * @param newAppUserModelId new value of the AppUserModelId property of the shortcut; if is null then the value will be cleared;
   * @param searchHints if not null then the files specified by it will be checked first.
   * @returns null if no shortcuts pointed to this IDE's executable were found;
   *          otherwise {@code result.getKey()} will return path of the found shortcut
   *          and {@code result.getValue()} will return PREVIOUS value of the AppUserModelID property.
   */
  private @Nullable Path findAndPatchTaskbarShortcut(@NotNull Path shortcutTargetPath,
                                                     @Nullable String newAppUserModelId,
                                                     @Nullable Path @NotNull... searchHints) {
    final var searchRoot = findTaskbarShortcutsRootDirectory();
    if (searchRoot == null) {
      log.warn("Failed to find the directory contains Taskbar's shortcuts. No Taskbar's shortcuts will be scanned.");
      return null;
    }

    return findAndPatchSystemShortcutImpl(searchRoot, shortcutTargetPath, newAppUserModelId, searchHints);
  }


  private boolean compareUpdateShortcut(@NotNull Path shortcutPath,
                                        @NotNull Path expectedShortcutTargetPath,
                                        @Nullable String newAppUserModelId) {
    final var foundShortcut = invokeNativeFindAndPatchShortcuts(
      expectedShortcutTargetPath,
      newAppUserModelId,
      Collections.singletonList(shortcutPath),
      null
    );

    return shortcutPath.equals(foundShortcut);
  }

  private @Nullable Path findAndPatchSystemShortcutImpl(final @NotNull Path searchRootDirectory,
                                                        final @NotNull Path shortcutTargetPath,
                                                        final @Nullable String newAppUserModelId,
                                                        final @Nullable Path @NotNull... searchHints) {
    final @NotNull Path searchRootDirectoryNormalized = searchRootDirectory.toAbsolutePath().normalize();

    final var pathsBufferNative = new String[50];
    final var pathsBuffer = new ArrayList<Path>();

    // firstly, checking hints
    for (var searchHint : searchHints) {
      if (searchHint == null)
        continue;
      final var searchHintNormalized = searchHint.toAbsolutePath().normalize();
      if (!searchRootDirectoryNormalized.startsWith(searchHintNormalized))
        continue;

      final Path foundShortcut = stashOrFindAndPatchShortcuts(searchHintNormalized,
                                                              pathsBuffer,
                                                              pathsBufferNative,
                                                              shortcutTargetPath,
                                                              newAppUserModelId);
      if (foundShortcut != null) {
        return foundShortcut;
      }
    }

    if (!pathsBuffer.isEmpty()) {
      final Path foundShortcut = invokeNativeFindAndPatchShortcuts(shortcutTargetPath, newAppUserModelId, pathsBuffer, pathsBufferNative);
      if (foundShortcut != null) {
        return foundShortcut;
      }
    }

    // if the shortcut is still not found, search it everywhere inside searchRootDirectoryNormalized
    final var filesVisitor = new SimpleFileVisitor<Path>(){
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (attrs.isDirectory()) {
          return visitResult;
        }

        file = file.toAbsolutePath().normalize();

        foundShortcut = stashOrFindAndPatchShortcuts(file, pathsBuffer, pathsBufferNative, shortcutTargetPath, newAppUserModelId);
        if (foundShortcut != null)
          visitResult = FileVisitResult.TERMINATE;

        return visitResult;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        return visitResult;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return visitResult;
      }

      FileVisitResult visitResult = FileVisitResult.CONTINUE;
      Path foundShortcut = null;
    };

    try {
      Files.walkFileTree(searchRootDirectoryNormalized, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, filesVisitor);
    }
    catch (IOException e) {
      log.warn(e);
    }

    if ( (filesVisitor.foundShortcut == null) && (!pathsBuffer.isEmpty()) ) {
      filesVisitor.foundShortcut = invokeNativeFindAndPatchShortcuts(shortcutTargetPath, newAppUserModelId, pathsBuffer, pathsBufferNative);
    }

    return filesVisitor.foundShortcut;
  }


  private @Nullable Path stashOrFindAndPatchShortcuts(final @NotNull Path newShortcut,
                                                      final @NotNull List<Path> shortcutsBuffer,
                                                      final String @NotNull [] shortcutsBufferNative,
                                                      final @NotNull Path shortcutsTargetPath,
                                                      final @Nullable String newAppUserModelId) {
    final int shortcutsBunchSize = Math.max(shortcutsBufferNative.length, 50);

    shortcutsBuffer.add(newShortcut);
    while (shortcutsBuffer.size() >= shortcutsBunchSize) {
      final var shortcutsBunch = shortcutsBuffer.subList(0, shortcutsBunchSize);

      final var foundShortcut = invokeNativeFindAndPatchShortcuts(
        shortcutsTargetPath,
        newAppUserModelId,
        shortcutsBunch,
        shortcutsBufferNative
      );

      shortcutsBunch.clear();

      if (foundShortcut != null) {
        return foundShortcut;
      }
    }

    return null;
  }

  private @Nullable Path invokeNativeFindAndPatchShortcuts(final @NotNull Path shortcutsTargetPath,
                                                           final @Nullable String newAppUserModelId,
                                                           final @NotNull List<Path> shortcutsBuffer,
                                                           final String @Nullable [] shortcutsBufferNative) {
    final String[] shortcutsBufferNativeFixed;
    if ( (shortcutsBufferNative == null) || (shortcutsBufferNative.length != shortcutsBuffer.size()) )
      shortcutsBufferNativeFixed = new String[shortcutsBuffer.size()];
    else
      shortcutsBufferNativeFixed = shortcutsBufferNative;

    {
      int i = 0;

      for (final var shortcut : shortcutsBuffer)
        shortcutsBufferNativeFixed[i++] = shortcut.toString();
    }

    final var shortcutsTargetPathNative = shortcutsTargetPath.toString();

    try {
      final String foundShortcut = postShellTask((ShellContext ctx) -> {
        return ctx.findAndPatchShortcut(shortcutsTargetPathNative, newAppUserModelId, shortcutsBufferNativeFixed);
      }).get();

      if (foundShortcut == null)
        return null;

      return Paths.get(foundShortcut);
    }
    catch (InterruptedException e) {
      log.warn(e);
    }
    catch (ExecutionException e) {
      log.error(e);
    }

    return null;
  }


  private void saveCacheToStorage(@NotNull WinShellIntegrationCache cache) {
    try {
      if (!Files.exists(thisIdeCacheDirPath, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(thisIdeCacheDirPath);

      cache.saveToStorage(thisIdeCacheDirPath);
    }
    catch (IOException e) {
      log.warn(e);
    }
  }


  /**
   * @returns null if not found
   */
  private static @Nullable Path findThisUserStartMenuShortcutsRootDirectory() {
    final var thisUserRoamingDir = findThisUserRoamingDir();
    if (thisUserRoamingDir == null) {
      return null;
    }

    final var roamingStartMenuShortcuts = Paths.get("Microsoft", "Windows", "Start Menu", "Programs");
    return thisUserRoamingDir.resolve(roamingStartMenuShortcuts).normalize();
  }

  /**
   * @returns null if not found
   */
  private static @Nullable Path findSystemStartMenuShortcutsRootDirectory() {
    final var programDataDir = findProgramDataDir();
    if (programDataDir == null) {
      return null;
    }

    final Path programDataStartMenuShortcuts = Paths.get("Microsoft", "Windows", "Start Menu", "Programs");
    return programDataDir.resolve(programDataStartMenuShortcuts).normalize();
  }

  /**
   * @returns null if not found
   */
  private static @Nullable Path findTaskbarShortcutsRootDirectory() {
    final var thisUserRoamingDir = findThisUserRoamingDir();
    if (thisUserRoamingDir == null) {
      return null;
    }

    final var roamingStartMenuShortcuts = Paths.get("Microsoft", "Internet Explorer", "Quick Launch", "User Pinned", "TaskBar");
    return thisUserRoamingDir.resolve(roamingStartMenuShortcuts).normalize();
  }


  private static @Nullable Path findThisUserRoamingDir() {
    final String appDataEnv = System.getenv("APPDATA");
    if (appDataEnv != null) {
      final Path appData = Paths.get(appDataEnv);
      if (Files.isDirectory(appData, LinkOption.NOFOLLOW_LINKS)) {
        return appData;
      }
    }

    final String localAppDataEnv = System.getenv("LOCALAPPDATA");
    if (localAppDataEnv != null) {
      final Path appData = Paths.get(localAppDataEnv, "..", "Roaming");
      if (Files.isDirectory(appData, LinkOption.NOFOLLOW_LINKS)) {
        return appData;
      }
    }

    final String userHome = System.getProperty("user.home");
    if (userHome != null) {
      final Path appData = Paths.get(userHome, "AppData", "Roaming");
      if (Files.isDirectory(appData, LinkOption.NOFOLLOW_LINKS)) {
        return appData;
      }
    }

    return null;
  }

  private static @Nullable Path findProgramDataDir() {
    final String programDataEnv = System.getenv("PROGRAMDATA");
    if (programDataEnv != null) {
      final Path programData = Paths.get(programDataEnv);
      if (Files.isDirectory(programData, LinkOption.NOFOLLOW_LINKS)) {
        return programData;
      }
    }

    return null;
  }


  private void clearRecentTasksList() {
    bridge.ensureNativeIsInitialized();
    bridge.clearRecentTasksListNative();
  }

  private void setRecentTasksList(@NotNull Task @NotNull [] recentTasks) {
    bridge.ensureNativeIsInitialized();
    bridge.setRecentTasksListNative(recentTasks);
  }

  private String findAndPatchShortcut(@NotNull String targetPath,
                                      @NotNull String @NotNull [] shortcutsPaths,
                                      @Nullable String newAppUserModelId) {
    if (shortcutsPaths.length < 1) {
      return null;
    }

    bridge.ensureNativeIsInitialized();
    return bridge.findAndPatchShortcutNative(targetPath, shortcutsPaths, newAppUserModelId);
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
    native private String findAndPatchShortcutNative(@NotNull String targetPath,
                                                     @NotNull String @NotNull [] shortcutsPaths,
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


  private static final String appUserModelIdProperty;

  static {
    final var app = ApplicationInfoEx.getInstanceEx();
    final var appMgr = ApplicationManagerEx.getApplicationEx();

    final boolean shouldBeDisabled = appMgr.isHeadlessEnvironment() || appMgr.isLightEditMode();

    appUserModelIdProperty = app.getWin32AppUserModelId();
    isAvailable = SystemInfo.isWin8OrNewer && !shouldBeDisabled && !StringUtilRt.isEmptyOrSpaces(appUserModelIdProperty);
  }
}
