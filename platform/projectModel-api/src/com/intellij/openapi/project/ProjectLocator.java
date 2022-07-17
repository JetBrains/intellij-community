// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.NonExtendable
public abstract class ProjectLocator {
  // called very often by StubUpdatingIndex
  private static ProjectLocator ourInstance = CachedSingletonsRegistry.markCachedField(ProjectLocator.class);

  private static final ThreadLocal<Map<VirtualFile, Project>> ourPreferredProjects = ThreadLocal.withInitial(() -> new HashMap<>());

  public static ProjectLocator getInstance() {
    ProjectLocator result = ourInstance;
    if (result == null) {
      result = ApplicationManager.getApplication().getService(ProjectLocator.class);
      ourInstance = result;
    }
    return result;
  }

  /**
   * Returns an open project which contains the given file.
   * This is a guess-method, so if several projects contain the file, only one will be returned.
   * @param file file to be located in projects.
   * @return project which probably contains the file, or null if couldn't guess (for example, there are no open projects).
   */
  @Nullable
  public abstract Project guessProjectForFile(@NotNull VirtualFile file);

  /**
  * Gets all open projects containing the given file.
  * If none does, an empty list is returned.
  * @param file file to be located in projects.
  * @return list of open projects containing this file.
  */
  @NotNull
  public abstract Collection<Project> getProjectsForFile(@NotNull VirtualFile file);

  /**
   * Execute {@code runnable}, making sure that within this computation every call to
   * {@link #guessProjectForFile(VirtualFile)} for the {@code file} will return {@code preferredProject}
   */
  public static <T, E extends Throwable> T computeWithPreferredProject(@NotNull VirtualFile file,
                                                                       @NotNull Project preferredProject,
                                                                       @NotNull ThrowableComputable<T, E> runnable) throws E {
    Map<VirtualFile, Project> local = ourPreferredProjects.get();
    Project prev = local.put(file, preferredProject);
    try {
      return runnable.compute();
    }
    finally {
      if (prev == null) {
        local.remove(file);
      }
      else {
        local.put(file, prev);
      }
    }
  }

  @Nullable
  static Project getPreferredProject(@NotNull VirtualFile file) {
    return ourPreferredProjects.get().get(file);
  }
}
