// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class VcsLogUiPropertiesWithSharedRecentFilters<S extends VcsLogUiPropertiesImpl.State> extends VcsLogUiPropertiesImpl<S> {
  private final @NotNull Project myProject;
  public VcsLogUiPropertiesWithSharedRecentFilters(@NotNull Project project, @NotNull VcsLogApplicationSettings appSettings) {
    super(appSettings);
    myProject = project;
  }

  @Override
  public @NotNull List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
    return getCommonProperties().getRecentlyFilteredGroups(filterName);
  }

  @Override
  public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
    getCommonProperties().addRecentlyFilteredGroup(filterName, values);
  }

  private @NotNull VcsLogProjectTabsProperties getCommonProperties() {
    return myProject.getService(VcsLogProjectTabsProperties.class);
  }
}