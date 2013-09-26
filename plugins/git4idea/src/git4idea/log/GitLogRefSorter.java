package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogRefSorter;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.BasicVcsLogRefSorter;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.VcsRef.RefType.LOCAL_BRANCH;
import static com.intellij.vcs.log.VcsRef.RefType.REMOTE_BRANCH;

/**
 * @author Kirill Likhodedov
 */
class GitLogRefSorter implements VcsLogRefSorter {

  private static final String MASTER = "master";
  private static final String ORIGIN_MASTER = "origin/master";
  private static final Logger LOG = Logger.getInstance(GitLogRefSorter.class);

  @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;

    // -1 => higher priority, i. e. the ref will be displayed at the left
  private final Comparator<VcsRef> REF_COMPARATOR = new Comparator<VcsRef>() {
    public int compare(VcsRef ref1, VcsRef ref2) {
      VcsRef.RefType type1 = ref1.getType();
      VcsRef.RefType type2 = ref2.getType();

      int typeComparison = BasicVcsLogRefSorter.REF_TYPE_COMPARATOR.compare(type1, type2);
      if (typeComparison != 0) {
        return typeComparison;
      }

      //noinspection UnnecessaryLocalVariable
      VcsRef.RefType type = type1; // common type
      if (type == LOCAL_BRANCH) {
        if (ref1.getName().equals(MASTER)) {
          return -1;
        }
        if (ref2.getName().equals(MASTER)) {
          return 1;
        }
        return ref1.getName().compareTo(ref2.getName());
      }

      if (type == REMOTE_BRANCH) {
        if (ref1.getName().equals(ORIGIN_MASTER)) {
          return -1;
        }
        if (ref2.getName().equals(ORIGIN_MASTER)) {
          return 1;
        }
        if (hasTrackingBranch(ref1) && !hasTrackingBranch(ref2)) {
          return -1;
        }
        if (!hasTrackingBranch(ref1) && hasTrackingBranch(ref2)) {
          return 1;
        }
        return ref1.getName().compareTo(ref2.getName());
      }

      return ref1.getName().compareTo(ref2.getName());
    }
  };

  private boolean hasTrackingBranch(@NotNull final VcsRef ref) {
    GitRepository repo = myRepositoryManager.getRepositoryForRoot(ref.getRoot());
    if (repo == null) {
      LOG.error("Undefined root " + ref.getRoot());
      return false;
    }
    return ContainerUtil.find(repo.getBranchTrackInfos(), new Condition<GitBranchTrackInfo>() {
      @Override
      public boolean value(GitBranchTrackInfo info) {
        return info.getRemoteBranch().getNameForLocalOperations().equals(ref.getName());
      }
    }) != null;
  }

  public GitLogRefSorter(@NotNull RepositoryManager<GitRepository> repositoryManager) {
    myRepositoryManager = repositoryManager;
  }

  @Override
  public List<VcsRef> sort(Collection<VcsRef> refs) {
    ArrayList<VcsRef> list = new ArrayList<VcsRef>(refs);
    Collections.sort(list, REF_COMPARATOR);
    return list;
  }

}
