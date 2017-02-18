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
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class GitTag extends GitReference {
  public static final String REFS_TAGS_PREFIX = "refs/tags/";

  public GitTag(@NotNull String name) {
    super(name);
  }

  @NotNull
  public String getFullName() {
    return REFS_TAGS_PREFIX + myName;
  }

  @Deprecated
  public static void listAsStrings(final Project project, final VirtualFile root, final Collection<String> tags,
                                   @Nullable final String containingCommit) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.TAG);
    handler.setSilent(true);
    handler.addParameters("-l");
    if (containingCommit != null) {
      handler.addParameters("--contains");
      handler.addParameters(containingCommit);
    }
    for (String line : handler.run().split("\n")) {
      if (line.length() == 0) {
        continue;
      }
      tags.add(new String(line));
    }
  }

  @Deprecated
  public static void list(final Project project, final VirtualFile root, final Collection<? super GitTag> tags) throws VcsException {
    ArrayList<String> temp = new ArrayList<>();
    listAsStrings(project, root, temp, null);
    for (String t : temp) {
      tags.add(new GitTag(t));
    }
  }
}
