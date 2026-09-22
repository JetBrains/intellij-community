// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ApiStatus.Internal
public final class RootFileElement extends FileElement {

  private static final Logger LOG = Logger.getInstance(RootFileElement.class);

  private List<VirtualFile> myFiles;
  private Object[] myChildren;

  public RootFileElement(@NotNull List<VirtualFile> files, String name, boolean showFileSystemRoots) {
    super(files.size() == 1 ? files.get(0) : null, name);
    myFiles = files.isEmpty() && showFileSystemRoots ? null : files;
  }

  public Object[] getChildren() {
    if (myChildren == null) {
      if (myFiles == null) {
        myFiles = getFileSystemRoots();
      }

      myChildren = myFiles.stream().
        filter(Objects::nonNull).
        map(file -> new FileElement(file, file.getPresentableUrl())).
        toArray();
    }
    return myChildren;
  }

  private static List<VirtualFile> getFileSystemRoots() {
    VirtualFileManager fileManager = VirtualFileManager.getInstance();

    Stream<Path> paths = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false);

    if (WSLUtil.isSystemCompatible() && Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser")) {
      CompletableFuture<List<WSLDistribution>> future = WslDistributionManager.getInstance().getInstalledDistributionsFuture();
      try {
        List<WSLDistribution> distributions = future.get(200, TimeUnit.MILLISECONDS);
        paths = Stream.concat(paths, distributions.stream().map(WSLDistribution::getUNCRootPath));
      }
      catch (Exception e) {
        LOG.info("Cannot fetch WSL distributions", e);
      }
    }
    return paths.map(path -> fileManager.findFileByNioPath(path)).filter(Objects::nonNull).toList();
  }
}
