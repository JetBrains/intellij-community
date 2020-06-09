// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.FileAnnotation.RevisionChangesProvider;
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.CacheOneStepIterator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class ShowDiffFromAnnotation extends DumbAwareAction implements UpToDateLineNumberListener {
  private final @NotNull Project myProject;
  private final FileAnnotation myFileAnnotation;
  private final RevisionChangesProvider myChangesProvider;
  private int currentLine = -1;

  ShowDiffFromAnnotation(@NotNull Project project,
                         @NotNull FileAnnotation fileAnnotation) {
    ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
    setShortcutSet(CustomShortcutSet.EMPTY);
    myProject = project;
    myFileAnnotation = fileAnnotation;
    myChangesProvider = fileAnnotation.getRevisionsChangesProvider();
  }

  @Override
  public void consume(Integer integer) {
    currentLine = integer;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final int number = currentLine;
    e.getPresentation().setVisible(myChangesProvider != null);
    e.getPresentation().setEnabled(myChangesProvider != null && number >= 0 && number < myFileAnnotation.getLineCount());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final int actualNumber = currentLine;
    if (actualNumber < 0) return;

    VcsRevisionNumber revisionNumber = myFileAnnotation.getLineRevisionNumber(actualNumber);
    if (revisionNumber == null) return;

    DiffRequestChain requestChain = new ChangeDiffRequestChain.Async() {
      @Override
      protected @NotNull ListSelection<ChangeDiffRequestProducer> loadRequestProducers() throws DiffRequestProducerException {
        return loadRequests(myFileAnnotation, myChangesProvider, actualNumber);
      }
    };
    DiffManager.getInstance().showDiff(myProject, requestChain, DiffDialogHints.FRAME);
  }

  private static @NotNull ListSelection<ChangeDiffRequestProducer> loadRequests(@NotNull FileAnnotation fileAnnotation,
                                                                                @NotNull RevisionChangesProvider changesProvider,
                                                                                int actualNumber) throws DiffRequestProducerException {
    try {
      Pair<? extends CommittedChangeList, FilePath> pair = changesProvider.getChangesIn(actualNumber);
      if (pair == null || pair.getFirst() == null || pair.getSecond() == null) {
        throw new DiffRequestProducerException("Can not load data to show diff");
      }

      FilePath targetPath = pair.getSecond();
      List<Change> changes = ContainerUtil.sorted(pair.getFirst().getChanges(), ChangesComparator.getInstance(true));

      Map<Change, Map<Key<?>, Object>> context = new HashMap<>();
      int idx = findSelfInList(changes, targetPath);
      if (idx != -1) {
        DiffNavigationContext navigationContext = createDiffNavigationContext(fileAnnotation, actualNumber);
        context.put(changes.get(idx), Collections.singletonMap(DiffUserDataKeysEx.NAVIGATION_CONTEXT, navigationContext));
      }

      ListSelection<Change> changeSelection = ListSelection.createAt(changes, idx);
      return changeSelection.map(change -> ChangeDiffRequestProducer.create(fileAnnotation.getProject(), change, context.get(change)));
    }
    catch (VcsException e) {
      throw new DiffRequestProducerException(e);
    }
  }

  private static int findSelfInList(@NotNull List<? extends Change> changes, @NotNull FilePath filePath) {
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
  private static @Nullable DiffNavigationContext createDiffNavigationContext(@NotNull FileAnnotation fileAnnotation, int actualLine) {
    String annotatedContent = fileAnnotation.getAnnotatedContent();
    if (StringUtil.isEmptyOrSpaces(annotatedContent)) return null;

    String[] contentsLines = LineTokenizer.tokenize(annotatedContent, false, false);
    if (contentsLines.length <= actualLine) return null;

    final int correctedLine = correctActualLineIfTextEmpty(fileAnnotation, contentsLines, actualLine);
    return new DiffNavigationContext(new Iterable<String>() {
      @Override
      public Iterator<String> iterator() {
        return new CacheOneStepIterator<>(new ContextLineIterator(contentsLines, fileAnnotation, correctedLine));
      }
    }, contentsLines[correctedLine]);
  }

  private static final int ourVicinity = 5;

  private static int correctActualLineIfTextEmpty(@NotNull FileAnnotation fileAnnotation, String @NotNull [] contentsLines,
                                                  final int actualLine) {
    final VcsRevisionNumber revision = fileAnnotation.getLineRevisionNumber(actualLine);
    if (revision == null) return actualLine;
    if (!StringUtil.isEmptyOrSpaces(contentsLines[actualLine])) return actualLine;

    int upperBound = Math.min(actualLine + ourVicinity, contentsLines.length);
    for (int i = actualLine + 1; i < upperBound; i++) {
      if (revision.equals(fileAnnotation.getLineRevisionNumber(i)) && !StringUtil.isEmptyOrSpaces(contentsLines[i])) {
        return i;
      }
    }

    int lowerBound = Math.max(actualLine - ourVicinity, 0);
    for (int i = actualLine - 1; i >= lowerBound; --i) {
      if (revision.equals(fileAnnotation.getLineRevisionNumber(i)) && !StringUtil.isEmptyOrSpaces(contentsLines[i])) {
        return i;
      }
    }

    return actualLine;
  }

  /**
   * Slightly break the contract: can return null from next() while had claimed hasNext()
   */
  private static final class ContextLineIterator implements Iterator<String> {
    private final String @NotNull [] myContentsLines;

    private final VcsRevisionNumber myRevisionNumber;
    private final @NotNull FileAnnotation myAnnotation;
    private final int myStopAtLine;
    // we assume file has at least one line ;)
    private int myCurrentLine;  // to start looking for next line with revision from

    private ContextLineIterator(String @NotNull [] contentLines, @NotNull FileAnnotation annotation, int stopAtLine) {
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
