// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * There are lazy initializing project model entities, that take time to turn to be fully
 * valid (e.g. downloading, unpacking, or installation).
 * <br />
 * An attempt to use not-yet-completed entity from the code would likely fail with
 * a error, and a suggestion for a user to fix a problem. We'd like to add an API for
 * such code to tell a true invalid entity from a preparing one.
 * <br/>
 * Use this API to tell between two cases:
 * <li>an entity is invalid</li>
 * <li>an entity is being prepared (that is the reason of it to be invalid)</li>
 * <br/>
 * The implementation is thread safe
 */
public abstract class SdkTracker {
  @NotNull
  public static SdkTracker getInstance(@NotNull Project project) {
    return project.getService(SdkTracker.class);
  }

  /**
   * Checks if a given SDK is fully ready.
   * @return @{code true} iff a given {@param sdk} if ready, {@code false} otherwise
   * @deprecated use the callback version of that function to avoid race conditions
   */
  @Deprecated
  public abstract boolean isSdkReady(@NotNull Sdk sdk);

  /**
   * Waits for a given {@param sdk} to become ready. That API does not let you
   * know if an Sdk preparation process completed successfully of failed. It
   * would only notify you once the process is ended (and it is safe to involve
   * a user to resolve a trouble).
   * <br/>
   * The {@param onReady} is executed directly, if the {@param sdk}
   * is ready right now. Otherwise the {@param onReady} callback is executed
   * later on from a random callback or EDT thread
   * <br />
   * @param sdk Sdk instance to track readiness
   * @param lifetime subscription lifetime
   * @param onReady callback to notify an SDK is ready. The callback is executed directly if
   *                a given Sdk instance is ready at the moment of the call
   */
  public abstract void whenReady(@NotNull Sdk sdk,
                                 @NotNull Disposable lifetime,
                                 @NotNull Consumer<Sdk> onReady);
}
