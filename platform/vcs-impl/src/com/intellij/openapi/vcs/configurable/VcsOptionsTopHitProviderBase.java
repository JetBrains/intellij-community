// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.ProjectTopHitCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VcsOptionsTopHitProviderBase implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  protected boolean isEnabled(@NotNull Project project, @Nullable AbstractVcs vcs) {
    if (project.isDefault()) return true;
    return vcs != null && ArrayUtil.contains(vcs, ProjectLevelVcsManager.getInstance(project).getAllActiveVcss());
  }

  public static class InitMappingsListenerActivity implements ProjectManagerListener {
    @Override
    public void projectOpened(@NotNull Project project) {
      project.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> invalidateTopHitCaches(project));
    }

    private static void invalidateTopHitCaches(@NotNull Project project) {
      for (ProjectLevelProvider provider : OptionsTopHitProvider.PROJECT_LEVEL_EP.getExtensionList()) {
        if (provider instanceof VcsOptionsTopHitProviderBase) {
          ProjectTopHitCache.getInstance(project).invalidateCachedOptions(provider.getClass());
        }
      }
    }
  }
}
