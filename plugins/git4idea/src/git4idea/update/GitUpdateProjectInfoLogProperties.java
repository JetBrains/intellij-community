// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.VcsLogFilterCollection.RANGE_FILTER;

@State(
  name = "Git.Update.Project.Info.Tabs.Properties",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class GitUpdateProjectInfoLogProperties extends VcsLogUiPropertiesImpl<GitUpdateProjectInfoLogProperties.MyState> {

  public MyState commonState = new MyState();

  public static class MyState extends VcsLogUiPropertiesImpl.State {
    @XCollection Map<String, List<VcsLogProjectTabsProperties.RecentGroup>> RECENT_FILTERS = new HashMap<>();
  }

  public GitUpdateProjectInfoLogProperties(@NotNull VcsLogApplicationSettings appSettings) {
    super(appSettings);
  }

  @NotNull
  @Override
  public MyState getState() {
    return commonState;
  }

  @NotNull
  @Override
  public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
    return VcsLogProjectTabsProperties.getRecentGroup(commonState.RECENT_FILTERS, filterName);
  }

  @Override
  public void loadState(@NotNull MyState state) {
    commonState = state;
  }

  @Override
  public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
    VcsLogProjectTabsProperties.addRecentGroup(commonState.RECENT_FILTERS, filterName, values);
  }

  @Override
  public void saveFilterValues(@NotNull String filterName, @Nullable List<String> values) {
    if (filterName != RANGE_FILTER.getName()) {
      super.saveFilterValues(filterName, values);
    }
  }
}