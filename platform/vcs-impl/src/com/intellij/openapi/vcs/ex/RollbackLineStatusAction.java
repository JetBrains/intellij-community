/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RollbackLineStatusAction extends DumbAwareAction {
  public RollbackLineStatusAction() {
    super("Rollback", "Rollback selected changes", AllIcons.Actions.Reset);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    if (tracker == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    LineStatusTracker tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.getDocument());
    if (tracker == null) return;

    rollback(tracker, editor, null);
  }

  protected static void rollback(@NotNull LineStatusTracker tracker, @Nullable Editor editor, @Nullable Range range) {
    if (range != null) {
      doRollback(tracker, range);
      return;
    }

    if (editor == null) return;
    Document document = editor.getDocument();
    int totalLines = getLineCount(document);

    SegmentTree lines = new SegmentTree(totalLines + 1);

    List<Caret> carets = editor.getCaretModel().getAllCarets();
    for (Caret caret : carets) {
      if (caret.hasSelection()) {
        int line1 = editor.offsetToLogicalPosition(caret.getSelectionStart()).line;
        int line2 = editor.offsetToLogicalPosition(caret.getSelectionEnd()).line;
        lines.mark(line1, line2 + 1);
        if (caret.getSelectionEnd() == document.getTextLength()) lines.mark(totalLines);
      }
      else {
        lines.mark(caret.getLogicalPosition().line);
        if (caret.getOffset() == document.getTextLength()) lines.mark(totalLines);
      }
    }

    doRollback(tracker, lines);
  }

  private static void doRollback(@NotNull final LineStatusTracker tracker, @NotNull final Range range) {
    execute(tracker, new Runnable() {
      @Override
      public void run() {
        tracker.rollbackChanges(range);
      }
    });
  }

  private static void doRollback(@NotNull final LineStatusTracker tracker, @NotNull final SegmentTree lines) {
    execute(tracker, new Runnable() {
      @Override
      public void run() {
        tracker.rollbackChanges(lines);
      }
    });
  }

  private static void execute(@NotNull final LineStatusTracker tracker, @NotNull final Runnable task) {
    // TODO: is there possible data races?
    CommandProcessor.getInstance().executeCommand(tracker.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (!tracker.getDocument().isWritable()) {
              final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler
                .getInstance(tracker.getProject()).ensureFilesWritable(tracker.getVirtualFile());
              if (operationStatus.hasReadonlyFiles()) return;
            }
            task.run();
          }
        });
      }
    }, VcsBundle.message("command.name.rollback.change"), null);
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }
}
