package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class VcsRef {

  @NotNull private final Hash myCommitHash;
  @NotNull private final String myName;
  @NotNull private final RefType myType;
  @NotNull private final VirtualFile myRoot;

  public VcsRef(@NotNull Hash commitHash, @NotNull String name, @NotNull RefType type, @NotNull VirtualFile root) {
    myCommitHash = commitHash;
    myName = name;
    myType = type;
    myRoot = root;
  }

  @NotNull
  public RefType getType() {
    return myType;
  }

  @NotNull
  public Hash getCommitHash() {
    return myCommitHash;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("%s:%s(%s|%s)", myRoot.getName(), myName, myCommitHash, myType);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRef ref = (VcsRef)o;

    if (!myCommitHash.equals(ref.myCommitHash)) return false;
    if (!myName.equals(ref.myName)) return false;
    if (!myRoot.equals(ref.myRoot)) return false;
    if (myType != ref.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCommitHash.hashCode();
    result = 31 * result + (myName.hashCode());
    result = 31 * result + (myRoot.hashCode());
    result = 31 * result + (myType.hashCode());
    return result;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  public enum RefType {
    LOCAL_BRANCH,
    REMOTE_BRANCH,
    TAG,
    ANOTHER,
    HEAD;

    public boolean isBranch() {
      return this == LOCAL_BRANCH || this == REMOTE_BRANCH || this == HEAD;
    }

    public boolean isLocalOrHead() {
      return this == LOCAL_BRANCH || this == HEAD;
    }

  }
}
