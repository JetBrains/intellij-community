// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class VcsLogUiPropertiesWithSharedRecentFilters extends VcsLogUiPropertiesImpl<VcsLogUiPropertiesImpl.State> {

  @NotNull private final Project myProject;
  public VcsLogUiPropertiesImpl.State commonState = new VcsLogUiPropertiesImpl.State();

  public VcsLogUiPropertiesWithSharedRecentFilters(@NotNull Project project, @NotNull VcsLogApplicationSettings appSettings) {
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
    return VcsLogProjectTabsProperties.getRecentGroup(getCommonState().RECENT_FILTERS, filterName);
  }

  @Override
  public void loadState(@NotNull VcsLogUiPropertiesImpl.State state) {
    commonState = state;
  }

  @Override
  public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
    VcsLogProjectTabsProperties.addRecentGroup(getCommonState().RECENT_FILTERS, filterName, values);
  }

  @NotNull
  private VcsLogProjectTabsProperties.State getCommonState() {
    return ServiceManager.getService(myProject, VcsLogProjectTabsProperties.class).getState();
  }
}