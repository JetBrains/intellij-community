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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.ArrayUtil;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public class CreatePatchToClipboardAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreatePatchToClipboardAction.class);

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
      try {
        String base = PatchWriter.calculateBaseForWritingPatch(project, changes).getPath();
        List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, changes, base, false);
        ProgressManager.checkCanceled();
        StringWriter writer = new StringWriter();
        PatchWriter.write(project, writer, base, patches, new CommitContext(), true);
        CopyPasteManager.getInstance().setContents(new StringSelection(writer.toString()));
        VcsNotifier.getInstance(project).notifySuccess("Patch copied to clipboard");
      }
      catch (IOException | VcsException exception) {
        LOG.error("Can't create patch", exception);
        VcsNotifier.getInstance(project).notifyWeakError("Create patch failed");
      }
    }, VcsBundle.message("create.patch.commit.action.progress"), true, project);
  }
}