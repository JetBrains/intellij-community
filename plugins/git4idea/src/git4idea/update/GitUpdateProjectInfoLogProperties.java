// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@State(
  name = "Git.Update.Project.Info.Tabs.Properties",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class GitUpdateProjectInfoLogProperties extends VcsLogUiPropertiesImpl<VcsLogUiPropertiesImpl.State> {

  @NotNull private final Project myProject;
  public VcsLogUiPropertiesImpl.State commonState = new VcsLogUiPropertiesImpl.State();

  public GitUpdateProjectInfoLogProperties(@NotNull Project project, @NotNull VcsLogApplicationSettings appSettings) {
    super(appSettings);
    myProject = project;
  }

  @NotNull
  @Override
  public VcsLogUiPropertiesImpl.State getState() {
    return commonState;
  }

  @NotNull
  @Override
  public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
    VcsLogProjectTabsProperties.State state = getCommonState();
    return state != null ? VcsLogProjectTabsProperties.getRecentGroup(state.RECENT_FILTERS, filterName) : Collections.emptyList();
  }

  @Override
  public void loadState(@NotNull VcsLogUiPropertiesImpl.State state) {
    commonState = state;
  }

  @Override
  public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
    VcsLogProjectTabsProperties.State state = getCommonState();
    if (state != null) {
      VcsLogProjectTabsProperties.addRecentGroup(state.RECENT_FILTERS, filterName, values);
    }
  }

  @Nullable
  private VcsLogProjectTabsProperties.State getCommonState() {
    return ServiceManager.getService(myProject, VcsLogProjectTabsProperties.class).getState();
  }
}