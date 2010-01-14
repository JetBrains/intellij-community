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
package git4idea.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import git4idea.i18n.GitBundle;

import java.awt.datatransfer.StringSelection;

/**
 * The action that copies a revision number text to clipboard
 */
public class GitCopyHistoryRevisionNumberAction extends AnAction implements DumbAware {

  /**
   * The constructor
   */
  public GitCopyHistoryRevisionNumberAction() {
    super(GitBundle.getString("history.copy.revsion.number"),
          GitBundle.getString("history.copy.revsion.number"),
          IconLoader.getIcon("/actions/copy.png"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (revision != null) {
      CopyPasteManager.getInstance().setContents(new StringSelection(revision.getRevisionNumber().asString()));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null));
  }
}
