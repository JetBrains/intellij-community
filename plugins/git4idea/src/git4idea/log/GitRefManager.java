package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitTag;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
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
  public static final VcsRefType OTHER = new SimpleRefType(false, TAG_COLOR);

  private static final String MASTER = "master";
  private static final String ORIGIN_MASTER = "origin/master";
  private static final Logger LOG = Logger.getInstance(GitRefManager.class);

  protected enum RefType {
    OTHER,
    HEAD,
    TAG,
    NON_TRACKING_LOCAL_BRANCH,
    NON_TRACKED_REMOTE_BRANCH,
    TRACKING_LOCAL_BRANCH,
    MASTER,
    TRACKED_REMOTE_BRANCH,
    ORIGIN_MASTER
  }

  @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;
  @NotNull private final Comparator<VcsRef> myLabelsComparator;
  @NotNull private final Comparator<VcsRef> myBranchLayoutComparator;

  public GitRefManager(@NotNull RepositoryManager<GitRepository> repositoryManager) {
    myRepositoryManager = repositoryManager;
    myBranchLayoutComparator = new GitBranchLayoutComparator(repositoryManager);
    myLabelsComparator = new GitLabelComparator(repositoryManager);
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getLabelsOrderComparator() {
    return myLabelsComparator;
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getBranchLayoutComparator() {
    return myBranchLayoutComparator;
  }

  @NotNull
  @Override
  public List<RefGroup> group(Collection<VcsRef> refs) {
    List<RefGroup> simpleGroups = ContainerUtil.newArrayList();
    List<VcsRef> localBranches = ContainerUtil.newArrayList();
    List<VcsRef> trackedBranches = ContainerUtil.newArrayList();
    MultiMap<GitRemote, VcsRef> remoteRefGroups = MultiMap.create();

    MultiMap<VirtualFile, VcsRef> refsByRoot = groupRefsByRoot(refs);
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : refsByRoot.entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VcsRef> refsInRoot = entry.getValue();

      GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null) {
        LOG.warn("No repository for root: " + root);
        continue;
      }

      Set<String> locals = getLocalBranches(repository);
      Set<String> tracked = getTrackedRemoteBranches(repository);
      Map<String, GitRemote> allRemote = getAllRemoteBranches(repository);

      for (VcsRef ref : refsInRoot) {
        if (ref.getType() == HEAD) {
          simpleGroups.add(new SingletonRefGroup(ref));
          continue;
        }

        String refName = ref.getName();
        if (locals.contains(refName)) {
          localBranches.add(ref);
        }
        else if (allRemote.containsKey(refName)) {
          remoteRefGroups.putValue(allRemote.get(refName), ref);
          if (tracked.contains(refName)) {
            trackedBranches.add(ref);
          }
        }
        else {
          LOG.debug("Didn't find ref neither in local nor in remote branches: " + ref);
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

  private static Set<String> getLocalBranches(GitRepository repository) {
    return ContainerUtil.map2Set(repository.getBranches().getLocalBranches(), new Function<GitBranch, String>() {
      @Override
      public String fun(GitBranch branch) {
        return branch.getName();
      }
    });
  }

  @NotNull
  private static Set<String> getTrackedRemoteBranches(@NotNull GitRepository repository) {
    Set<GitRemoteBranch> all = new HashSet<GitRemoteBranch>(repository.getBranches().getRemoteBranches());
    Set<String> tracked = new HashSet<String>();
    for (GitBranchTrackInfo info : repository.getBranchTrackInfos()) {
      GitRemoteBranch trackedRemoteBranch = info.getRemoteBranch();
      if (all.contains(trackedRemoteBranch)) { // check that this branch really exists, not just written in .git/config
        tracked.add(trackedRemoteBranch.getName());
      }
    }
    return tracked;
  }

  @NotNull
  private static Map<String, GitRemote> getAllRemoteBranches(@NotNull GitRepository repository) {
    Set<GitRemoteBranch> all = new HashSet<GitRemoteBranch>(repository.getBranches().getRemoteBranches());
    Map<String, GitRemote> allRemote = ContainerUtil.newHashMap();
    for (GitRemoteBranch remoteBranch : all) {
      allRemote.put(remoteBranch.getName(), remoteBranch.getRemote());
    }
    return allRemote;
  }

  private static Set<String> getTrackedRemoteBranchesFromConfig(GitRepository repository) {
    return ContainerUtil.map2Set(repository.getBranchTrackInfos(), new Function<GitBranchTrackInfo, String>() {
      @Override
      public String fun(GitBranchTrackInfo trackInfo) {
        return trackInfo.getRemoteBranch().getName();
      }
    });
  }

  @NotNull
  private static MultiMap<VirtualFile, VcsRef> groupRefsByRoot(@NotNull Iterable<VcsRef> refs) {
    MultiMap<VirtualFile, VcsRef> grouped = MultiMap.create();
    for (VcsRef ref : refs) {
      grouped.putValue(ref.getRoot(), ref);
    }
    return grouped;
  }

  @NotNull
  public static VcsRefType getRefType(@NotNull String refName) {
    if (refName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
      return LOCAL_BRANCH;
    }
    if (refName.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
      return REMOTE_BRANCH;
    }
    if (refName.startsWith(GitTag.REFS_TAGS_PREFIX)) {
      return TAG;
    }
    if (refName.startsWith("HEAD")) {
      return HEAD;
    }
    return OTHER;
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
      return ContainerUtil.sorted(myBranches, getLabelsOrderComparator());
    }

    @NotNull
    @Override
    public Color getBgColor() {
      return REMOTE_BRANCH_COLOR;
    }

  }

  private static class GitLabelComparator extends GitRefComparator {
    private static final RefType[] ORDERED_TYPES = {
      RefType.HEAD,
      RefType.MASTER,
      RefType.TRACKING_LOCAL_BRANCH,
      RefType.NON_TRACKING_LOCAL_BRANCH,
      RefType.ORIGIN_MASTER,
      RefType.TRACKED_REMOTE_BRANCH,
      RefType.NON_TRACKED_REMOTE_BRANCH,
      RefType.TAG,
      RefType.OTHER
    };

    GitLabelComparator(@NotNull RepositoryManager<GitRepository> repositoryManager) {
      super(repositoryManager);
    }

    @Override
    protected RefType[] getOrderedTypes() {
      return ORDERED_TYPES;
    }
  }

  private static class GitBranchLayoutComparator extends GitRefComparator {
    private static final RefType[] ORDERED_TYPES = {
      RefType.ORIGIN_MASTER,
      RefType.TRACKED_REMOTE_BRANCH,
      RefType.MASTER,
      RefType.TRACKING_LOCAL_BRANCH,
      RefType.NON_TRACKING_LOCAL_BRANCH,
      RefType.NON_TRACKED_REMOTE_BRANCH,
      RefType.TAG,
      RefType.HEAD,
      RefType.OTHER
    };

    GitBranchLayoutComparator(@NotNull RepositoryManager<GitRepository> repositoryManager) {
      super(repositoryManager);
    }

    @Override
    protected RefType[] getOrderedTypes() {
      return ORDERED_TYPES;
    }
  }

  private abstract static class GitRefComparator implements Comparator<VcsRef> {
    @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;

    GitRefComparator(@NotNull RepositoryManager<GitRepository> repositoryManager) {
      myRepositoryManager = repositoryManager;
    }

    @Override
    public int compare(@NotNull VcsRef ref1, @NotNull VcsRef ref2) {
      int power1 = ArrayUtil.find(getOrderedTypes(), getType(ref1));
      int power2 = ArrayUtil.find(getOrderedTypes(), getType(ref2));
      if (power1 != power2) {
        return power1 - power2;
      }
      int namesComparison = ref1.getName().compareTo(ref2.getName());
      if (namesComparison != 0) {
        return namesComparison;
      }
      return VcsLogUtil.compareRoots(ref1.getRoot(), ref2.getRoot());
    }

    protected abstract RefType[] getOrderedTypes();

    @NotNull
    private RefType getType(@NotNull VcsRef ref) {
      VcsRefType type = ref.getType();
      if (type == HEAD) {
        return RefType.HEAD;
      }
      else if (type == TAG) {
        return RefType.TAG;
      }
      else if (type == LOCAL_BRANCH) {
        if (ref.getName().equals(MASTER)) {
          return RefType.MASTER;
        }
        return isTracked(ref, false) ? RefType.TRACKING_LOCAL_BRANCH : RefType.NON_TRACKING_LOCAL_BRANCH;
      }
      else if (type == REMOTE_BRANCH) {
        if (ref.getName().equals(ORIGIN_MASTER)) {
          return RefType.ORIGIN_MASTER;
        }
        return isTracked(ref, true) ? RefType.TRACKED_REMOTE_BRANCH : RefType.NON_TRACKED_REMOTE_BRANCH;
      }
      else {
        return RefType.OTHER;
      }
    }

    private boolean isTracked(@NotNull final VcsRef ref, final boolean remoteBranch) {
      GitRepository repo = myRepositoryManager.getRepositoryForRoot(ref.getRoot());
      if (repo == null) {
        LOG.error("Undefined root " + ref.getRoot());
        return false;
      }
      return ContainerUtil.exists(repo.getBranchTrackInfos(), new Condition<GitBranchTrackInfo>() {
        @Override
        public boolean value(GitBranchTrackInfo info) {
          return remoteBranch ?
                 info.getRemoteBranch().getNameForLocalOperations().equals(ref.getName()) :
                 info.getLocalBranch().getName().equals(ref.getName());
        }
      });
    }
  }
}
