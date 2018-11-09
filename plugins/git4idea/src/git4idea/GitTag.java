// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.branch.GitBranchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitTag extends GitReference {
  public static final String REFS_TAGS_PREFIX = "refs/tags/";

  public GitTag(@NotNull String name) {
    super(name);
  }

  @Override
  @NotNull
  public String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }

  @NotNull
  public static List<GitTag> list(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    return ContainerUtil.map(GitBranchUtil.getAllTags(project, root), GitTag::new);
  }
}
