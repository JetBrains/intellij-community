// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import git4idea.update.VcsLogUiPropertiesWithSharedRecentFilters;
import org.jetbrains.annotations.NotNull;

@State(
  name = "Git.Compare.Branches.Log.Properties",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class GitCompareBranchesLogProperties extends VcsLogUiPropertiesWithSharedRecentFilters {
  public GitCompareBranchesLogProperties(@NotNull Project project, @NotNull VcsLogApplicationSettings appSettings) {
    super(project, appSettings);
  }
}
