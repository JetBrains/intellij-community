package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.branch.HgMultiRootBranchConfig;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * @author Nadya Zabrodina
 */
public class HgRepositoryManager extends AbstractRepositoryManager<HgRepository> {

  private final HgProjectSettings mySettings;

  public HgRepositoryManager(@NotNull Project project,
                             @NotNull ProjectLevelVcsManager vcsManager) {
    super(project, vcsManager, HgVcs.getInstance(project), HgUtil.DOT_HG);
    mySettings = ObjectUtils.assertNotNull(HgVcs.getInstance(project)).getProjectSettings();
  }

  @NotNull
  @Override
  protected HgRepository createRepository(@NotNull VirtualFile root) {
    return HgRepositoryImpl.getInstance(root, myProject, this);
  }

  @Override
  public boolean isSyncEnabled() {
    return mySettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC && !new HgMultiRootBranchConfig(getRepositories()).diverged();
  }

}
