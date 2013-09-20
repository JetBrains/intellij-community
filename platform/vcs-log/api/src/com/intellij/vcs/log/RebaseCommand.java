package com.intellij.vcs.log;

import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.Nullable;

public class RebaseCommand {
  public enum RebaseCommandKind {
    PICK,
    FIXUP,
    REWORD
  }

  private final RebaseCommandKind kind;
  private final Hash commit;
  private final String newMessage;

  public RebaseCommand(RebaseCommandKind kind, Hash commit) {
    this(kind, commit, null);
  }

  public RebaseCommand(RebaseCommandKind kind, Hash commit, @Nullable String newMessage) {
    this.kind = kind;
    this.commit = commit;
    this.newMessage = newMessage;
  }

  public RebaseCommandKind getKind() {
    return kind;
  }

  public Hash getCommit() {
    return commit;
  }

  @Nullable
  public String getNewMessage() {
    return newMessage;
  }

  @Override
  public String toString() {
    return kind + " " + getCommit().toStrHash() + (newMessage == null ? "" : " " + newMessage);
  }
}
