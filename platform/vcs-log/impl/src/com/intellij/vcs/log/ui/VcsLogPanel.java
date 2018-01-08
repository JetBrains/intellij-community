/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.intellij.vcs.log.VcsLogDataKeys.*;

public class VcsLogPanel extends JBPanel implements DataProvider {
  @NotNull private final VcsLogManager myManager;
  @NotNull private final AbstractVcsLogUi myUi;

  public VcsLogPanel(@NotNull VcsLogManager manager, @NotNull AbstractVcsLogUi logUi) {
    super(new BorderLayout());
    myManager = manager;
    myUi = logUi;
    add(myUi.getMainComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public AbstractVcsLogUi getUi() {
    return myUi;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (VcsLogInternalDataKeys.LOG_MANAGER.is(dataId)) {
      return myManager;
    }
    else if (VCS_LOG.is(dataId)) {
      return myUi.getVcsLog();
    }
    else if (VCS_LOG_UI.is(dataId)) {
      return myUi;
    }
    else if (VCS_LOG_DATA_PROVIDER.is(dataId)) {
      return myManager.getDataManager();
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBER.is(dataId)) {
      List<CommitId> hashes = myUi.getVcsLog().getSelectedCommits();
      if (hashes.isEmpty()) return null;
      return VcsLogUtil.convertToRevisionNumber(ObjectUtils.notNull(ContainerUtil.getFirstItem(hashes)).getHash());
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<CommitId> hashes = myUi.getVcsLog().getSelectedCommits();
      if (hashes.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ArrayUtil.toObjectArray(ContainerUtil.map(hashes, commitId -> VcsLogUtil.convertToRevisionNumber(commitId.getHash())),
                                     VcsRevisionNumber.class);
    }
    return null;
  }
}
