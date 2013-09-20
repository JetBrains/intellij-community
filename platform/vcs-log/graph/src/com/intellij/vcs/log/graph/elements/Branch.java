package com.intellij.vcs.log.graph.elements;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public final class Branch {
  private final Hash upCommitHash;
  private final Hash downCommitHash;
  @Nullable private final VcsRef myRef;
  @NotNull private final VirtualFile myRepositoryRoot;

  public Branch(@NotNull Hash upCommitHash, @NotNull Hash downCommitHash, @Nullable VcsRef ref, @NotNull VirtualFile repositoryRoot) {
    this.upCommitHash = upCommitHash;
    this.downCommitHash = downCommitHash;
    myRef = ref;
    myRepositoryRoot = repositoryRoot;
  }

  public Branch(@NotNull Hash commit, @Nullable VcsRef ref, @NotNull VirtualFile repositoryRoot) {
    this(commit, commit, ref, repositoryRoot);
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
    if (myRef == null || myRef.getType() == VcsRef.RefType.TAG) {
      return upCommitHash.hashCode() + 73 * downCommitHash.hashCode();
    }
    return myRef.getName().hashCode();
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
      return upCommitHash.toStrHash();
    }
    else {
      return upCommitHash.toStrHash() + '#' + downCommitHash.toStrHash();
    }
  }

  @NotNull
  public VirtualFile getRepositoryRoot() {
    return myRepositoryRoot;
  }

  @Nullable
  public VcsRef getRef() {
    return myRef;
  }
}
