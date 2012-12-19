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

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineNumberListener;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffUIContext;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CacheOneStepIterator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class ShowDiffFromAnnotation extends AnAction implements LineNumberListener {
  private final UpToDateLineNumberProvider myLineNumberProvider;
  private final FileAnnotation myFileAnnotation;
  private final AbstractVcs myVcs;
  private final VirtualFile myFile;
  private int currentLine;
  private boolean myEnabled;

  ShowDiffFromAnnotation(final UpToDateLineNumberProvider lineNumberProvider,
                         final FileAnnotation fileAnnotation, final AbstractVcs vcs, final VirtualFile file) {
    super(ActionsBundle.message("action.Diff.UpdatedFiles.text"),
          ActionsBundle.message("action.Diff.UpdatedFiles.description"),
          AllIcons.Actions.Diff);
    myLineNumberProvider = lineNumberProvider;
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
    final int number = getActualLineNumber();
    e.getPresentation().setVisible(myEnabled);
    e.getPresentation().setEnabled(myEnabled && number >= 0 && number < myFileAnnotation.getLineCount());
  }

  private int getActualLineNumber() {
    if (currentLine < 0) return -1;
    return myLineNumberProvider.getLineNumber(currentLine);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final int actualNumber = getActualLineNumber();
    if (actualNumber < 0) return;

    final VcsRevisionNumber revisionNumber = myFileAnnotation.getLineRevisionNumber(actualNumber);
    if (revisionNumber != null) {
      final VcsException[] exc = new VcsException[1];
      final List<Change> changes = new LinkedList<Change>();
      final FilePath[] targetPath = new FilePath[1];
      ProgressManager.getInstance().run(new Task.Backgroundable(myVcs.getProject(),
                                                                "Loading revision " + revisionNumber.asString() + " contents", true,
                                                                BackgroundFromStartOption.getInstance()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final CommittedChangesProvider provider = myVcs.getCommittedChangesProvider();
          try {
            final Pair<CommittedChangeList, FilePath> pair = provider.getOneList(myFile, revisionNumber);
            if (pair == null || pair.getFirst() == null) {
              VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), "Can not load data for show diff", MessageType.ERROR);
              return;
            }
            targetPath[0] = pair.getSecond() == null ? new FilePathImpl(myFile) : pair.getSecond();
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
            final ShowDiffUIContext context = new ShowDiffUIContext(true);
            context.setDiffNavigationContext(createDiffNavigationContext(actualNumber));
            if (ChangeListManager.getInstance(myVcs.getProject()).isFreezedWithNotification(null)) return;
            ShowDiffAction.showDiffForChange(changes.toArray(new Change[changes.size()]), idx, myVcs.getProject(), context);
          }
        }
      });
    }
  }

  private static int findSelfInList(List<Change> changes, final FilePath filePath) {
    int idx = -1;
    final File ioFile = filePath.getIOFile();
    for (int i = 0; i < changes.size(); i++) {
      final Change change = changes.get(i);
      if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().getIOFile().equals(ioFile))) {
        idx = i;
        break;
      }
    }
    if (idx >= 0) return idx;
    idx = 0;
    // try to use name only
    final String name = ioFile.getName();
    for (int i = 0; i < changes.size(); i++) {
      final Change change = changes.get(i);
      if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().getName().equals(name))) {
        idx = i;
        break;
      }
    }

    return idx;
  }

  // for current line number
  private DiffNavigationContext createDiffNavigationContext(final int actualLine) {
    final ContentsLines contentsLines = new ContentsLines(myFileAnnotation.getAnnotatedContent());

    final Pair<Integer, String> pair = correctActualLineIfTextEmpty(contentsLines, actualLine);
    return new DiffNavigationContext(new Iterable<String>() {
      @Override
      public Iterator<String> iterator() {
        return new CacheOneStepIterator<String>(new ContextLineIterator(contentsLines, myFileAnnotation, pair.getFirst()));
      }
    }, pair.getSecond());
  }

  private final static int ourVicinity = 5;

  private Pair<Integer, String> correctActualLineIfTextEmpty(final ContentsLines contentsLines, final int actualLine) {
    final VcsRevisionNumber revision = myFileAnnotation.getLineRevisionNumber(actualLine);

    for (int i = actualLine; (i < (actualLine + ourVicinity)) && (!contentsLines.isLineEndsFinished()); i++) {
      if (!revision.equals(myFileAnnotation.getLineRevisionNumber(i))) continue;
      final String lineContents = contentsLines.getLineContents(i);
      if (!StringUtil.isEmptyOrSpaces(lineContents)) {
        return new Pair<Integer, String>(i, lineContents);
      }
    }
    int bound = Math.max(actualLine - ourVicinity, 0);
    for (int i = actualLine - 1; (i >= bound); --i) {
      if (!revision.equals(myFileAnnotation.getLineRevisionNumber(i))) continue;
      final String lineContents = contentsLines.getLineContents(i);
      if (!StringUtil.isEmptyOrSpaces(lineContents)) {
        return new Pair<Integer, String>(i, lineContents);
      }
    }
    return new Pair<Integer, String>(actualLine, contentsLines.getLineContents(actualLine));
  }

  /**
   * Slightly break the contract: can return null from next() while had claimed hasNext()
   */
  private static class ContextLineIterator implements Iterator<String> {
    private final ContentsLines myContentsLines;

    private final VcsRevisionNumber myRevisionNumber;
    private final FileAnnotation myAnnotation;
    private final int myStopAtLine;
    // we assume file has at least one line ;)
    private int myCurrentLine;  // to start looking for next line with revision from

    private ContextLineIterator(final ContentsLines contentLines, final FileAnnotation annotation, final int stopAtLine) {
      myAnnotation = annotation;
      myRevisionNumber = myAnnotation.originalRevision(stopAtLine);
      myStopAtLine = stopAtLine;
      myContentsLines = contentLines;
    }

    @Override
    public boolean hasNext() {
      return lineNumberInBounds();
    }

    private boolean lineNumberInBounds() {
      final int knownLinesNumber = myContentsLines.getKnownLinesNumber();
      return ((knownLinesNumber == -1) || (myCurrentLine < knownLinesNumber)) && (myCurrentLine < myStopAtLine);
    }

    @Override
    public String next() {
      int nextLine;
      while (lineNumberInBounds()) {
        final VcsRevisionNumber vcsRevisionNumber = myAnnotation.originalRevision(myCurrentLine);
        if (myRevisionNumber.equals(vcsRevisionNumber)) {
          nextLine = myCurrentLine;
          final String text = myContentsLines.getLineContents(nextLine);
          if (!StringUtil.isEmptyOrSpaces(text)) {
            ++myCurrentLine;
            return text;
          }
        }
        ++myCurrentLine;
      }
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
