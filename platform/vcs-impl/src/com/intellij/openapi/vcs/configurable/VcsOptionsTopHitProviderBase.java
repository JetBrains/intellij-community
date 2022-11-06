// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.ProjectTopHitCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsMappingListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VcsOptionsTopHitProviderBase implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  protected boolean isEnabled(@NotNull Project project, @Nullable VcsKey vcsKey) {
    if (project.isDefault()) return true;
    if (vcsKey == null) return false;
    List<VcsDirectoryMapping> mappings = ProjectLevelVcsManager.getInstance(project).getDirectoryMappings();
    return ContainerUtil.exists(mappings, it -> vcsKey.getName().equals(it.getVcs()));
  }

  static final class InitMappingsListenerActivity implements VcsMappingListener {
    private final Project myProject;

    InitMappingsListenerActivity(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void directoryMappingChanged() {
      for (ProjectLevelProvider provider : OptionsTopHitProvider.PROJECT_LEVEL_EP.getExtensionList()) {
        if (provider instanceof VcsOptionsTopHitProviderBase) {
          ProjectTopHitCache.getInstance(myProject).invalidateCachedOptions(provider.getClass());
        }
      }
    }
  }
}
