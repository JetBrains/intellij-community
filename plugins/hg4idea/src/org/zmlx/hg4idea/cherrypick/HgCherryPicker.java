/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.cherrypick;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgGraftCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;
import java.util.Map;

public class HgCherryPicker extends VcsCherryPicker {

  @NotNull private final Project myProject;

  public HgCherryPicker(@NotNull Project project) {
    myProject = project;
  }


  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @Override
  public String getPreferredActionTitle() {
    return "Graft";
  }

  @Override
  public void cherryPick(@NotNull final List<VcsFullCommitDetails> commits) {
    Map<HgRepository, List<VcsFullCommitDetails>> commitsInRoots = DvcsUtil.groupCommitsByRoots(
      HgUtil.getRepositoryManager(myProject), commits);
    for (Map.Entry<HgRepository, List<VcsFullCommitDetails>> entry : commitsInRoots.entrySet()) {
      new HgGraftCommand(myProject,
                         entry.getKey()).startGrafting(ContainerUtil.map(entry.getValue(),
                                                                         new Function<VcsFullCommitDetails, String>() {
                                                                           @Override
                                                                           public String fun(
                                                                             VcsFullCommitDetails commitDetails) {
                                                                             return commitDetails.getId()
                                                                               .asString();
                                                                           }
                                                                         }));
      //todo  should process grafting ;
    }
  }

  @Override
  public boolean isEnabled(@NotNull VcsLog log, @NotNull List<VcsFullCommitDetails> details) {
    if (details.isEmpty()) {
      return false;
    }

    for (VcsFullCommitDetails commit : details) {
      HgRepository repository = HgUtil.getRepositoryManager(myProject).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        return false;
      }
    }
    return true;
  }
}
