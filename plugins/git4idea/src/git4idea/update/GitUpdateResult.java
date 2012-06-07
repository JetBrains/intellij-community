/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

  @NotNull
  public GitUpdateResult join(@NotNull GitUpdateResult next) {
    if (myPriority >= next.myPriority) {
      return this;
    }
    return next;
  }

}
