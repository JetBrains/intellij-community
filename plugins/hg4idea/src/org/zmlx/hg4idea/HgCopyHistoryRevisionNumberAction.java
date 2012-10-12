/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.PlatformIcons;

import java.awt.datatransfer.StringSelection;

/**
 * @author Nadya Zabrodina
 */
public class HgCopyHistoryRevisionNumberAction extends AnAction implements DumbAware {

  public HgCopyHistoryRevisionNumberAction() {
    super(HgVcsMessages.message("hg4idea.history.copy.revision.number"),
          HgVcsMessages.message("hg4idea.history.copy.revision.number"),
          PlatformIcons.COPY_ICON);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision != null) {
      CopyPasteManager.getInstance().setContents(new StringSelection(revision.getRevisionNumber().asString()));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null));
  }
}


