/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.ui.actions.history.CompareRevisionsFromHistoryAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsLogActionPromoter implements ActionPromoter {

  @Override
  public List<AnAction> promote(@NotNull List<AnAction> actions, @NotNull DataContext context) {
    List<AnAction> promoted = ContainerUtil.newArrayList();

    VcsLogUi ui = VcsLogDataKeys.VCS_LOG_UI.getData(context);
    if (ui != null && ui instanceof FileHistoryUi) {
      CompareRevisionsFromHistoryAction compareAction = ContainerUtil.findInstance(actions, CompareRevisionsFromHistoryAction.class);
      if (compareAction != null) promoted.add(compareAction);
    }

    return promoted;
  }
}
