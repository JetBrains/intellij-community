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
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.ui.VcsLogUiImpl;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ExpandGraphAction extends CollapseOrExpandGraphAction {
  @NotNull private static final String EXPAND = "Expand";

  public ExpandGraphAction() {
    super(EXPAND);
  }

  @Override
  protected void executeAction(@NotNull VcsLogUiImpl vcsLogUi) {
    vcsLogUi.expandAll();
  }

  @NotNull
  @Override
  protected Icon getMergesIcon() {
    return VcsLogIcons.ExpandMerges;
  }

  @NotNull
  @Override
  protected Icon getBranchesIcon() {
    return VcsLogIcons.ExpandMerges;
  }

  @NotNull
  @Override
  protected String getPrefix() {
    return EXPAND + " ";
  }
}
