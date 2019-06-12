// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.Set;

public class JarFileSystemImpl extends JarFileSystem {
  private final Set<String> myNoCopyJarPaths;
  private final File myNoCopyJarDir;

  public JarFileSystemImpl() {
    boolean noCopy = SystemProperties.getBooleanProperty("idea.jars.nocopy", !SystemInfo.isWindows);
    myNoCopyJarPaths = noCopy ? null : ConcurrentCollectionFactory.createConcurrentSet(FileUtil.PATH_HASHING_STRATEGY);

    // to prevent platform .jar files from copying
    boolean runningFromDist = new File(PathManager.getLibPath(), "openapi.jar").exists();
    myNoCopyJarDir = !runningFromDist ? null : new File(PathManager.getHomePath());
  }

  @Override
  public void setNoCopyJarForPath(@NotNull String pathInJar) {
    if (myNoCopyJarPaths == null) return;
    int index = pathInJar.indexOf(JAR_SEPARATOR);
    if (index < 0) return;
    String path = FileUtil.toSystemIndependentName(pathInJar.substring(0, index));
    myNoCopyJarPaths.add(path);
  }

  @Nullable
  public File getMirroredFile(@NotNull VirtualFile vFile) {
    VirtualFile root = getRootByLocal(vFile);
    if (root != null) {
      ArchiveHandler handler = getHandler(root);
      if (handler instanceof JarHandler) return ((JarHandler)handler).getFileToUse();
      return handler.getFile();
    }
    return null;
  }

  public boolean isMakeCopyOfJar(@NotNull File originalJar) {
    if (myNoCopyJarPaths == null || myNoCopyJarPaths.contains(FileUtil.toSystemIndependentName(originalJar.getPath()))) return false;
    if (myNoCopyJarDir != null && FileUtil.isAncestor(myNoCopyJarDir, originalJar, false)) return false;
    return true;
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @NotNull
  @Override
  public String extractPresentableUrl(@NotNull String path) {
    return super.extractPresentableUrl(StringUtil.trimEnd(path, JAR_SEPARATOR));
  }

  @NotNull
  @Override
  protected String normalize(@NotNull String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    if (jarSeparatorIndex > 0) {
      final String root = path.substring(0, jarSeparatorIndex);
      return FileUtil.normalize(root) + path.substring(jarSeparatorIndex);
    }
    return super.normalize(path);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    assert jarSeparatorIndex >= 0 : "Path passed to JarFileSystem must have jar separator '!/': " + path;
    return path.substring(0, jarSeparatorIndex + JAR_SEPARATOR.length());
  }

  @NotNull
  @Override
  protected String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, JAR_SEPARATOR);
  }

  @NotNull
  @Override
  protected String composeRootPath(@NotNull String localPath) {
    return localPath + JAR_SEPARATOR;
  }

  @NotNull
  @Override
  protected ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    boolean useNewJarHandler = Registry.is("vfs.use.new.jar.handler") && SystemInfo.isWindows;
    return VfsImplUtil.getHandler(this, entryFile, useNewJarHandler ? BasicJarHandler::new : JarHandler::new);
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
    BasicJarHandler.closeOpenedZipReferences();
  }
}