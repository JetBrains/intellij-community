// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SimpleRefGroup;
import com.intellij.vcs.log.impl.SimpleRefType;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.util.VcsLogUtil;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitTag;
import git4idea.branch.GitBranchType;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static com.intellij.ui.JBColor.namedColor;

/**
 * @author Kirill Likhodedov
 */
public class GitRefManager implements VcsLogRefManager {
  private static final JBColor HEAD_COLOR = namedColor("VersionControl.GitLog.headIconColor", VcsLogStandardColors.Refs.TIP);
  private static final JBColor LOCAL_BRANCH_COLOR = namedColor("VersionControl.GitLog.localBranchIconColor", VcsLogStandardColors.Refs.BRANCH);
  private static final JBColor REMOTE_BRANCH_COLOR = namedColor("VersionControl.GitLog.remoteBranchIconColor", VcsLogStandardColors.Refs.BRANCH_REF);
  private static final JBColor TAG_COLOR = namedColor("VersionControl.GitLog.tagIconColor", VcsLogStandardColors.Refs.TAG);
  private static final JBColor OTHER_COLOR = namedColor("VersionControl.GitLog.otherIconColor", VcsLogStandardColors.Refs.TAG);

  public static final VcsRefType HEAD = new SimpleRefType("HEAD", true, HEAD_COLOR);
  public static final VcsRefType LOCAL_BRANCH = new SimpleRefType("LOCAL_BRANCH", true, LOCAL_BRANCH_COLOR);
  public static final VcsRefType REMOTE_BRANCH = new SimpleRefType("REMOTE_BRANCH", true, REMOTE_BRANCH_COLOR);
  public static final VcsRefType TAG = new SimpleRefType("TAG", false, TAG_COLOR);
  public static final VcsRefType OTHER = new SimpleRefType("OTHER", false, OTHER_COLOR);

  private static final List<VcsRefType> REF_TYPE_INDEX = Arrays.asList(HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG, OTHER);

  public static final String MASTER = "master";
  public static final String MAIN = "main";
  public static final String ORIGIN_MASTER = "origin/master";
  public static final String ORIGIN_MAIN = "origin/main";
  private static final Logger LOG = Logger.getInstance(GitRefManager.class);
  private static final String REMOTE_TABLE_SEPARATOR = " & ";
  private static final String SEPARATOR = "/";

  protected enum RefType {
    OTHER,
    HEAD,
    CURRENT_BRANCH,
    TAG,
    LOCAL_BRANCH,
    MASTER,
    REMOTE_BRANCH,
    ORIGIN_MASTER
  }

  @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;
  @NotNull private final Comparator<VcsRef> myLabelsComparator;
  @NotNull private final Comparator<VcsRef> myBranchLayoutComparator;
  @NotNull private final GitBranchManager myBranchManager;

