// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class DiffProviderEx implements DiffProvider {
  public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(@NotNull Iterable<? extends VirtualFile> files) {
    return getCurrentRevisions(files, this);
  }

  public static Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(Iterable<? extends VirtualFile> file, DiffProvider provider) {
    Map<VirtualFile, VcsRevisionNumber> result = new HashMap<>();
    for (VirtualFile virtualFile : file) {
      result.put(virtualFile, provider.getCurrentRevision(virtualFile));
    }
    return result;
  }
}
