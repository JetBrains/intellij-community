package git4idea.log;

import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsRefImpl;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.*;
import git4idea.test.GitTestRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitLogRefSorterTest extends UsefulTestCase {

  public static final MockVirtualFile MOCK_VIRTUAL_FILE = new MockVirtualFile("mockFile");

  public void testEmpty() {
    check(Collections.<VcsRef>emptyList(), Collections.<VcsRef>emptyList());
  }

  public void testSingle() {
    check(given("HEAD"),
          expect("HEAD"));
  }

  public void testHeadIsMoreImportantThanBranch() {
    check(given("master", "HEAD"),
          expect("HEAD", "master"));
  }

  public void testLocalBranchesAreComparedAsStrings() {
    check(given("release", "feature"),
          expect("feature", "release"));
  }

  public void testTagIsTheLessImportant() {
    check(given("tag/v1", "origin/master"),
          expect("origin/master", "tag/v1"));
  }

  public void testMasterIsMoreImportant() {
    check(given("feature", "master"),
          expect("master", "feature"));
  }

  public void testOriginMasterIsMoreImportant() {
    check(given("origin/master", "origin/aaa"),
          expect("origin/master", "origin/aaa"));
  }

  public void testRemoteBranchHavingTrackingBranchIsMoreImportant() {
    check(given("feature", "origin/aaa", "origin/feature"),
          expect("feature", "origin/feature", "origin/aaa"));
  }

  public void testSeveral1() {
    check(given("tag/v1", "feature", "HEAD", "master"),
          expect("HEAD", "master", "feature", "tag/v1"));
  }

  public void testSeveral2() {
    check(given("origin/master", "origin/great_feature", "tag/v1", "release", "HEAD", "master"),
          expect("HEAD", "master", "release", "origin/master", "origin/great_feature", "tag/v1"));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

  }

  private static Collection<VcsRef> given(String... refs) {
    return convertToRefs(refs);
  }

  private static List<VcsRef> expect(String... refs) {
    return new ArrayList<VcsRef>(convertToRefs(refs));
  }

  private static List<VcsRef> convertToRefs(String[] refs) {
    return ContainerUtil.map(refs, new Function<String, VcsRef>() {
      @Override
      public VcsRef fun(String name) {
        return ref(name);
      }
    });
  }

  private static VcsRef ref(String name) {
    String randomHash = randomHash();
    if (isHead(name)) {
      return ref(randomHash, name, GitRefManager.HEAD);
    }
    if (isRemoteBranch(name)) {
      return ref(randomHash, name, GitRefManager.REMOTE_BRANCH);
    }
    if (isTag(name)) {
      return ref(randomHash, name, GitRefManager.TAG);
    }
    return ref(randomHash, name, GitRefManager.LOCAL_BRANCH);
  }

  private static String randomHash() {
    return String.valueOf(new Random().nextInt());
  }

  private static boolean isHead(String name) {
    return name.equals("HEAD");
  }

  private static boolean isTag(String name) {
    return name.startsWith("tag/");
  }

  private static boolean isRemoteBranch(String name) {
    return name.startsWith("origin/");
  }

  private static boolean isLocalBranch(String name) {
    return !isHead(name) && !isTag(name) && !isRemoteBranch(name);
  }

  private static VcsRef ref(String hash, String name, VcsRefType type) {
    return new VcsRefImpl(new NotNullFunction<Hash, Integer>() {
      @NotNull
      @Override
      public Integer fun(Hash hash) {
        return Integer.parseInt(hash.asString().substring(0, Math.min(4, hash.asString().length())), 16);
      }
    }, HashImpl.build(hash), name, type, MOCK_VIRTUAL_FILE);
  }

  private static void check(Collection<VcsRef> unsorted, List<VcsRef> expected) {
    // for the sake of simplicity we check only names of references
    List<VcsRef> actual = sort(unsorted);
    assertEquals("Collections size don't match", expected.size(), actual.size());
    for (int i = 0; i < actual.size(); i++) {
      assertEquals("Incorrect element at place " + i, expected.get(i).getName(), actual.get(i).getName());
    }
  }

  private static List<VcsRef> sort(final Collection<VcsRef> refs) {
    final GitTestRepositoryManager manager = new GitTestRepositoryManager();
    manager.add(new MockGitRepository() {
      @NotNull
      @Override
      public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
        List<GitBranchTrackInfo> infos = new ArrayList<GitBranchTrackInfo>();
        List<VcsRef> remoteRefs = ContainerUtil.findAll(refs, new Condition<VcsRef>() {
          @Override
          public boolean value(VcsRef ref) {
            return isRemoteBranch(ref.getName());
          }
        });
        List<VcsRef> localRefs = ContainerUtil.findAll(refs, new Condition<VcsRef>() {
          @Override
          public boolean value(VcsRef ref) {
            return isLocalBranch(ref.getName());
          }
        });

        for (final VcsRef localRef : localRefs) {
          final VcsRef trackedRef = ContainerUtil.find(remoteRefs, new Condition<VcsRef>() {
            @Override
            public boolean value(VcsRef remoteRef) {
              return localRef.getName().equals(remoteRef.getName().substring("origin/".length()));
            }
          });
          if (trackedRef != null) {
            infos.add(new GitBranchTrackInfo(new GitLocalBranch(localRef.getName(), HashImpl.build(randomHash())),
                                             new GitRemoteBranch(trackedRef.getName(), HashImpl.build(randomHash())) {
                                               @NotNull
                                               @Override
                                               public String getNameForRemoteOperations() {
                                                 return trackedRef.getName().substring("origin/".length());
                                               }

                                               @NotNull
                                               @Override
                                               public String getNameForLocalOperations() {
                                                 return trackedRef.getName();
                                               }

                                               @NotNull
                                               @Override
                                               public GitRemote getRemote() {
                                                 return GitRemote.DOT;
                                               }

                                               @Override
                                               public boolean isRemote() {
                                                 return true;
                                               }
                                             }, true));
          }
        }
        return infos;
      }
    });
    return new GitRefManager(manager).sort(refs);
  }

  // TODO either use the real GitRepository, or move upwards and make more generic implementation
  private static class MockGitRepository implements GitRepository {
    @NotNull
    @Override
    public VirtualFile getGitDir() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public GitUntrackedFilesHolder getUntrackedFilesHolder() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public GitRepoInfo getInfo() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public GitLocalBranch getCurrentBranch() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public GitBranchesCollection getBranches() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<GitRemote> getRemotes() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRebaseInProgress() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOnBranch() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VirtualFile getRoot() {
      return MOCK_VIRTUAL_FILE;
    }

    @NotNull
    @Override
    public String getPresentableUrl() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Project getProject() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public State getState() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getCurrentRevision() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFresh() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String toLogString() {
      throw new UnsupportedOperationException();
    }
  }
}
