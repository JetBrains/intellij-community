/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands;

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
public class GitCommand {

  public static final GitCommand ADD = write("add");
  public static final GitCommand BLAME = read("blame");
  public static final GitCommand BRANCH = read("branch");
  public static final GitCommand CHECKOUT = write("checkout");
  public static final GitCommand CHECK_ATTR = read("check-attr");
  public static final GitCommand COMMIT = write("commit");
  public static final GitCommand CONFIG = read("config");
  public static final GitCommand CHERRY = read("cherry");
  public static final GitCommand CHERRY_PICK = write("cherry-pick");
  public static final GitCommand CLONE = read("clone"); // write, but can't interfere with any other command => should be treated as read
  public static final GitCommand DIFF = read("diff");
  public static final GitCommand FETCH = read("fetch");  // fetch is a read-command, because it doesn't modify the index
  public static final GitCommand INIT = write("init");
  public static final GitCommand LOG = read("log");
  public static final GitCommand LS_FILES = read("ls-files");
  public static final GitCommand LS_TREE = read("ls-tree");
  public static final GitCommand LS_REMOTE = read("ls-remote");
  public static final GitCommand MERGE = write("merge");
  public static final GitCommand MERGE_BASE = read("merge-base");
  public static final GitCommand MV = write("mv");
  public static final GitCommand PULL = write("pull");
  public static final GitCommand PUSH = write("push");
  public static final GitCommand REBASE = write("rebase");
  public static final GitCommand REMOTE = read("remote");
  public static final GitCommand RESET = write("reset");
  public static final GitCommand REVERT = write("revert");
  public static final GitCommand REV_LIST = read("rev-list");
  public static final GitCommand REV_PARSE = read("rev-parse");
  public static final GitCommand RM = write("rm");
  public static final GitCommand SHOW = read("show");
  public static final GitCommand STASH = write("stash");
  public static final GitCommand STATUS = write("status");
  public static final GitCommand TAG = read("tag");
  public static final GitCommand UPDATE_INDEX = write("update-index");
  public static final GitCommand HASH_OBJECT = write("hash-object");

  /**
   * Name of environment variable that specifies editor for the git
   */
  public static final String GIT_EDITOR_ENV = "GIT_EDITOR";

  enum LockingPolicy {
    READ,
    WRITE
  }

  @NotNull @NonNls private final String myName; // command name passed to git
  @NotNull private final LockingPolicy myLocking; // Locking policy for the command

  private GitCommand(@NotNull String name, @NotNull LockingPolicy lockingPolicy) {
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
  @NotNull
  public GitCommand readLockingCommand() {
    return new GitCommand(this, LockingPolicy.READ);
  }

  @NotNull
  public GitCommand writeLockingCommand() {
    return new GitCommand(this, LockingPolicy.WRITE);
  }

  @NotNull
  private static GitCommand read(@NotNull String name) {
    return new GitCommand(name, LockingPolicy.READ);
  }

  @NotNull
  private static GitCommand write(@NotNull String name) {
    return new GitCommand(name, LockingPolicy.WRITE);
  }

  @NotNull
  public String name() {
    return myName;
  }

  @NotNull
  public LockingPolicy lockingPolicy() {
    return myLocking;
  }

  @Override
  public String toString() {
    return myName;
  }
}
