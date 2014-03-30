package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * @author Nadya Zabrodina
 */
public class HgRepositoryManager extends AbstractRepositoryManager<HgRepository> {

  public HgRepositoryManager(@NotNull Project project,
                             @NotNull ProjectLevelVcsManager vcsManager) {
    super(project, vcsManager, HgVcs.getInstance(project), HgUtil.DOT_HG);
  }

  @NotNull
  @Override
  protected HgRepository createRepository(@NotNull VirtualFile root) {
    return HgRepositoryImpl.getInstance(root, myProject, this);
  }
}
