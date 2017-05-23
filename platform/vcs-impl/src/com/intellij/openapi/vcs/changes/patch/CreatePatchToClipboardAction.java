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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.writeAsPatchToClipboard;

public class CreatePatchToClipboardAction extends DumbAwareAction {

  public CreatePatchToClipboardAction() {
    super(VcsBundle.message("create.patch.to.clipboard.title"), VcsBundle.message("create.patch.to.clipboard.description"), null);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES)));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final List<Change> changes = Arrays.asList(e.getRequiredData(VcsDataKeys.CHANGES));
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      writeAsPatchToClipboard(project, changes, PatchWriter.calculateBaseForWritingPatch(project, changes).getPath(), false,
                              new CommitContext());
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }
}