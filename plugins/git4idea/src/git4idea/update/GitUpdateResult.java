// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public enum GitUpdateResult {
  /** Nothing to update. */
  NOTHING_TO_UPDATE(1),
  /** Successful update, without merge conflict resolution during update. */
  SUCCESS(2),
  /** Update introduced a merge conflict, that was immediately resolved by user. */
  SUCCESS_WITH_RESOLVED_CONFLICTS(3),
  /** Update introduced a merge conflict that wasn't immediately resolved. */
  INCOMPLETE(4),
  /** User cancelled update, everything that has changed was rolled back (git rebase/merge --abort) */
  CANCEL(5),
  /** An error happened during update */
  ERROR(6),
  /** Update is not possible due to a configuration error or because of a failed fetch. */
  NOT_READY(7);

  private final int myPriority;

  GitUpdateResult(int priority) {
    myPriority = priority;
  }

  public boolean isSuccess() {
    return this == SUCCESS || this == SUCCESS_WITH_RESOLVED_CONFLICTS || this == INCOMPLETE || this == NOTHING_TO_UPDATE;
  }

  public @NotNull GitUpdateResult join(@NotNull GitUpdateResult next) {
    if (myPriority >= next.myPriority) {
      return this;
    }
    return next;
  }

}
