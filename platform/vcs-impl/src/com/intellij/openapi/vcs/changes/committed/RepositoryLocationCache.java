// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.synchronizedMap;

public class RepositoryLocationCache {

  private final @NotNull Project myProject;
  private final @NotNull Map<Couple<String>, RepositoryLocation> myMap = synchronizedMap(new HashMap<>());

  public RepositoryLocationCache(@NotNull Project project) {
    myProject = project;
  }

  public @Nullable RepositoryLocation getLocation(@NotNull AbstractVcs vcs, @NotNull FilePath filePath, boolean silent) {
    Couple<String> key = Couple.of(vcs.getName(), filePath.getPath());
    RepositoryLocation location = myMap.get(key);

    if (location == null) {
      location = getLocationUnderProgress(vcs, filePath, silent);
      myMap.put(key, location);
    }

    return location;
  }

  private @Nullable RepositoryLocation getLocationUnderProgress(@NotNull AbstractVcs vcs, @NotNull FilePath filePath, boolean silent) {
    ThrowableComputable<RepositoryLocation, RuntimeException> result = () -> {
      CommittedChangesProvider committedChangesProvider = vcs.getCommittedChangesProvider();
      return committedChangesProvider != null ? committedChangesProvider.getLocationFor(filePath) : null;
    };

    return !silent && ApplicationManager.getApplication().isDispatchThread()
           ? ProgressManager.getInstance()
             .runProcessWithProgressSynchronously(result,
                                                  VcsBundle.message("progress.title.discovering.location", filePath.getPresentableUrl()), true, myProject)
           : result.compute();
  }

  public void reset() {
    myMap.clear();
  }
}
