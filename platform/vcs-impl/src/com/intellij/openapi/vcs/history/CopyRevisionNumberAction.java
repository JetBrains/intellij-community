/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.util.PlatformIcons;

import java.awt.datatransfer.StringSelection;

/**
 * The action that copies a revision number text to clipboard
 */
public class CopyRevisionNumberAction extends DumbAwareAction {

  public CopyRevisionNumberAction() {
    super(VcsBundle.getString("history.copy.revision.number"), VcsBundle.getString("history.copy.revision.number"),
          PlatformIcons.COPY_ICON);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsRevisionNumber revision = e.getData(VcsDataKeys.VCS_REVISION_NUMBER);
    if (revision == null) {
      VcsFileRevision fileRevision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
      if (fileRevision != null) {
        revision = fileRevision.getRevisionNumber();
      }
    }
    if (revision == null) {
      return;
    }

    String rev = revision instanceof ShortVcsRevisionNumber ? ((ShortVcsRevisionNumber)revision).toShortString() : revision.asString();
    CopyPasteManager.getInstance().setContents(new StringSelection(rev));
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null
                                    || e.getData(VcsDataKeys.VCS_REVISION_NUMBER) != null));
  }
}
