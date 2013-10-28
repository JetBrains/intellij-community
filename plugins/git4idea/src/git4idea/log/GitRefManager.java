package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    List<RefGroup> simpleGroups = ContainerUtil.newArrayList();
    List<VcsRef> localBranches = ContainerUtil.newArrayList();
    List<VcsRef> trackedBranches = ContainerUtil.newArrayList();
    MultiMap<GitRemote, VcsRef> remoteRefGroups = MultiMap.create();

    for (VcsRef ref : refs) {
      if (ref.getType() == HEAD) {
        simpleGroups.add(new SingletonRefGroup(ref));
      }
      else {
        GitRepository repository = myRepositoryManager.getRepositoryForRoot(ref.getRoot());
        if (repository == null) {
          LOG.warn("No repository for root: " + ref.getRoot());
          continue;
        }
        Collection<GitBranchTrackInfo> trackInfos = repository.getBranchTrackInfos();
        GitBranchesCollection branches = repository.getBranches();

        GitLocalBranch localBranch = findBranchByName(ref, branches.getLocalBranches());
        if (localBranch != null) {
          localBranches.add(ref);
        }
        else {
          GitRemoteBranch remoteBranch = findBranchByName(ref, branches.getRemoteBranches());
          if (remoteBranch != null) {
            if (isTracked(trackInfos, remoteBranch)) {
              trackedBranches.add(ref);
            }
            else {
              remoteRefGroups.putValue(remoteBranch.getRemote(), ref);
            }
          }
          else {
            LOG.warn("Didn't find ref neither in local nor in remote branches: " + ref);
          }
        }
      }
    }

    List<RefGroup> result = ContainerUtil.newArrayList();
    result.addAll(simpleGroups);
    result.add(new LogicalRefGroup("Local", localBranches));
    result.add(new LogicalRefGroup("Tracked", trackedBranches));
    for (Map.Entry<GitRemote, Collection<VcsRef>> entry : remoteRefGroups.entrySet()) {
      final GitRemote remote = entry.getKey();
      final Collection<VcsRef> branches = entry.getValue();
      result.add(new RemoteRefGroup(remote, branches));
    }
    return result;
  }

  @Nullable
  private static <T extends GitBranch> T findBranchByName(final VcsRef ref, Collection<T> branches) {
    return ContainerUtil.find(branches, new Condition<T>() {
      @Override
      public boolean value(T branch) {
        return branch.getName().equals(ref.getName());
      }
    });
  }

  private static boolean isTracked(Collection<GitBranchTrackInfo> trackInfos, final GitRemoteBranch remoteBranch) {
    return ContainerUtil.find(trackInfos, new Condition<GitBranchTrackInfo>() {
      @Override
      public boolean value(GitBranchTrackInfo info) {
        return info.getRemoteBranch().equals(remoteBranch);
      }
    }) != null;
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

  private static class LogicalRefGroup implements RefGroup {
    private final String myGroupName;
    private final List<VcsRef> myRefs;

    private LogicalRefGroup(String groupName, List<VcsRef> refs) {
      myGroupName = groupName;
      myRefs = refs;
    }

    @Override
    public boolean isExpanded() {
      return true;
    }

    @NotNull
    @Override
    public String getName() {
      return myGroupName;
    }

    @NotNull
    @Override
    public List<VcsRef> getRefs() {
      return myRefs;
    }

    @NotNull
    @Override
    public Color getBgColor() {
      return HEAD_COLOR;
    }
  }

  private class RemoteRefGroup implements RefGroup {
    private final GitRemote myRemote;
    private final Collection<VcsRef> myBranches;

    public RemoteRefGroup(GitRemote remote, Collection<VcsRef> branches) {
      myRemote = remote;
      myBranches = branches;
    }

    @Override
    public boolean isExpanded() {
      return false;
    }

    @NotNull
    @Override
    public String getName() {
      return myRemote.getName() + "/...";
    }

    @NotNull
    @Override
    public List<VcsRef> getRefs() {
      return sort(myBranches);
    }

    @NotNull
    @Override
    public Color getBgColor() {
      return REMOTE_BRANCH_COLOR;
    }

  }
}
