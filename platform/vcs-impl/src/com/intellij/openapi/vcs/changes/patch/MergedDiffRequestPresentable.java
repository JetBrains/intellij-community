/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.actions.DiffChainContext;
import com.intellij.openapi.vcs.changes.actions.DiffPresentationReturnValue;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;
import java.util.List;

public class MergedDiffRequestPresentable implements DiffRequestPresentable {
  private final Project myProject;
  private final VirtualFile myFile;
  private final String myAfterTitle;
  private final ApplyPatchForBaseRevisionTexts myTexts;

  public MergedDiffRequestPresentable(final Project project, final ApplyPatchForBaseRevisionTexts texts, final VirtualFile file, final String afterTitle) {
    myTexts = texts;
    myProject = project;
    myFile = file;
    myAfterTitle = afterTitle;
  }

  public MyResult step(DiffChainContext context) {
    final MergeRequest request = DiffRequestFactory.getInstance()
      .create3WayDiffRequest(myTexts.getLocal().toString(), myTexts.getPatched(), myTexts.getBase().toString(), myProject, null);
    request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", myFile.getPresentableUrl()));
    request.setVersionTitles(new String[] {"Current Version", "Base Version", myAfterTitle});
    return new MyResult(request, DiffPresentationReturnValue.useRequest);
  }

  public boolean haveStuff() {
    return true;
  }

  public List<? extends AnAction> createActions(ShowDiffAction.DiffExtendUIFactory uiFactory) {
    return Collections.emptyList();
  }
}
