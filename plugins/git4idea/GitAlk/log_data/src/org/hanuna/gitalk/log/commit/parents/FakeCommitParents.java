package org.hanuna.gitalk.log.commit.parents;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class FakeCommitParents implements CommitParents {

  private static final String FAKE_HASH_PREFIX = "aaaaaaaaaaaaa00000000";

  public static boolean isFake(Hash hash) {
    return isFake(hash.toStrHash());
  }

  public static boolean isFake(String hashStr) {
    return hashStr.startsWith(FAKE_HASH_PREFIX);
  }

  public static Hash getOriginal(Hash hash) {
    return Hash.build(getOriginal(hash.toStrHash()));
  }

  public static String getOriginal(String hash) {
    while (isFake(hash)) {
      hash = hash.substring(FAKE_HASH_PREFIX.length());
    }
    return hash;
  }

  private final RebaseCommand command;
  private final Hash fakeHash;
  private final Hash parent;

  public FakeCommitParents(@NotNull Hash parent, @NotNull RebaseCommand command) {
    this.parent = parent;
    this.command = command;
    this.fakeHash = Hash.build(FAKE_HASH_PREFIX + command.getCommit().toStrHash());
  }

  @NotNull
  @Override
  public Hash getCommitHash() {
    return fakeHash;
  }

  @NotNull
  @Override
  public List<Hash> getParentHashes() {
    return Collections.singletonList(parent);
  }

  @NotNull
  public RebaseCommand getCommand() {
    return command;
  }
}
