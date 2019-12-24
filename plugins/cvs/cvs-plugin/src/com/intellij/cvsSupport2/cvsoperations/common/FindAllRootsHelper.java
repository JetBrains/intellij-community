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

public class FindAllRootsHelper {
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
