// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

import static git4idea.log.GitRefManager.*;

@Service(Service.Level.PROJECT)
public final class GitBranchManager extends DvcsBranchManager<GitRepository> {
  public GitBranchManager(@NotNull Project project) {
    super(project, GitVcsSettings.getInstance(project).getBranchSettings(), GitBranchType.values(),
          GitRepositoryManager.getInstance(project));
  }

  @Override
  protected Collection<String> getDefaultBranchNames(@NotNull BranchType type) {
    ArrayList<String> branches = new ArrayList<>();
    if (type == GitBranchType.LOCAL) {
      branches.add(MASTER);
      branches.add(MAIN);
    }
    if (type == GitBranchType.REMOTE) {
      branches.add(ORIGIN_MASTER);
      branches.add(ORIGIN_MAIN);
    }
    return branches;
  }
}
