package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * @author Nadya Zabrodina
 */
public class HgRepositoryManager extends AbstractRepositoryManager<HgRepository> implements RepositoryManager<HgRepository> {

  public HgRepositoryManager(@NotNull Project project,
                             @NotNull ProjectLevelVcsManager vcsManager) {
    super(project, vcsManager);
  }

  @Override
  public void initComponent() {
    myVcs = HgVcs.getInstance(myProject);
    Disposer.register(myProject, this);
    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
  }


  @Override
  protected boolean isRootValid(@NotNull VirtualFile root) {
    VirtualFile hgDir = root.findChild(HgUtil.DOT_HG);
    return hgDir != null && hgDir.exists();
  }

  @NotNull
  @Override
  protected HgRepository createRepository(@NotNull VirtualFile root) {
    return HgRepositoryImpl.getInstance(root, myProject, this);
  }
}
