// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git revision selector class.
 */
public class GitRevisionSelector implements RevisionSelector {
  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public VcsRevisionNumber selectNumber(@NotNull VirtualFile file) {
    //GitVirtualFile gitFile = (GitVirtualFile) file;
    //TODO: implement selectNumber()
    return null;
  }
}
