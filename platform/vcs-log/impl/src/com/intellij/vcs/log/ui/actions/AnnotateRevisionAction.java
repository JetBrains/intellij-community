/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.AnnotateRevisionActionBase;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware {
  public AnnotateRevisionAction() {
    super(VcsBundle.message("annotate.action.name"), VcsBundle.message("annotate.action.description"), AllIcons.Actions.Annotate);
    setShortcutSet(ActionManager.getInstance().getAction("Annotate").getShortcutSet());
  }

  @Nullable
  @Override
  protected AbstractVcs getVcs(@NotNull AnActionEvent e) {
    VcsKey key = e.getData(VcsDataKeys.VCS);
    if (key == null) return null;
    return ProjectLevelVcsManager.getInstance(e.getProject()).findVcsByName(key.getName());
  }

  @Nullable
  @Override
  protected VirtualFile getFile(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
  }

  @Nullable
  @Override
  protected VcsFileRevision getFileRevision(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.VCS_FILE_REVISION);
  }
}
