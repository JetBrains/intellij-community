// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Location;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * A parser for location URLs reported by test runners.
 * See {@link SMTestProxy#getLocation(Project, GlobalSearchScope)} for details.
 */
public interface SMTestLocator extends PossiblyDumbAware {
  /**
   * Creates the <code>Location</code> list from <code>protocol</code> and <code>path</code> in <code>scope</code>.
   */
   @NotNull
   @Unmodifiable
  List<Location> getLocation(@NonNls @NotNull String protocol,
                             @NonNls @NotNull String path,
                             @NonNls @NotNull Project project,
                             @NotNull GlobalSearchScope scope);

  /**
   * Creates the <code>Location</code> list from <code>protocol</code>, <code>path</code>, and <code>metainfo</code> in <code>scope</code>.
   * Implementation of test framework can provide additional information in <code>metainfo</code> parameter,
   * The <code>metainfo</code> parameter simplifies the search for locations, but cannot be used to identify the test.
   * A good example for <code>metainfo</code> is the line number of the beginning of the test. It can speed up the search procedure,
   * but it changes when editing.
   */
  @NotNull
  @Unmodifiable
  default List<Location> getLocation(@NonNls @NotNull String protocol,
                                     @NonNls @NotNull String path,
                                     @NonNls @Nullable String metainfo,
                                     @NotNull Project project,
                                     @NotNull GlobalSearchScope scope) {
    return getLocation(protocol, path, project, scope);
  }

  /**
   * Parse stacktrace line and return corresponding location.
   */
  @NotNull
  @Unmodifiable
  default List<Location> getLocation(@NotNull String stacktraceLine,
                                     @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return Collections.emptyList();
  }

  /**
   * @param project Project instance
   * @return ModificationTracker instance used to cache result of {{@link #getLocation(String, String, Project, GlobalSearchScope)}};
   *         To disable caching, override and return {@link ModificationTracker#EVER_CHANGED}.
   */
  @NotNull
  default ModificationTracker getLocationCacheModificationTracker(@NotNull Project project) {
    return PsiModificationTracker.getInstance(project); // invalidates cache on entering/exiting dumb mode
  }
}