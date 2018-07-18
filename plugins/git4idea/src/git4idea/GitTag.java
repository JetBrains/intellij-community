/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @NotNull
  public String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }

  @NotNull
  public static List<GitTag> list(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    return ContainerUtil.map(GitBranchUtil.getAllTags(project, root), GitTag::new);
  }
}
