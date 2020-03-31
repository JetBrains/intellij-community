// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.navigation.History;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Objects;

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
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myUi.getHelpId();
    }
    else if (History.KEY.is(dataId)) {
      return myUi.getNavigationHistory();
    }
    return null;
  }
}
