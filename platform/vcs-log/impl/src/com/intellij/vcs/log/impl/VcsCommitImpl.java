package com.intellij.vcs.log.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>We suppose that the Hash is the unique identifier of the Commit,
 *    i. e. it is the only value that should be checked in equals() and hashCode().</p>
 * <p>equals() and hashCode() are made final to ensure that any descendants of this class are considered equal
 *    if and only if their hashes are equals.</p>
 * <p>It is highly recommended to use this standard implementation of the VcsCommit because of the above reasons.</p>
 *
 * @author erokhins
 * @author Kirill Likhodedov
 */
public class VcsCommitImpl implements VcsCommit {

  @NotNull private final Hash myHash;
  @NotNull private final List<Hash> myParents;

  public VcsCommitImpl(@NotNull Hash hash, @NotNull List<Hash> parents) {
    myHash = hash;
    myParents = new ArrayList<Hash>(parents);
  }

  @Override
  @NotNull
  public final Hash getHash() {
    return myHash;
  }

  @Override
  @NotNull
  public final List<Hash> getParents() {
    return myParents;
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof VcsCommitImpl)) {
      return false;
    }
    return myHash.equals(((VcsCommitImpl)obj).myHash);
  }

  @Override
  public final int hashCode() {
    return myHash.hashCode();
  }

  @Override
  public String toString() {
    return myHash.toShortString() + "|-" + StringUtil.join(ContainerUtil.map(myParents, new Function<Hash, String>() {
      @Override
      public String fun(Hash hash) {
        return hash.toShortString();
      }
    }), ",");
  }

}
