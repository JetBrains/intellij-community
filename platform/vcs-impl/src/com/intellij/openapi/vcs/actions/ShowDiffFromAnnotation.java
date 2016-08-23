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
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CacheOneStepIterator;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class ShowDiffFromAnnotation extends DumbAwareAction implements UpToDateLineNumberListener {
  private final FileAnnotation myFileAnnotation;
  private final AbstractVcs myVcs;
  private final VirtualFile myFile;
  private int currentLine;
  private boolean myEnabled;

  ShowDiffFromAnnotation(final FileAnnotation fileAnnotation, final AbstractVcs vcs, final VirtualFile file) {
    super(ActionsBundle.message("action.Diff.UpdatedFiles.text"),
          ActionsBundle.message("action.Diff.UpdatedFiles.description"),
          AllIcons.Actions.Diff);
    myFileAnnotation = fileAnnotation;
    myVcs = vcs;
    myFile = file;
    currentLine = -1;
    myEnabled = ProjectLevelVcsManager.getInstance(vcs.getProject()).getVcsFor(myFile) != null;
  }

  @Override
  public void consume(Integer integer) {
    currentLine = integer;
  }

  @Override
  public void update(AnActionEvent e) {
    final int number = currentLine;
    e.getPresentation().setVisible(myEnabled);
    e.getPresentation().setEnabled(myEnabled && number >= 0 && number < myFileAnnotation.getLineCount());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final int actualNumber = currentLine;
    if (actualNumber < 0) return;

    final VcsRevisionNumber revisionNumber = myFileAnnotation.getLineRevisionNumber(actualNumber);
    if (revisionNumber != null) {
      final VcsException[] exc = new VcsException[1];
      final List<Change> changes = new LinkedList<>();
      final FilePath[] targetPath = new FilePath[1];
      ProgressManager.getInstance().run(new Task.Backgroundable(myVcs.getProject(),
                                                                "Loading revision " + revisionNumber.asString() + " contents", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final CommittedChangesProvider provider = myVcs.getCommittedChangesProvider();
          try {
            final Pair<CommittedChangeList, FilePath> pair = provider.getOneList(myFile, revisionNumber);
            if (pair == null || pair.getFirst() == null) {
              VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), "Can not load data for show diff", MessageType.ERROR);
              return;
            }
            targetPath[0] = pair.getSecond() == null ? VcsUtil.getFilePath(myFile) : pair.getSecond();
            final CommittedChangeList cl = pair.getFirst();
            changes.addAll(cl.getChanges());
            Collections.sort(changes, ChangesComparator.getInstance(true));
          }
          catch (VcsException e1) {
            exc[0] = e1;
          }
        }

        @Override
        public void onSuccess() {
          if (exc[0] != null) {
            VcsBalloonProblemNotifier
              .showOverChangesView(myVcs.getProject(), "Can not show diff: " + exc[0].getMessage(), MessageType.ERROR);
          }
          else if (!changes.isEmpty()) {
            int idx = findSelfInList(changes, targetPath[0]);
            final ShowDiffContext context = new ShowDiffContext(DiffDialogHints.FRAME);
            if (idx != -1) {
              context.putChangeContext(changes.get(idx), DiffUserDataKeysEx.NAVIGATION_CONTEXT, createDiffNavigationContext(actualNumber));
            }
            if (ChangeListManager.getInstance(myVcs.getProject()).isFreezedWithNotification(null)) return;
            ShowDiffAction.showDiffForChange(myVcs.getProject(), changes, idx, context);
          }
        }
      });
    }
  }

  private static int findSelfInList(@NotNull List<Change> changes, @NotNull FilePath filePath) {
    int idx = -1;
    for (int i = 0; i < changes.size(); i++) {
      final Change change = changes.get(i);
      if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().equals(filePath))) {
        idx = i;
        break;
      }
    }
    if (idx >= 0) return idx;
    idx = 0;
    // try to use name only
    final String name = filePath.getName();
    for (int i = 0; i < changes.size(); i++) {
      final Change change = changes.get(i);
      if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().getName().equals(name))) {
        idx = i;
        break;
      }
    }

    return idx;
  }

  /*
   * Locate line in annotated content, using lines that are known to be modified in this revision
   */
  @Nullable
  private DiffNavigationContext createDiffNavigationContext(int actualLine) {
    String annotatedContent = myFileAnnotation.getAnnotatedContent();
    if (StringUtil.isEmptyOrSpaces(annotatedContent)) return null;

    String[] contentsLines = LineTokenizer.tokenize(annotatedContent, false, false);
    if (contentsLines.length <= actualLine) return null;

    final int correctedLine = correctActualLineIfTextEmpty(contentsLines, actualLine);
    return new DiffNavigationContext(new Iterable<String>() {
      @Override
      public Iterator<String> iterator() {
        return new CacheOneStepIterator<>(new ContextLineIterator(contentsLines, myFileAnnotation, correctedLine));
      }
    }, contentsLines[correctedLine]);
  }

  private final static int ourVicinity = 5;

  private int correctActualLineIfTextEmpty(@NotNull String[] contentsLines, final int actualLine) {
    final VcsRevisionNumber revision = myFileAnnotation.getLineRevisionNumber(actualLine);
    if (revision == null) return actualLine;
    if (!StringUtil.isEmptyOrSpaces(contentsLines[actualLine])) return actualLine;

    int upperBound = Math.min(actualLine + ourVicinity, contentsLines.length);
    for (int i = actualLine + 1; i < upperBound; i++) {
      if (revision.equals(myFileAnnotation.getLineRevisionNumber(i)) && !StringUtil.isEmptyOrSpaces(contentsLines[i])) {
        return i;
      }
    }

    int lowerBound = Math.max(actualLine - ourVicinity, 0);
    for (int i = actualLine - 1; i >= lowerBound; --i) {
      if (revision.equals(myFileAnnotation.getLineRevisionNumber(i)) && !StringUtil.isEmptyOrSpaces(contentsLines[i])) {
        return i;
      }
    }

    return actualLine;
  }

  /**
   * Slightly break the contract: can return null from next() while had claimed hasNext()
   */
  private static class ContextLineIterator implements Iterator<String> {
    @NotNull private final String[] myContentsLines;

    private final VcsRevisionNumber myRevisionNumber;
    @NotNull private final FileAnnotation myAnnotation;
    private final int myStopAtLine;
    // we assume file has at least one line ;)
    private int myCurrentLine;  // to start looking for next line with revision from

    private ContextLineIterator(@NotNull String[] contentLines, @NotNull FileAnnotation annotation, int stopAtLine) {
      myAnnotation = annotation;
      myRevisionNumber = myAnnotation.originalRevision(stopAtLine);
      myStopAtLine = stopAtLine;
      myContentsLines = contentLines;
    }

    @Override
    public boolean hasNext() {
      return myRevisionNumber != null && lineNumberInBounds();
    }

    private boolean lineNumberInBounds() {
      return (myCurrentLine < myContentsLines.length) && (myCurrentLine < myStopAtLine);
    }

    @Override
    public String next() {
      while (lineNumberInBounds()) {
        final VcsRevisionNumber vcsRevisionNumber = myAnnotation.originalRevision(myCurrentLine);
        final String text = myContentsLines[myCurrentLine];
        ++myCurrentLine;

        if (myRevisionNumber.equals(vcsRevisionNumber) && !StringUtil.isEmptyOrSpaces(text)) {
          return text;
        }
      }
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
