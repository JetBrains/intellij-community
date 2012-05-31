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
package com.intellij.openapi.vcs.ex;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
* @author irengrig
*/
public class ShowLineStatusRangeDiffAction extends BaseLineStatusRangeAction {
  public ShowLineStatusRangeDiffAction(final LineStatusTracker lineStatusTracker, final Range range, final Editor editor) {
    super(VcsBundle.message("action.name.show.difference"), AllIcons.Actions.Diff, lineStatusTracker, range);
  }

  public boolean isEnabled() {
    return isModifiedRange() || isDeletedRange();
  }

  private boolean isDeletedRange() {
    return Range.DELETED == myRange.getType();
  }

  private boolean isModifiedRange() {
    return Range.MODIFIED == myRange.getType();
  }

  public void actionPerformed(final AnActionEvent e) {
    DiffManager.getInstance().getDiffTool().show(createDiffData());
  }

  private DiffRequest createDiffData() {
    return new DiffRequest(myLineStatusTracker.getProject()) {
      @NotNull
      public DiffContent[] getContents() {
        return new DiffContent[]{createDiffContent(myLineStatusTracker.getUpToDateDocument(),
                                                   myLineStatusTracker.getUpToDateRangeWithEndSymbol(myRange), null),
          createDiffContent(myLineStatusTracker.getDocument(), myLineStatusTracker.getCurrentTextRange(myRange),
                            myLineStatusTracker.getVirtualFile())};
      }

      public String[] getContentTitles() {
        return new String[]{VcsBundle.message("diff.content.title.up.to.date"),
          VcsBundle.message("diff.content.title.current.range")};
      }

      public String getWindowTitle() {
        return VcsBundle.message("dialog.title.diff.for.range");
      }
    };
  }

  private DiffContent createDiffContent(final Document uDocument, final TextRange textRange, final VirtualFile file) {
    final Project project = myLineStatusTracker.getProject();
    final DiffContent diffContent = new DocumentContent(project, uDocument);
    return new FragmentContent(diffContent, textRange, project, file);
  }
}
