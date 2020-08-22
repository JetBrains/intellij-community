// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

public final class FindAllRootsHelper {
  private FindAllRootsHelper() { }

  public static List<VirtualFile> findVersionedUnder(final List<? extends VirtualFile> coll) {
    final List<FilePath> pathList = map(coll, VcsUtil::getFilePath);
    final MyVisitor visitor = new MyVisitor();

    for (FilePath root : pathList) {
      final VirtualFile vf = root.getVirtualFile();
      if (vf == null) continue;
      VfsUtilCore.visitChildrenRecursively(vf, visitor);
    }

    return visitor.found;
  }

  private static class MyVisitor extends VirtualFileVisitor<Void> {
    private final List<VirtualFile> found = new LinkedList<>();

    @NotNull
    @Override
    public Result visitFileEx(@NotNull VirtualFile file) {
      if (CvsUtil.fileIsUnderCvsMaybeWithVfs(file)) {
        found.add(file);
      }
      return file.isDirectory() && found.contains(file) ? SKIP_CHILDREN : CONTINUE;
    }
  }
}
