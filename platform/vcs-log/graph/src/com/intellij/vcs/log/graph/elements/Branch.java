package com.intellij.vcs.log.graph.elements;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author erokhins
 */
public final class Branch {
  private final Hash upCommitHash;
  private final Hash downCommitHash;
  @Nullable private final VcsRef myColoredRef;
  @NotNull private final VirtualFile myRepositoryRoot;

  public Branch(@NotNull Hash upCommitHash, @NotNull Hash downCommitHash, @NotNull Collection<VcsRef> refs,
                @NotNull VirtualFile repositoryRoot) {
    this.upCommitHash = upCommitHash;
    this.downCommitHash = downCommitHash;
    myRepositoryRoot = repositoryRoot;
    myColoredRef = findRefForBranchColor(refs);
  }

  @Nullable
  private static VcsRef findRefForBranchColor(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.find(refs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });
  }

  public Branch(@NotNull Hash commit, @NotNull Collection<VcsRef> refs, @NotNull VirtualFile repositoryRoot) {
    this(commit, commit, refs, repositoryRoot);
  }

  @NotNull
  public Hash getUpCommitHash() {
    return upCommitHash;
  }

  @NotNull
  public Hash getDownCommitHash() {
    return downCommitHash;
  }

  public int getBranchNumber() {
    if (myColoredRef == null) {
      return upCommitHash.hashCode() + 73 * downCommitHash.hashCode();
    }
    return myColoredRef.getName().hashCode();
  }

  @Override
  public int hashCode() {
    return upCommitHash.hashCode() + 73 * downCommitHash.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj.getClass() == Branch.class) {
      Branch anBranch = (Branch)obj;
      return anBranch.upCommitHash == upCommitHash && anBranch.downCommitHash == downCommitHash;
    }
    return false;
  }

  @Override
  public String toString() {
    if (upCommitHash == downCommitHash) {
      return upCommitHash.asString();
    }
    else {
      return upCommitHash.asString() + '#' + downCommitHash.asString();
    }
  }

  @NotNull
  public VirtualFile getRepositoryRoot() {
    return myRepositoryRoot;
  }

}
