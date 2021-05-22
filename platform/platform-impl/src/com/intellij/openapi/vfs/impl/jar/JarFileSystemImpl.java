// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.IntegrityCheckCapableFileSystem;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.vfs.impl.ZipHandlerBase;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JarFileSystemImpl extends JarFileSystem implements IntegrityCheckCapableFileSystem {
  private final Set<String> myNoCopyJarPaths;
  private final Path myNoCopyJarDir;

  public JarFileSystemImpl() {
    if (!SystemInfoRt.isWindows) {
      myNoCopyJarPaths = null;
    }
    else if (SystemInfoRt.isFileSystemCaseSensitive) {
      //noinspection SSBasedInspection
      myNoCopyJarPaths = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }
    else {
      myNoCopyJarPaths = ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.caseInsensitive());
    }

    // to prevent platform .jar files from copying
    myNoCopyJarDir = Path.of(PathManager.getHomePath());
  }

  @Override
  public void setNoCopyJarForPath(@NotNull String pathInJar) {
    if (myNoCopyJarPaths == null) return;
    int index = pathInJar.indexOf(JAR_SEPARATOR);
    if (index > 0) pathInJar = pathInJar.substring(0, index);
    myNoCopyJarPaths.add(new File(pathInJar).getPath());
  }

  public @Nullable File getMirroredFile(@NotNull VirtualFile file) {
    return new File(file.getPath());
  }

  public boolean isMakeCopyOfJar(@NotNull File originalJar) {
    return !(myNoCopyJarPaths == null ||
             myNoCopyJarPaths.contains(originalJar.getPath()) ||
             originalJar.toPath().startsWith(myNoCopyJarDir));
  }

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
  protected @NotNull ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    return VfsImplUtil.getHandler(this, entryFile, myNoCopyJarPaths == null ? ZipHandler::new : TimedZipHandler::new);
  }

  @TestOnly
  public void markDirtyAndRefreshVirtualFileDeepInsideJarForTest(@NotNull VirtualFile file) {
    // clear caches in ArchiveHandler so that refresh will actually refresh something
    getHandler(file).dispose();
    VfsUtil.markDirtyAndRefresh(false, true, true, file);
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return isValid(path) ? VfsImplUtil.findFileByPath(this, path) : null;
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return isValid(path) ? VfsImplUtil.findFileByPathIfCached(this, path) : null;
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
  public static void cleanupForNextTest() {
    TimedZipHandler.closeOpenZipReferences();
  }

  @Override
  public long getEntryCrc(@NotNull VirtualFile file) throws IOException {
    ArchiveHandler handler = getHandler(file);
    return ((ZipHandlerBase)handler).getEntryCrc(getRelativePath(file));
  }
}
