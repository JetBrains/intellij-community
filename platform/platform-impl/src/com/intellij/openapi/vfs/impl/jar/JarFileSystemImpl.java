/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.jar;

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
import com.intellij.util.containers.ContainerUtil;
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
    myNoCopyJarPaths = noCopy ? null : ContainerUtil.newConcurrentSet(FileUtil.PATH_HASHING_STRATEGY);

    // to prevent platform .jar files from copying
    boolean runningFromDist = new File(PathManager.getLibPath(), "openapi.jar").exists();
    myNoCopyJarDir = !runningFromDist ? null : new File(PathManager.getHomePath());
  }

  @Override
  public void setNoCopyJarForPath(String pathInJar) {
    if (myNoCopyJarPaths == null || pathInJar == null) return;
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
    boolean useNewJarHandler = Registry.is("vfs.use.new.jar.handler");
    return VfsImplUtil.getHandler(this, entryFile, useNewJarHandler ? BasicJarHandler::new : JarHandler::new);
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @TestOnly
  public void cleanupForNextTest() {
    BasicJarHandler.closeOpenedZipReferences();
  }
}