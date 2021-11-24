// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.navigation.History;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.vcs.log.VcsLogDataKeys.*;

public class VcsLogPanel extends JBPanel implements DataProvider {
  @NotNull private final VcsLogManager myManager;
  @NotNull private final VcsLogUiEx myUi;

  public VcsLogPanel(@NotNull VcsLogManager manager, @NotNull VcsLogUiEx logUi) {
    super(new BorderLayout());
    myManager = manager;
    myUi = logUi;
    add(myUi.getMainComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public VcsLogUiEx getUi() {
    return myUi;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (VcsLogInternalDataKeys.LOG_MANAGER.is(dataId)) {
      return myManager;
    }
    else if (VCS_LOG.is(dataId)) {
      return myUi.getVcsLog();
    }
    else if (VCS_LOG_UI.is(dataId)) {
      return myUi;
    }
    else if (VCS_LOG_DATA_PROVIDER.is(dataId) || VcsLogInternalDataKeys.LOG_DATA.is(dataId)) {
      return myManager.getDataManager();
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBER.is(dataId)) {
      List<CommitId> hashes = myUi.getVcsLog().getSelectedCommits();
      if (hashes.isEmpty()) return null;
      return VcsLogUtil.convertToRevisionNumber(Objects.requireNonNull(ContainerUtil.getFirstItem(hashes)).getHash());
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<CommitId> hashes = myUi.getVcsLog().getSelectedCommits();
      if (hashes.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.map(hashes,
                               commitId -> VcsLogUtil.convertToRevisionNumber(commitId.getHash())).toArray(new VcsRevisionNumber[0]);
    }
    else if (VcsDataKeys.VCS_COMMIT_SUBJECTS.is(dataId)) {
      List<VcsCommitMetadata> metadata = myUi.getVcsLog().getSelectedShortDetails();
      if (metadata.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.map2Array(metadata, String.class, data -> data.getSubject());
    }
    else if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return myUi.getHelpId();
    }
    else if (History.KEY.is(dataId)) {
      return myUi.getNavigationHistory();
    }
    return null;
  }

  public static @NotNull List<VcsLogUiEx> getLogUis(@NotNull JComponent c) {
    Set<VcsLogPanel> panels = new HashSet<>();
    collectLogPanelInstances(c, panels);

    return ContainerUtil.map(panels, VcsLogPanel::getUi);
  }

  private static void collectLogPanelInstances(@NotNull JComponent component, @NotNull Set<VcsLogPanel> result) {
    if (component instanceof VcsLogPanel) {
      result.add((VcsLogPanel)component);
      return;
    }
    for (Component childComponent : component.getComponents()) {
      if (childComponent instanceof JComponent) {
        collectLogPanelInstances((JComponent)childComponent, result);
      }
    }
  }
}