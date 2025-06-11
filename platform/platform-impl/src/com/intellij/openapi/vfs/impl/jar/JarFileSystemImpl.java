// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.IntegrityCheckCapableFileSystem;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.Suppressions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JarFileSystemImpl extends JarFileSystem implements IntegrityCheckCapableFileSystem {
  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public @NotNull String extractPresentableUrl(@NotNull String path) {
    return super.extractPresentableUrl(StringUtil.trimEnd(path, JAR_SEPARATOR));
  }

  @Override
  protected @Nullable String normalize(@NotNull String path) {
    int separatorIndex = path.indexOf(JAR_SEPARATOR);
    return separatorIndex > 0 ? FileUtil.normalize(path.substring(0, separatorIndex)) + path.substring(separatorIndex) : null;
  }

  @Override
  protected @NotNull String extractRootPath(@NotNull String normalizedPath) {
    int separatorIndex = normalizedPath.indexOf(JAR_SEPARATOR);
    return separatorIndex > 0 ? normalizedPath.substring(0, separatorIndex + JAR_SEPARATOR.length()) : "";
  }

  @Override
  protected @NotNull String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, JAR_SEPARATOR);
  }

  @Override
  protected @NotNull String composeRootPath(@NotNull String localPath) {
    return localPath + JAR_SEPARATOR;
  }

  @Override
  protected @NotNull ZipHandlerBase getHandler(@NotNull VirtualFile entryFile) {
    return VfsImplUtil.getHandler(this, entryFile, SystemInfo.isWindows ? TimedZipHandler::new : ZipHandler::new);
  }

  @TestOnly
  public void markDirtyAndRefreshVirtualFileDeepInsideJarForTest(@NotNull VirtualFile file) {
    // clear caches in ArchiveHandler so that refresh will actually refresh something
    getHandler(file).clearCaches();
    VfsUtil.markDirtyAndRefresh(false, true, true, file);
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return isValid(path) ? findFileByPath(this, path) : null;
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return isValid(path) ? findFileByPathIfCached(this, path) : null;
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return isValid(path) ? VfsImplUtil.refreshAndFindFileByPath(this, path) : null;
  }

  private static boolean isValid(String path) {
    return path.contains(JAR_SEPARATOR);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @TestOnly
  @ApiStatus.Internal
  public static void cleanupForNextTest() {
    Suppressions.runSuppressing(
      () -> TimedZipHandler.closeOpenZipReferences(),
      () -> ZipHandler.clearFileAccessorCache()
    );
  }

  @Override
  public @NotNull Map<String, Long> getArchiveCrcHashes(@NotNull VirtualFile file) throws IOException {
    return getHandler(file).getArchiveCrcHashes();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated pointless; inline or avoid */
  @Deprecated(forRemoval = true)
  public @Nullable File getMirroredFile(@NotNull VirtualFile file) {
    return new File(file.getPath());
  }

  /** @deprecated no-op; stop using */
  @Deprecated(forRemoval = true)
  public boolean isMakeCopyOfJar(@SuppressWarnings("unused") @NotNull File originalJar) {
    return false;
  }
  //</editor-fold>
}
