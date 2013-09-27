package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitRefManager implements VcsLogRefManager {

  private static final Color HEAD_COLOR = new JBColor(new Color(0xf1ef9e), new Color(113, 111, 64));
  private static final Color LOCAL_BRANCH_COLOR = new JBColor(new Color(0x75eec7), new Color(0x0D6D4F));
  private static final Color REMOTE_BRANCH_COLOR = new JBColor(new Color(0xbcbcfc), new Color(0xbcbcfc).darker().darker());
  private static final Color TAG_COLOR = JBColor.WHITE;

  public static final VcsRefType HEAD = new SimpleRefType(true, HEAD_COLOR);
  public static final VcsRefType LOCAL_BRANCH = new SimpleRefType(true, LOCAL_BRANCH_COLOR);
  public static final VcsRefType REMOTE_BRANCH = new SimpleRefType(true, REMOTE_BRANCH_COLOR);
  public static final VcsRefType TAG = new SimpleRefType(false, TAG_COLOR);

  // first has the highest priority
  private static final List<VcsRefType> REF_TYPE_PRIORITIES = Arrays.asList(HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG);

  // -1 => higher priority
  public static final Comparator<VcsRefType> REF_TYPE_COMPARATOR = new Comparator<VcsRefType>() {
    @Override
    public int compare(VcsRefType type1, VcsRefType type2) {
      int p1 = REF_TYPE_PRIORITIES.indexOf(type1);
      int p2 = REF_TYPE_PRIORITIES.indexOf(type2);
      return p1 - p2;
    }
  };

  private static final String MASTER = "master";
  private static final String ORIGIN_MASTER = "origin/master";
  private static final Logger LOG = Logger.getInstance(GitRefManager.class);

  @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;

    // -1 => higher priority, i. e. the ref will be displayed at the left
  private final Comparator<VcsRef> REF_COMPARATOR = new Comparator<VcsRef>() {
    public int compare(VcsRef ref1, VcsRef ref2) {
      VcsRefType type1 = ref1.getType();
      VcsRefType type2 = ref2.getType();

      int typeComparison = REF_TYPE_COMPARATOR.compare(type1, type2);
      if (typeComparison != 0) {
        return typeComparison;
      }

      //noinspection UnnecessaryLocalVariable
      VcsRefType type = type1; // common type
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

  public GitRefManager(@NotNull RepositoryManager<GitRepository> repositoryManager) {
    myRepositoryManager = repositoryManager;
  }

  @NotNull
  @Override
  public List<VcsRef> sort(Collection<VcsRef> refs) {
    ArrayList<VcsRef> list = new ArrayList<VcsRef>(refs);
    Collections.sort(list, REF_COMPARATOR);
    return list;
  }

  @NotNull
  @Override
  public List<RefGroup> group(Collection<VcsRef> refs) {
    // TODO group non-tracking refs into remotes
    return ContainerUtil.map(sort(refs), new Function<VcsRef, RefGroup>() {
      @Override
      public RefGroup fun(final VcsRef ref) {
        return new RefGroup() {
          @NotNull
          @Override
          public String getName() {
            return ref.getName();
          }

          @NotNull
          @Override
          public List<VcsRef> getRefs() {
            return Collections.singletonList(ref);
          }
        };
      }
    });
  }

  private static class SimpleRefType implements VcsRefType {
    private final boolean myIsBranch;
    @NotNull private final Color myColor;

    public SimpleRefType(boolean isBranch, @NotNull Color color) {
      myIsBranch = isBranch;
      myColor = color;
    }

    @Override
    public boolean isBranch() {
      return myIsBranch;
    }

    @NotNull
    @Override
    public Color getBackgroundColor() {
      return myColor;
    }
  }

}
