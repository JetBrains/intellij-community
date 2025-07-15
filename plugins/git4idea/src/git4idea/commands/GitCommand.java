// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 *   The descriptor of git command.
 * </p>
 * <p>
 *   It contains policy information about locking which is handled in {@link Git#runCommand(GitLineHandler)} to prevent
 *   simultaneous Git commands conflict on the index.lock file.
 *   write-commands can't be executed simultaneously, but a write-command doesn't prevent read-commands to execute.
 * </p>
 * <p>
 *   A lock-policy can be different for a single command, for example, {@code git stash} may change the index (and thus should hold the
 *   write lock), which {@code git stash list} doesn't (and therefore no lock is needed).
 * </p>
 */
public final class GitCommand {

  public static final GitCommand ADD = write("add");
  public static final GitCommand BLAME = read("blame");
  public static final GitCommand BRANCH = read("branch");
  public static final GitCommand FOR_EACH_REF = read("for-each-ref");
  public static final GitCommand CAT_FILE = read("cat-file");
  public static final GitCommand CHECKOUT = write("checkout");
  public static final GitCommand SPARSE_CHECKOUT = write("sparse-checkout");
  public static final GitCommand CHECK_ATTR = read("check-attr");
  public static final GitCommand CHECK_IGNORE = read("check-ignore");
  public static final GitCommand COMMIT = write("commit");
  public static final GitCommand COMMIT_TREE = write("commit-tree");
  public static final GitCommand CONFIG = read("config");
  public static final GitCommand CHERRY = read("cherry");
  public static final GitCommand CHERRY_PICK = write("cherry-pick");
  public static final GitCommand CLONE = read("clone"); // write, but can't interfere with any other command => should be treated as read
  public static final GitCommand DIFF = read("diff");
  public static final GitCommand FETCH = read("fetch"); // fetch is a read-command, because it doesn't modify the index
  public static final GitCommand INIT = write("init");
  public static final GitCommand LOG = read("log");
  public static final GitCommand SHORTLOG = read("shortlog");
  public static final GitCommand LS_FILES = readOptional("ls-files");
  public static final GitCommand LS_TREE = read("ls-tree");
  public static final GitCommand LS_REMOTE = read("ls-remote");
  public static final GitCommand MERGE = write("merge");
  public static final GitCommand MERGE_BASE = read("merge-base");
  public static final GitCommand MV = write("mv");
  public static final GitCommand PULL = write("pull");
  public static final GitCommand PUSH = read("push"); // push is a read-command, because it doesn't modify the index. We still benefit from COMMIT & Co being write-commands, preventing HEAD from moving.
  public static final GitCommand REBASE = write("rebase");
  public static final GitCommand REMOTE = read("remote");
  public static final GitCommand RESET = write("reset");
  public static final GitCommand RESTORE = write("restore");
  public static final GitCommand REVERT = write("revert");
  public static final GitCommand REV_LIST = read("rev-list");
  public static final GitCommand REV_PARSE = read("rev-parse");
  public static final GitCommand REF_LOG = read("reflog");
  public static final GitCommand RM = write("rm");
  public static final GitCommand SHOW = read("show");
  public static final GitCommand STASH = write("stash");
  public static final GitCommand STATUS = readOptional("status");
  public static final GitCommand SUBMODULE = write("submodule"); // NB: it is write command in the submodule, not in the current root which is the submodule's parent
  public static final GitCommand TAG = read("tag");
  public static final GitCommand UPDATE_INDEX = write("update-index");
  public static final GitCommand UPDATE_REF = write("update-ref");
  public static final GitCommand HASH_OBJECT = write("hash-object");
  public static final GitCommand VERSION = read("version");
  public static final GitCommand WORKTREE = read("worktree");

  /**
   * Name of environment variable that specifies editor for the git
   */
  public static final @NonNls String GIT_EDITOR_ENV = "GIT_EDITOR";
  /**
   * Name of environment variable that specifies askpass for the git (http and ssh passphrase authentication)
   */
  public static final @NonNls String GIT_ASK_PASS_ENV = "GIT_ASKPASS";
  public static final @NonNls String GIT_SSH_ASK_PASS_ENV = "SSH_ASKPASS";
  public static final @NonNls String SSH_ASKPASS_REQUIRE_ENV = "SSH_ASKPASS_REQUIRE";
  public static final @NonNls String DISPLAY_ENV = "DISPLAY";
  public static final @NonNls String GIT_SSH_ENV = "GIT_SSH";
  public static final @NonNls String GIT_SSH_COMMAND_ENV = "GIT_SSH_COMMAND";
  /**
   * Marker-ENV, that lets git hooks to detect us if needed.
   */
  public static final @NonNls String IJ_HANDLER_MARKER_ENV = "INTELLIJ_GIT_EXECUTABLE";

  @ApiStatus.Internal
  public enum LockingPolicy {
    READ,
    READ_OPTIONAL_LOCKING,
    WRITE
  }

  private final @NotNull @NonNls String myName; // command name passed to git
  private final @NotNull LockingPolicy myLocking; // Locking policy for the command

  private GitCommand(@NotNull @NonNls String name, @NotNull LockingPolicy lockingPolicy) {
    myLocking = lockingPolicy;
    myName = name;
  }

  /**
   * Copy constructor with other locking policy.
   */
  private GitCommand(@NotNull GitCommand command, @NotNull LockingPolicy lockingPolicy) {
    myName = command.name();
    myLocking = lockingPolicy;
  }

  /**
   * <p>Creates the clone of this git command, but with LockingPolicy different from the default one.</p>
   * <p>This can be used for commands, which are considered to be "write" commands in general, but can be "read" commands when a certain
   *    set of arguments is given ({@code git stash list}, for instance).</p>
   * <p>Use this constructor with care: specifying read-policy on a write operation may result in a conflict during simultaneous
   *    modification of index.</p>
   */
  public @NotNull GitCommand readLockingCommand() {
    return new GitCommand(this, LockingPolicy.READ);
  }

  public @NotNull GitCommand writeLockingCommand() {
    return new GitCommand(this, LockingPolicy.WRITE);
  }

  private static @NotNull GitCommand read(@NotNull @NonNls String name) {
    return new GitCommand(name, LockingPolicy.READ);
  }

  private static @NotNull GitCommand readOptional(@NotNull @NonNls String name) {
    return new GitCommand(name, LockingPolicy.READ_OPTIONAL_LOCKING);
  }

  private static @NotNull GitCommand write(@NotNull @NonNls String name) {
    return new GitCommand(name, LockingPolicy.WRITE);
  }

  public @NotNull String name() {
    return myName;
  }

  public @NotNull LockingPolicy lockingPolicy() {
    return myLocking;
  }

  @Override
  public String toString() {
    return myName;
  }
}
