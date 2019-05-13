/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.ui.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static git4idea.log.GitRefManager.MASTER;
import static git4idea.log.GitRefManager.ORIGIN_MASTER;

public class GitBranchManager extends DvcsBranchManager {

  public GitBranchManager(@NotNull GitVcsSettings settings) {
    super(settings.getFavoriteBranchSettings(), GitBranchType.values());
  }

  @Nullable
  @Override
  protected String getDefaultBranchName(@NotNull BranchType type) {
    if (type == GitBranchType.LOCAL) return MASTER;
    if (type == GitBranchType.REMOTE) return ORIGIN_MASTER;
    return null;
  }
}
