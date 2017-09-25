package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SimpleRefGroup;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitTag;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitRefManager implements VcsLogRefManager {
  public static final VcsRefType HEAD = new SimpleRefType(true, VcsLogStandardColors.Refs.TIP, "HEAD");
  public static final VcsRefType LOCAL_BRANCH = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH, "LOCAL_BRANCH");
  public static final VcsRefType REMOTE_BRANCH = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH_REF, "REMOTE_BRANCH");
  public static final VcsRefType TAG = new SimpleRefType(false, VcsLogStandardColors.Refs.TAG, "TAG");
  public static final VcsRefType OTHER = new SimpleRefType(false, VcsLogStandardColors.Refs.TAG, "OTHER");

  private static final List<VcsRefType> REF_TYPE_INDEX = Arrays.asList(HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG, OTHER);

  public static final String MASTER = "master";
  public static final String ORIGIN_MASTER = "origin/master";
  private static final Logger LOG = Logger.getInstance(GitRefManager.class);
  private static final String REMOTE_TABLE_SEPARATOR = " & ";
  private static final String SEPARATOR = "/";

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
  public List<RefGroup> groupForBranchFilter(@NotNull Collection<VcsRef> refs) {
    List<RefGroup> simpleGroups = ContainerUtil.newArrayList();
    List<VcsRef> localBranches = ContainerUtil.newArrayList();
    List<VcsRef> trackedBranches = ContainerUtil.newArrayList();
    MultiMap<GitRemote, VcsRef> remoteRefGroups = MultiMap.create();

    MultiMap<VirtualFile, VcsRef> refsByRoot = groupRefsByRoot(refs);
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : refsByRoot.entrySet()) {
      VirtualFile root = entry.getKey();
      List<VcsRef> refsInRoot = ContainerUtil.sorted(entry.getValue(), myLabelsComparator);

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
    if (!localBranches.isEmpty()) result.add(new LogicalRefGroup("Local", localBranches));
    if (!trackedBranches.isEmpty()) result.add(new LogicalRefGroup("Tracked", trackedBranches));
    for (Map.Entry<GitRemote, Collection<VcsRef>> entry : remoteRefGroups.entrySet()) {
      final GitRemote remote = entry.getKey();
      final Collection<VcsRef> branches = entry.getValue();
      result.add(new RemoteRefGroup(remote, branches));
    }
    return result;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForTable(@NotNull Collection<VcsRef> references, boolean compact, boolean showTagNames) {
    List<VcsRef> sortedReferences = ContainerUtil.sorted(references, myLabelsComparator);
    MultiMap<VcsRefType, VcsRef> groupedRefs = ContainerUtil.groupBy(sortedReferences, VcsRef::getType);

    List<RefGroup> result = ContainerUtil.newArrayList();
    if (groupedRefs.isEmpty()) return result;

    VcsRef head = null;
    Map.Entry<VcsRefType, Collection<VcsRef>> firstGroup = ObjectUtils.notNull(ContainerUtil.getFirstItem(groupedRefs.entrySet()));
    if (firstGroup.getKey().equals(HEAD)) {
      head = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(firstGroup.getValue()));
      groupedRefs.remove(HEAD, head);
    }

    GitRepository repository = getRepository(references);
    if (repository != null) {
      result.addAll(getTrackedRefs(groupedRefs, repository));
    }
    result.forEach(refGroup -> {
      groupedRefs.remove(LOCAL_BRANCH, refGroup.getRefs().get(0));
      groupedRefs.remove(REMOTE_BRANCH, refGroup.getRefs().get(1));
    });

    SimpleRefGroup.buildGroups(groupedRefs, compact, showTagNames, result);

    if (head != null) {
      if (repository != null && !repository.isOnBranch()) {
        result.add(0, new SimpleRefGroup("!", Collections.singletonList(head)));
      }
      else {
        if (!result.isEmpty()) {
          RefGroup first = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(result));
          first.getRefs().add(0, head);
        }
        else {
          result.add(0, new SimpleRefGroup("", Collections.singletonList(head)));
        }
      }
    }

    return result;
  }

  @NotNull
  private static List<RefGroup> getTrackedRefs(@NotNull MultiMap<VcsRefType, VcsRef> groupedRefs,
                                               @NotNull GitRepository repository) {
    List<RefGroup> result = ContainerUtil.newArrayList();

    Collection<VcsRef> locals = groupedRefs.get(LOCAL_BRANCH);
    Collection<VcsRef> remotes = groupedRefs.get(REMOTE_BRANCH);

    for (VcsRef localRef : locals) {
      SimpleRefGroup group = createTrackedGroup(repository, remotes, localRef);
      if (group != null) {
        result.add(group);
      }
    }

    return result;
  }

  @Nullable
  private static SimpleRefGroup createTrackedGroup(@NotNull GitRepository repository,
                                                   @NotNull Collection<VcsRef> references,
                                                   @NotNull VcsRef localRef) {
    List<VcsRef> remoteBranches = ContainerUtil.filter(references, ref -> ref.getType().equals(REMOTE_BRANCH));

    GitBranchTrackInfo trackInfo =
      ContainerUtil.find(repository.getBranchTrackInfos(), info -> info.getLocalBranch().getName().equals(localRef.getName()));
    if (trackInfo != null) {
      VcsRef trackedRef = ContainerUtil.find(remoteBranches, ref -> ref.getName().equals(trackInfo.getRemoteBranch().getName()));
      if (trackedRef != null) {
        return new SimpleRefGroup(trackInfo.getRemote().getName() + REMOTE_TABLE_SEPARATOR + localRef.getName(),
                                  ContainerUtil.newArrayList(localRef, trackedRef));
      }
    }

    List<VcsRef> trackingCandidates = ContainerUtil.filter(remoteBranches, ref -> ref.getName().endsWith(SEPARATOR + localRef.getName()));
    for (GitRemote remote : repository.getRemotes()) {
      for (VcsRef candidate : trackingCandidates) {
        if (candidate.getName().equals(remote.getName() + SEPARATOR + localRef.getName())) {
          return new SimpleRefGroup(remote.getName() + REMOTE_TABLE_SEPARATOR + localRef.getName(),
                                    ContainerUtil.newArrayList(localRef, candidate));
        }
      }
    }

    return null;
  }

  @Nullable
  private GitRepository getRepository(@NotNull Collection<VcsRef> references) {
    if (references.isEmpty()) return null;

    VcsRef ref = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(references));
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(ref.getRoot());
    if (repository == null) {
      LOG.warn("No repository for root: " + ref.getRoot());
    }
    return repository;
  }

  @Override
  public void serialize(@NotNull DataOutput out, @NotNull VcsRefType type) throws IOException {
    out.writeInt(REF_TYPE_INDEX.indexOf(type));
  }

  @NotNull
  @Override
  public VcsRefType deserialize(@NotNull DataInput in) throws IOException {
    int id = in.readInt();
    if (id < 0 || id > REF_TYPE_INDEX.size() - 1) throw new IOException("Reference type by id " + id + " does not exist");
    return REF_TYPE_INDEX.get(id);
  }

  private static Set<String> getLocalBranches(GitRepository repository) {
    return ContainerUtil.map2Set(repository.getBranches().getLocalBranches(), (Function<GitBranch, String>)branch -> branch.getName());
  }

  @NotNull
  private static Set<String> getTrackedRemoteBranches(@NotNull GitRepository repository) {
    Set<GitRemoteBranch> all = new HashSet<>(repository.getBranches().getRemoteBranches());
    Set<String> tracked = new HashSet<>();
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
    Set<GitRemoteBranch> all = new HashSet<>(repository.getBranches().getRemoteBranches());
    Map<String, GitRemote> allRemote = ContainerUtil.newHashMap();
    for (GitRemoteBranch remoteBranch : all) {
      allRemote.put(remoteBranch.getName(), remoteBranch.getRemote());
    }
    return allRemote;
  }

  private static Set<String> getTrackedRemoteBranchesFromConfig(GitRepository repository) {
    return ContainerUtil.map2Set(repository.getBranchTrackInfos(), trackInfo -> trackInfo.getRemoteBranch().getName());
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
    @NotNull private final String myName;

    public SimpleRefType(boolean isBranch, @NotNull Color color, @NotNull String typeName) {
      myIsBranch = isBranch;
      myColor = color;
      myName = typeName;
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

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SimpleRefType type = (SimpleRefType)o;
      return myIsBranch == type.myIsBranch && Objects.equals(myName, type.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myIsBranch, myName);
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
    public List<Color> getColors() {
      return Collections.singletonList(VcsLogStandardColors.Refs.TIP);
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
    public List<Color> getColors() {
      return Collections.singletonList(VcsLogStandardColors.Refs.BRANCH_REF);
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
      return ContainerUtil.exists(repo.getBranchTrackInfos(), info -> remoteBranch ?
                                                                  info.getRemoteBranch().getNameForLocalOperations().equals(ref.getName()) :
                                                                  info.getLocalBranch().getName().equals(ref.getName()));
    }
  }
}
