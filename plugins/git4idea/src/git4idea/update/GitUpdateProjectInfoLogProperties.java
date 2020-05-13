// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import org.jetbrains.annotations.NotNull;

@State(name = "Git.Update.Project.Info.Tabs.Properties", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
})
public final class GitUpdateProjectInfoLogProperties extends VcsLogUiPropertiesWithSharedRecentFilters {
  public GitUpdateProjectInfoLogProperties(@NotNull Project project) {
    super(project, ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class));
  }
}