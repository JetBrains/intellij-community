// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.intellij.ui.win.WinShellIntegration.appUserModelIdProperty;


public class LauncherSystemShellLinksPatcher {
  public void patchSystemShellLinks() {
    try {
      final @NotNull String launcherExe = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName())
                                          + (SystemInfo.is64Bit ? "64" : "")
                                          + ".exe";
      final @NotNull Path launcherPath = Paths.get(PathManager.getBinPath(), launcherExe).normalize();

      final var cache = wsi.obtainCache();
      if (cache == null) {
        patchSystemShellLinksNoCache(launcherPath);
      } else {
        patchSystemShellLinksCached(cache, launcherPath);
      }
    }
    catch (Throwable e) {
      log.error(e);
    }
  }


  LauncherSystemShellLinksPatcher(@NotNull WinShellIntegration wsi)
  {
    this.wsi = wsi;
  }


  private void patchSystemShellLinksCached(@NotNull WinShellIntegrationCache cache, @NotNull Path shellLinksTargetPath) {
    final var thisIdeCacheEntryOld = cache.pullThisIdeEntry();

    final WinShellIntegrationCache.IdeEntry thisIdeCacheEntryNew;
    if (thisIdeCacheEntryOld == null) {
      thisIdeCacheEntryNew = patchSystemShellLinksCacheMissed(cache, shellLinksTargetPath);
    } else {
      thisIdeCacheEntryNew = patchSystemShellLinksCacheHit(cache, thisIdeCacheEntryOld, shellLinksTargetPath);
    }

    cache.insertThisIdeEntry(thisIdeCacheEntryNew);

    wsi.saveCacheToStorage(cache);
  }

  private @NotNull WinShellIntegrationCache.IdeEntry patchSystemShellLinksCacheMissed(@NotNull WinShellIntegrationCache cache,
                                                                                      @NotNull Path shellLinksTargetPath) {
    final var startmenuShellLinkSearchHints = new ArrayList<Path>(cache.getSize());
    final var taskbarShellLinkSearchHints = new ArrayList<Path>(cache.getSize());

    cache.forEach(
      (path, entry) -> {
        if (entry == null) return;
        if (entry.startMenuShellLinkPath != null) startmenuShellLinkSearchHints.add(entry.startMenuShellLinkPath);
        if (entry.taskbarShellLinkPath != null) taskbarShellLinkSearchHints.add(entry.taskbarShellLinkPath);
      }
    );

    final var startMenuShellLinkPath = findAndPatchStartmenuShellLink(
      shellLinksTargetPath,
      appUserModelIdProperty,
      startmenuShellLinkSearchHints.toArray(Path[]::new)
    );

    final var taskbarShellLinkPath = findAndPatchTaskbarShellLink(
      shellLinksTargetPath,
      (startMenuShellLinkPath == null) ? appUserModelIdProperty : null,
      taskbarShellLinkSearchHints.toArray(Path[]::new)
    );

    return new WinShellIntegrationCache.IdeEntry(appUserModelIdProperty, startMenuShellLinkPath, taskbarShellLinkPath);
  }

  private @NotNull WinShellIntegrationCache.IdeEntry patchSystemShellLinksCacheHit(@NotNull WinShellIntegrationCache cache,
                                                                                   @NotNull WinShellIntegrationCache.IdeEntry thisIdeCacheEntryOld,
                                                                                   @NotNull Path shellLinksTargetPath) {
    final @Nullable Path startMenuShellLinkPath;

    if (!appUserModelIdProperty.equals(thisIdeCacheEntryOld.appUserModelId)) {
      // appUserModelIdProperty's value was changed; it may be caused for example by upgrading a major version of the IDE.
      // We have to re-patch Shell links installed in Start Menu/Taskbar directories.

      startMenuShellLinkPath = findAndPatchStartmenuShellLink(
        shellLinksTargetPath,
        appUserModelIdProperty,
        thisIdeCacheEntryOld.startMenuShellLinkPath
      );

    } else if (thisIdeCacheEntryOld.startMenuShellLinkPath != null) {
      // There was a Shell link installed in Start Menu.
      // We should ensure that it still exists and has a correct value of the AppUserModelID property.

      final var shellLinkIsUpdated = compareUpdateShellLink(
        thisIdeCacheEntryOld.startMenuShellLinkPath,
        shellLinksTargetPath,
        appUserModelIdProperty
      );

      if (shellLinkIsUpdated)
        startMenuShellLinkPath = thisIdeCacheEntryOld.startMenuShellLinkPath;
      else
        startMenuShellLinkPath = findAndPatchStartmenuShellLink(shellLinksTargetPath, appUserModelIdProperty);

    } else {
      // The cache told us a Shell link is not installed in Start Menu.
      // Let's believe it :).

      startMenuShellLinkPath = null;
    }

    final @Nullable Path taskbarShellLinkPath = findAndPatchTaskbarShellLink(
      shellLinksTargetPath,
      (startMenuShellLinkPath == null) ? appUserModelIdProperty : null,
      thisIdeCacheEntryOld.taskbarShellLinkPath
    );

    return new WinShellIntegrationCache.IdeEntry(appUserModelIdProperty, startMenuShellLinkPath, taskbarShellLinkPath);
  }

  private void patchSystemShellLinksNoCache(@NotNull Path shellLinksTargetPath) {
    final @Nullable var startMenuShellLinkPath = findAndPatchStartmenuShellLink(shellLinksTargetPath, appUserModelIdProperty);

    findAndPatchTaskbarShellLink(shellLinksTargetPath, (startMenuShellLinkPath == null) ? appUserModelIdProperty : null);
  }

  /**
   * @apiNote this method can take a long time for execution (perhaps > 10 sec).
   * @param newAppUserModelId new value of the AppUserModelId property of the Shell link; if is null then the value will be cleared;
   * @param searchHints if not null then the files specified by it will be checked first.
   * @returns null if no Shell links pointed to this IDE's executable were found;
   *          otherwise {@code result.getKey()} will return path of the found Shell link
   *          and {@code result.getValue()} will return PREVIOUS value of the AppUserModelID property.
   */
  private @Nullable Path findAndPatchStartmenuShellLink(@NotNull Path shellLinkTargetPath,
                                                        @Nullable String newAppUserModelId,
                                                        @Nullable Path @NotNull... searchHints) {
    Path foundShellLink = null;
    Path searchRoot = findThisUserStartMenuShellLinksRootDirectory();

    if (searchRoot == null) {
      log.warn("Failed to find the directory contains Start Menu's shellLinks of the current user. No such shellLinks will be scanned.");
    } else {
      foundShellLink = findAndPatchSystemShellLinkImpl(searchRoot, shellLinkTargetPath, newAppUserModelId, searchHints);
    }

    if (foundShellLink != null) {
      return foundShellLink;
    }

    searchRoot = findSystemStartMenuShellLinksRootDirectory();
    if (searchRoot == null) {
      log.warn("Failed to find the system directory contains Start Menu's shellLinks. No such shellLinks will be scanned.");
    } else {
      foundShellLink = findAndPatchSystemShellLinkImpl(searchRoot, shellLinkTargetPath, newAppUserModelId, searchHints);
    }

    return foundShellLink;
  }

  /**
   * @apiNote this method can take a long time for execution (perhaps > 10 sec).
   * @param newAppUserModelId new value of the AppUserModelId property of the Shell link; if is null then the value will be cleared;
   * @param searchHints if not null then the files specified by it will be checked first.
   * @returns null if no Shell links pointed to this IDE's executable were found;
   *          otherwise {@code result.getKey()} will return path of the found Shell link
   *          and {@code result.getValue()} will return PREVIOUS value of the AppUserModelID property.
   */
  private @Nullable Path findAndPatchTaskbarShellLink(@NotNull Path shellLinkTargetPath,
                                                      @Nullable String newAppUserModelId,
                                                      @Nullable Path @NotNull... searchHints) {
    final var searchRoot = findTaskbarShellLinksRootDirectory();
    if (searchRoot == null) {
      log.warn("Failed to find the directory contains Taskbar's shellLinks. No Taskbar's shellLinks will be scanned.");
      return null;
    }

    return findAndPatchSystemShellLinkImpl(searchRoot, shellLinkTargetPath, newAppUserModelId, searchHints);
  }


  private boolean compareUpdateShellLink(@NotNull Path shellLinkPath,
                                         @NotNull Path expectedShellLinkTargetPath,
                                         @Nullable String newAppUserModelId) {
    final var foundShellLink = invokeNativeFindAndPatchShellLinks(
      expectedShellLinkTargetPath,
      newAppUserModelId,
      Collections.singletonList(shellLinkPath),
      null
    );

    return shellLinkPath.equals(foundShellLink);
  }

  private @Nullable Path findAndPatchSystemShellLinkImpl(final @NotNull Path searchRootDirectory,
                                                         final @NotNull Path shellLinkTargetPath,
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

      final Path foundShellLink = stashOrFindAndPatchShellLinks(
        searchHintNormalized,
        pathsBuffer,
        pathsBufferNative,
        shellLinkTargetPath,
        newAppUserModelId
      );

      if (foundShellLink != null) {
        return foundShellLink;
      }
    }

    if (!pathsBuffer.isEmpty()) {
      final Path foundShellLink = invokeNativeFindAndPatchShellLinks(shellLinkTargetPath, newAppUserModelId, pathsBuffer, pathsBufferNative);
      if (foundShellLink != null) {
        return foundShellLink;
      }
    }

    // if the Shell link is still not found, search it everywhere inside searchRootDirectoryNormalized
    final var filesVisitor = new SimpleFileVisitor<Path>(){
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (attrs.isDirectory()) {
          return visitResult;
        }

        file = file.toAbsolutePath().normalize();

        foundShellLink = stashOrFindAndPatchShellLinks(file, pathsBuffer, pathsBufferNative, shellLinkTargetPath, newAppUserModelId);
        if (foundShellLink != null)
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
      Path foundShellLink = null;
    };

    try {
      Files.walkFileTree(searchRootDirectoryNormalized, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, filesVisitor);
    }
    catch (IOException e) {
      log.warn(e);
    }

    if ( (filesVisitor.foundShellLink == null) && (!pathsBuffer.isEmpty()) ) {
      filesVisitor.foundShellLink = invokeNativeFindAndPatchShellLinks(shellLinkTargetPath, newAppUserModelId, pathsBuffer, pathsBufferNative);
    }

    return filesVisitor.foundShellLink;
  }


  private @Nullable Path stashOrFindAndPatchShellLinks(final @NotNull Path newShellLink,
                                                       final @NotNull List<Path> shellLinksBuffer,
                                                       final String @NotNull [] shellLinksBufferNative,
                                                       final @NotNull Path shellLinksTargetPath,
                                                       final @Nullable String newAppUserModelId) {
    final int shellLinksBunchSize = Math.max(shellLinksBufferNative.length, 50);

    shellLinksBuffer.add(newShellLink);
    while (shellLinksBuffer.size() >= shellLinksBunchSize) {
      final var shellLinksBunch = shellLinksBuffer.subList(0, shellLinksBunchSize);

      final var foundShellLink = invokeNativeFindAndPatchShellLinks(
        shellLinksTargetPath,
        newAppUserModelId,
        shellLinksBunch,
        shellLinksBufferNative
      );

      shellLinksBunch.clear();

      if (foundShellLink != null) {
        return foundShellLink;
      }
    }

    return null;
  }

  private @Nullable Path invokeNativeFindAndPatchShellLinks(final @NotNull Path shellLinksTargetPath,
                                                            final @Nullable String newAppUserModelId,
                                                            final @NotNull List<Path> shellLinksBuffer,
                                                            final String @Nullable [] shellLinksBufferNative) {
    final String[] shellLinksBufferNativeFixed;
    if ( (shellLinksBufferNative == null) || (shellLinksBufferNative.length != shellLinksBuffer.size()) )
      shellLinksBufferNativeFixed = new String[shellLinksBuffer.size()];
    else
      shellLinksBufferNativeFixed = shellLinksBufferNative;

    {
      int i = 0;

      for (final var shellLink : shellLinksBuffer)
        shellLinksBufferNativeFixed[i++] = shellLink.toString();
    }

    final var shellLinksTargetPathNative = shellLinksTargetPath.toString();

    try {
      final String foundShellLink = wsi.postShellTask((WinShellIntegration.ShellContext ctx) -> {
        return ctx.findAndPatchShellLink(shellLinksTargetPathNative, newAppUserModelId, shellLinksBufferNativeFixed);
      }).get();

      if (foundShellLink == null)
        return null;

      return Paths.get(foundShellLink);
    }
    catch (InterruptedException e) {
      log.warn(e);
    }
    catch (ExecutionException e) {
      log.error(e);
    }

    return null;
  }


  /**
   * @returns null if not found
   */
  private static @Nullable Path findThisUserStartMenuShellLinksRootDirectory() {
    final var thisUserRoamingDir = findThisUserRoamingDir();
    if (thisUserRoamingDir == null) {
      return null;
    }

    final var roamingStartMenuShellLinks = Paths.get("Microsoft", "Windows", "Start Menu", "Programs");
    return thisUserRoamingDir.resolve(roamingStartMenuShellLinks).normalize();
  }

  /**
   * @returns null if not found
   */
  private static @Nullable Path findSystemStartMenuShellLinksRootDirectory() {
    final var programDataDir = findProgramDataDir();
    if (programDataDir == null) {
      return null;
    }

    final Path programDataStartMenuShellLinks = Paths.get("Microsoft", "Windows", "Start Menu", "Programs");
    return programDataDir.resolve(programDataStartMenuShellLinks).normalize();
  }

  /**
   * @returns null if not found
   */
  private static @Nullable Path findTaskbarShellLinksRootDirectory() {
    final var thisUserRoamingDir = findThisUserRoamingDir();
    if (thisUserRoamingDir == null) {
      return null;
    }

    final var roamingStartMenuShellLinks = Paths.get("Microsoft", "Internet Explorer", "Quick Launch", "User Pinned", "TaskBar");
    return thisUserRoamingDir.resolve(roamingStartMenuShellLinks).normalize();
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


  private final @NotNull WinShellIntegration wsi;


  private static final Logger log = Logger.getInstance(LauncherSystemShellLinksPatcher.class);
}
