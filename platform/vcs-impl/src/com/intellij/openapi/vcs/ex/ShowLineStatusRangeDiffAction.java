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
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public class ShowLineStatusRangeDiffAction extends BaseLineStatusRangeAction {
  public ShowLineStatusRangeDiffAction(@NotNull LineStatusTracker lineStatusTracker, @NotNull Range range, @Nullable Editor editor) {
    super(VcsBundle.message("action.name.show.difference"), AllIcons.Actions.Diff, lineStatusTracker, range);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    DiffManager.getInstance().getDiffTool().show(createDiffData());
  }

  private DiffRequest createDiffData() {
    return new DiffRequest(myLineStatusTracker.getProject()) {
      @NotNull
      public DiffContent[] getContents() {
        Range range = expand(myRange, myLineStatusTracker.getDocument(), myLineStatusTracker.getUpToDateDocument());
        return new DiffContent[]{
          createDiffContent(myLineStatusTracker.getUpToDateDocument(),
                            myLineStatusTracker.getUpToDateRange(range),
                            null),
          createDiffContent(myLineStatusTracker.getDocument(),
                            myLineStatusTracker.getCurrentTextRange(range),
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

  @NotNull
  private DiffContent createDiffContent(@NotNull Document uDocument, @NotNull TextRange textRange, @Nullable VirtualFile file) {
    final Project project = myLineStatusTracker.getProject();
    final DiffContent diffContent = new DocumentContent(project, uDocument);
    return new FragmentContent(diffContent, textRange, project, file);
  }

  @NotNull
  private static Range expand(@NotNull Range range, @NotNull Document document, @NotNull Document uDocument) {
    if (range.getType() == Range.MODIFIED) return range;
    if (range.getType() == Range.INSERTED || range.getType() == Range.DELETED) {
      boolean canExpandBefore = range.getOffset1() != 0 && range.getUOffset1() != 0;
      boolean canExpandAfter = range.getOffset2() < document.getLineCount() && range.getUOffset2() < uDocument.getLineCount();
      int offset1 = range.getOffset1() - (canExpandBefore ? 1 : 0);
      int uOffset1 = range.getUOffset1() - (canExpandBefore ? 1 : 0);
      int offset2 = range.getOffset2() + (canExpandAfter ? 1 : 0);
      int uOffset2 = range.getUOffset2() + (canExpandAfter ? 1 : 0);
      return new Range(offset1, offset2, uOffset1, uOffset2, range.getType());
    }
    throw new IllegalArgumentException("Unknown range type: " + range.getType());
  }
}
