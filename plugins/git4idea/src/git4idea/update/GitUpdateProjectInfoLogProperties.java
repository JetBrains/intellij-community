// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "Git.Update.Project.Info.Tabs.Properties", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class GitUpdateProjectInfoLogProperties extends VcsLogUiPropertiesWithSharedRecentFilters<VcsLogUiPropertiesImpl.State>
                                                             implements PersistentStateComponent<VcsLogUiPropertiesImpl.State> {
  public GitUpdateProjectInfoLogProperties(@NotNull Project project) {
    super(project, ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class));
  }

  public VcsLogUiPropertiesImpl.State commonState = new VcsLogUiPropertiesImpl.State();

  @Override
  protected @NotNull VcsLogUiPropertiesImpl.State getLogUiState() {
    return commonState;
  }

  @Override
  public @Nullable VcsLogUiPropertiesImpl.State getState() {
    return getLogUiState();
  }

  @Override
  public void loadState(@NotNull VcsLogUiPropertiesImpl.State state) {
    commonState = state;
  }
}