// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsEnvCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitExecutableContext extends VcsEnvCustomizer.VcsExecutableContext {
  private boolean myWithLowPriority;
  private boolean myWithNoTty;

  public GitExecutableContext(@Nullable AbstractVcs vcs,
                              @Nullable VirtualFile vcsRoot,
                              @NotNull VcsEnvCustomizer.ExecutableType type) {
    super(vcs, vcsRoot, type);
  }

  public boolean isWithLowPriority() {
    return myWithLowPriority;
  }

  public boolean isWithNoTty() {
    return myWithNoTty;
  }

  public void withLowPriority(boolean value) {
    myWithLowPriority = value;
  }

  public void withNoTty(boolean withNoTty) {
    myWithNoTty = withNoTty;
  }
}
