/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides details for a commit usually not available from the {@link git4idea.history.browser.GitHeavyCommit} object.
 *
 * @author Kirill Likhodedov
 */
public interface GitCommitDetailsProvider {

  /**
   * Returns names of branches which contain the given commit.
   */
  @NotNull
  List<String> getContainingBranches(@NotNull VirtualFile root, @NotNull AbstractHash commitHash);
}
