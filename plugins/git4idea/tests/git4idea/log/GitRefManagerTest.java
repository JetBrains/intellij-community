// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsRefImpl;
import git4idea.branch.GitBranchUtil;
import git4idea.test.GitSingleRepoTest;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.vcs.Executor.cd;

public abstract class GitRefManagerTest extends GitSingleRepoTest {
  @NotNull
  protected Collection<VcsRef> given(String @NotNull ... refs) {
    Collection<VcsRef> result = new ArrayList<>();
    cd(getProjectRoot());
    Hash hash = HashImpl.build(git("rev-parse HEAD"));
    for (String refName : refs) {
      if (isHead(refName)) {
        result.add(ref(hash, "HEAD", GitRefManager.HEAD));
      }
      else if (isRemoteBranch(refName)) {
        git("update-ref refs/remotes/" + refName + " " + hash.asString());
        result.add(ref(hash, refName, GitRefManager.REMOTE_BRANCH));
      }
      else if (isTag(refName)) {
        git("update-ref " + refName + " " + hash.asString());
        result.add(ref(hash, GitBranchUtil.stripRefsPrefix(refName), GitRefManager.TAG));
      }
      else {
        git("update-ref refs/heads/" + refName + " " + hash.asString());
        result.add(ref(hash, refName, GitRefManager.LOCAL_BRANCH));
      }
    }
    setUpTracking(result);
    repo.update();
    return result;
  }

  @NotNull
  protected List<VcsRef> expect(String @NotNull ... refNames) {
    final Set<VcsRef> refs = GitTestUtil.readAllRefs(this, getProjectRoot(), myProject.getService(VcsLogObjectsFactory.class));
    return ContainerUtil.map(refNames, refName -> {
      VcsRef item = ContainerUtil.find(refs, ref -> ref.getName().equals(GitBranchUtil.stripRefsPrefix(refName)));
      assertNotNull("Ref " + refName + " not found among " + refs, item);
      return item;
    });
  }

  private static boolean isHead(String name) {
    return name.equals("HEAD");
  }

  private static boolean isTag(String name) {
    return name.startsWith("refs/tags/");
  }

  private static boolean isRemoteBranch(String name) {
    return name.startsWith("origin/");
  }

  private VcsRef ref(Hash hash, String name, VcsRefType type) {
    return new VcsRefImpl(hash, name, type, getProjectRoot());
  }

  private void setUpTracking(@NotNull Collection<? extends VcsRef> refs) {
    cd(getProjectRoot());
    for (final VcsRef ref : refs) {
      if (ref.getType() == GitRefManager.LOCAL_BRANCH) {
        final String localBranch = ref.getName();
        if (ContainerUtil.exists(refs, remoteRef -> remoteRef.getType() == GitRefManager.REMOTE_BRANCH && remoteRef.getName().replace("origin/", "").equals(localBranch))) {
          git("config branch." + localBranch + ".remote origin");
          git("config branch." + localBranch + ".merge refs/heads/" + localBranch);
        }
      }
    }
  }
}
