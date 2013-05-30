package org.hanuna.gitalk.graph.elements;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author erokhins
 */
public final class Branch {
  private final Hash upCommitHash;
  private final Hash downCommitHash;
  @Nullable private final Ref myRef;

  public Branch(@NotNull Hash upCommitHash, @NotNull Hash downCommitHash, Collection<Ref> refs) {
    this.upCommitHash = upCommitHash;
    this.downCommitHash = downCommitHash;
    myRef = findUpRef(upCommitHash, refs);
  }

  public Branch(Hash commit, Collection<Ref> refs) {
    this(commit, commit, refs);
  }

  @Nullable
  private static Ref findUpRef(Hash upCommitHash, Collection<Ref> refs) {
    for (Ref ref : refs) {
      if (ref.getType() != Ref.RefType.TAG && ref.getCommitHash().equals(upCommitHash)) {
        return ref;
      }
    }
    return null;
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
    if (myRef == null) {
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
}
