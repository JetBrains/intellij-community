// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

//@Immutable
public final class VFSInitializationResult {
  public final @NotNull PersistentFSConnection connection;

  /** Were data storages created empty, from scratch? false means storages were loaded from already existing files */
  public final boolean vfsCreatedAnew;

  /**
   * If N initialization attempts were done, contains a list of [N-1] exceptions lead to the failure of the
   * particular attempt, 1 exception per attempt.
   */
  public final @NotNull List<Throwable> attemptsFailures;

  /** Total initialization time, including all the attempts done */
  public final long totalInitializationDurationNs;

  public VFSInitializationResult(@NotNull PersistentFSConnection connection,
                                 boolean createdAnew,
                                 @NotNull List<Throwable> attemptsFailures,
                                 long totalInitializationDurationNs) {
    this.connection = connection;
    this.vfsCreatedAnew = createdAnew;
    this.attemptsFailures = Collections.unmodifiableList(attemptsFailures);
    this.totalInitializationDurationNs = totalInitializationDurationNs;
  }

  @Override
  public String toString() {
    return "InitializationResult{" +
           "durationNs: " + totalInitializationDurationNs +
           ", attemptsFailures: " + attemptsFailures +
           ", createdAnew: " + vfsCreatedAnew +
           '}';
  }
}