  public GitRefManager(@NotNull Project project, @NotNull RepositoryManager<GitRepository> repositoryManager) {
    myRepositoryManager = repositoryManager;
    myBranchLayoutComparator = new GitBranchLayoutComparator();
    myLabelsComparator = new GitLabelComparator(myRepositoryManager);
    myBranchManager = project.getService(GitBranchManager.class);
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
  public List<RefGroup> groupForBranchFilter(@NotNull Collection<? extends VcsRef> refs) {
    List<RefGroup> simpleGroups = new ArrayList<>();
    List<VcsRef> localBranches = new ArrayList<>();
    MultiMap<GitRemote, VcsRef> remoteRefGroups = MultiMap.create();

    MultiMap<VirtualFile, VcsRef> refsByRoot = groupRefsByRoot(refs);
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : refsByRoot.entrySet()) {
      VirtualFile root = entry.getKey();
      List<VcsRef> refsInRoot = ContainerUtil.sorted(entry.getValue(), myLabelsComparator);

      GitRepository repository = myRepositoryManager.getRepositoryForRootQuick(root);
      if (repository == null) {
        LOG.warn("No repository for root: " + root);
        continue;
      }

      Set<String> locals = getLocalBranches(repository);
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
        }
        else {
          LOG.debug("Didn't find ref neither in local nor in remote branches: " + ref);
        }
      }
    }

    List<RefGroup> result = new ArrayList<>(simpleGroups);
    if (!localBranches.isEmpty()) result.add(new SimpleRefGroup(GitBundle.message("git.log.refGroup.local"), localBranches, false));
    for (Map.Entry<GitRemote, Collection<VcsRef>> entry : remoteRefGroups.entrySet()) {
      result.add(new RemoteRefGroup(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForTable(@NotNull Collection<? extends VcsRef> references, boolean compact, boolean showTagNames) {
    List<VcsRef> sortedReferences = ContainerUtil.sorted(references, myLabelsComparator);
    MultiMap<VcsRefType, VcsRef> groupedRefs = ContainerUtil.groupBy(sortedReferences, VcsRef::getType);

    List<RefGroup> result = new ArrayList<>();
    if (groupedRefs.isEmpty()) return result;

    VcsRef head = null;
    Map.Entry<VcsRefType, Collection<VcsRef>> firstGroup = Objects.requireNonNull(ContainerUtil.getFirstItem(groupedRefs.entrySet()));
    if (firstGroup.getKey().equals(HEAD)) {
      head = Objects.requireNonNull(ContainerUtil.getFirstItem(firstGroup.getValue()));
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
          RefGroup first = Objects.requireNonNull(ContainerUtil.getFirstItem(result));
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
    List<RefGroup> result = new ArrayList<>();

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
                                                   @NotNull Collection<? extends VcsRef> references,
                                                   @NotNull VcsRef localRef) {
    List<VcsRef> remoteBranches = ContainerUtil.filter(references, ref -> ref.getType().equals(REMOTE_BRANCH));

    GitBranchTrackInfo trackInfo =
      ContainerUtil.find(repository.getBranchTrackInfos(), info -> info.getLocalBranch().getName().equals(localRef.getName()));
    if (trackInfo != null) {
      VcsRef trackedRef = ContainerUtil.find(remoteBranches, ref -> ref.getName().equals(trackInfo.getRemoteBranch().getName()));
      if (trackedRef != null) {
        return new SimpleRefGroup(trackInfo.getRemote().getName() + REMOTE_TABLE_SEPARATOR + localRef.getName(),
                                  new ArrayList<>(Arrays.asList(localRef, trackedRef)));
      }
    }

    List<VcsRef> trackingCandidates = ContainerUtil.filter(remoteBranches, ref -> ref.getName().endsWith(SEPARATOR + localRef.getName()));
    for (GitRemote remote : repository.getRemotes()) {
      for (VcsRef candidate : trackingCandidates) {
        if (candidate.getName().equals(remote.getName() + SEPARATOR + localRef.getName())) {
          return new SimpleRefGroup(remote.getName() + REMOTE_TABLE_SEPARATOR + localRef.getName(),
                                    new ArrayList<>(Arrays.asList(localRef, candidate)));
        }
      }
    }

    return null;
  }

  @Nullable
  private GitRepository getRepository(@NotNull Collection<? extends VcsRef> references) {
    if (references.isEmpty()) return null;

    VcsRef ref = Objects.requireNonNull(ContainerUtil.getFirstItem(references));
    GitRepository repository = getRepository(ref);
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

  @NotNull
  private static GitBranchType getBranchType(@NotNull VcsRef reference) {
    return reference.getType().equals(LOCAL_BRANCH) ? GitBranchType.LOCAL : GitBranchType.REMOTE;
  }

  @Nullable
  private GitRepository getRepository(@NotNull VcsRef reference) {
    return myRepositoryManager.getRepositoryForRootQuick(reference.getRoot());
  }

  @Override
  public boolean isFavorite(@NotNull VcsRef reference) {
    if (reference.getType().equals(HEAD)) return true;
    if (!reference.getType().isBranch()) return false;
    return myBranchManager.isFavorite(getBranchType(reference), getRepository(reference), reference.getName());
  }

  @Override
  public void setFavorite(@NotNull VcsRef reference, boolean favorite) {
    if (reference.getType().equals(HEAD)) return;
    if (!reference.getType().isBranch()) return;
    myBranchManager.setFavorite(getBranchType(reference), getRepository(reference), reference.getName(), favorite);
  }

  private static Set<String> getLocalBranches(GitRepository repository) {
    return ContainerUtil.map2Set(repository.getBranches().getLocalBranches(), (Function<GitBranch, String>)branch -> branch.getName());
  }

  @NotNull
  private static Map<String, GitRemote> getAllRemoteBranches(@NotNull GitRepository repository) {
    Set<GitRemoteBranch> all = new HashSet<>(repository.getBranches().getRemoteBranches());
    Map<String, GitRemote> allRemote = new HashMap<>();
    for (GitRemoteBranch remoteBranch : all) {
      allRemote.put(remoteBranch.getName(), remoteBranch.getRemote());
    }
    return allRemote;
  }

  @NotNull
  private static MultiMap<VirtualFile, VcsRef> groupRefsByRoot(@NotNull Iterable<? extends VcsRef> refs) {
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

  private class RemoteRefGroup implements RefGroup {
    private final GitRemote myRemote;
    private final Collection<? extends VcsRef> myBranches;

    RemoteRefGroup(GitRemote remote, Collection<? extends VcsRef> branches) {
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
      RefType.CURRENT_BRANCH,
      RefType.MASTER,
      RefType.ORIGIN_MASTER,
      RefType.LOCAL_BRANCH,
      RefType.REMOTE_BRANCH,
      RefType.TAG,
      RefType.OTHER
    };
    @NotNull private final RepositoryManager<GitRepository> myRepositoryManager;

    private GitLabelComparator(@NotNull RepositoryManager<GitRepository> repositoryManager) {
      myRepositoryManager = repositoryManager;
    }

    @Override
    protected RefType[] getOrderedTypes() {
      return ORDERED_TYPES;
    }

    @Override
    protected @NotNull RefType getType(@NotNull VcsRef ref) {
      RefType type = super.getType(ref);
      if (type == RefType.LOCAL_BRANCH || type == RefType.MASTER) {
        if (isCurrentBranch(ref)) {
          return RefType.CURRENT_BRANCH;
        }
      }
      return type;
    }

    private boolean isCurrentBranch(@NotNull VcsRef ref) {
      GitRepository repo = myRepositoryManager.getRepositoryForRootQuick(ref.getRoot());
      if (repo == null) return false;
      GitLocalBranch currentBranch = repo.getCurrentBranch();
      if (currentBranch == null) return false;
      return currentBranch.getName().equals(ref.getName());
    }
  }

  private static class GitBranchLayoutComparator extends GitRefComparator {
    private static final RefType[] ORDERED_TYPES = {
      RefType.ORIGIN_MASTER,
      RefType.REMOTE_BRANCH,
      RefType.MASTER,
      RefType.LOCAL_BRANCH,
      RefType.TAG,
      RefType.CURRENT_BRANCH,
      RefType.HEAD,
      RefType.OTHER
    };

    @Override
    protected RefType[] getOrderedTypes() {
      return ORDERED_TYPES;
    }
  }

  private abstract static class GitRefComparator implements Comparator<VcsRef> {

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
    protected RefType getType(@NotNull VcsRef ref) {
      VcsRefType type = ref.getType();
      String name = ref.getName();
      if (type == HEAD) {
        return RefType.HEAD;
      }
      else if (type == TAG) {
        return RefType.TAG;
      }
      else if (type == LOCAL_BRANCH) {
        if (name.equals(MASTER) || name.equals(MAIN)) {
          return RefType.MASTER;
        }
        return RefType.LOCAL_BRANCH;
      }
      else if (type == REMOTE_BRANCH) {
        if (name.equals(ORIGIN_MASTER) || name.equals(ORIGIN_MAIN)) {
          return RefType.ORIGIN_MASTER;
        }
        return RefType.REMOTE_BRANCH;
      }
      else {
        return RefType.OTHER;
      }
    }
  }
}
