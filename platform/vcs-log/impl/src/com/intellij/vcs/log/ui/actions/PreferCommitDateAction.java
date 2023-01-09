// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.column.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.isVisible;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.supportsColumnsToggling;

public class PreferCommitDateAction extends BooleanPropertyToggleAction implements DumbAware {
  public PreferCommitDateAction() {
    super(VcsBundle.messagePointer("prefer.commit.timestamp.action.text"),
          VcsBundle.messagePointer("prefer.commit.timestamp.action.description"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.PREFER_COMMIT_DATE;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    VcsLogData data = e.getData(VcsLogInternalDataKeys.LOG_DATA);
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (data == null || logUi == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Map<VirtualFile, VcsLogProvider> providers = data.getLogProviders();
    if (!isCommitDateSupported(providers.values())) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Set<VirtualFile> visibleRoots = e.getData(VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS);
    if (visibleRoots == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Set<VcsLogProvider> visibleProviders = ContainerUtil.map2SetNotNull(visibleRoots, root -> providers.get(root));
    if (!isDateDisplayed(e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES)) ||
        !isCommitDateSupported(visibleProviders)) {
      e.getPresentation().setEnabled(false);
    }
  }

  private static boolean isCommitDateSupported(@NotNull Collection<? extends VcsLogProvider> providers) {
    return providers.stream().anyMatch(VcsLogProperties.HAS_COMMITTER::getOrDefault);
  }

  private static boolean isDateDisplayed(@Nullable VcsLogUiProperties properties) {
    if (properties != null && supportsColumnsToggling(properties)) {
      return isVisible(Date.INSTANCE, properties);
    }
    return false;
  }
}
