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
package git4idea.ui.branch;

import com.intellij.dvcs.branch.BranchStorage;
import com.intellij.dvcs.branch.DvcsBranchInfo;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map2List;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static git4idea.log.GitRefManager.MASTER;
import static git4idea.log.GitRefManager.ORIGIN_MASTER;

public class GitBranchManager {
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitVcsSettings mySettings;
  @NotNull public final BranchStorage myPredefinedFavoriteBranches = new BranchStorage();

  public GitBranchManager(@NotNull GitRepositoryManager repositoryManager, @NotNull GitVcsSettings settings) {
    myRepositoryManager = repositoryManager;
    mySettings = settings;
    for (GitBranchType type : GitBranchType.values()) {
      myPredefinedFavoriteBranches.myBranches.put(type.toString(), constructDefaultBranchPredefinedList(type));
    }
  }

  @NotNull
  private List<DvcsBranchInfo> constructDefaultBranchPredefinedList(GitBranchType type) {
    List<DvcsBranchInfo> branchInfos = newArrayList(new DvcsBranchInfo("", getDefaultBranchName(type)));
    branchInfos.addAll(map2List(myRepositoryManager.getRepositories(),
                                repository -> new DvcsBranchInfo(repository.getRoot().getPath(), getDefaultBranchName(type))));
    return branchInfos;
  }

  @NotNull
  private static String getDefaultBranchName(@NotNull GitBranchType type) {
    return type == GitBranchType.LOCAL ? MASTER : ORIGIN_MASTER;
  }

  public boolean isFavorite(@NotNull GitBranchType branchType, @Nullable GitRepository repository, @NotNull String branchName) {
    if (mySettings.isFavorite(branchType, repository, branchName)) return true;
    if (mySettings.isExcludedFromFavorites(branchType, repository, branchName)) return false;
    return myPredefinedFavoriteBranches.contains(branchType.toString(), repository, branchName);
  }

  public void setFavorite(@NotNull GitBranchType branchType,
                           @Nullable GitRepository repository,
                           @NotNull String branchName,
                           boolean shouldBeFavorite) {
    if (shouldBeFavorite) {
      mySettings.addToFavorites(branchType, repository, branchName);
      mySettings.removeFromExcluded(branchType, repository, branchName);
    }
    else {
      if (mySettings.isFavorite(branchType, repository, branchName)) {
        mySettings.removeFromFavorites(branchType, repository, branchName);
      }
      else if (myPredefinedFavoriteBranches.contains(branchType.toString(), repository, branchName)) {
        mySettings.excludedFromFavorites(branchType, repository, branchName);
      }
    }
  }
}
