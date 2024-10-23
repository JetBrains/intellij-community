// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.model.ModelPatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.*;

import static java.util.Collections.emptyList;

public final class VcsFacadeImpl extends VcsFacade {

  @NotNull
  public static VcsFacadeImpl getVcsInstance() {
    return (VcsFacadeImpl)VcsFacade.getInstance();
  }

  @Override
  public boolean hasActiveVcss(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project).hasActiveVcss();
  }

  @Override
  public boolean hasChanges(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final Change change = ChangeListManager.getInstance(project).getChange(virtualFile);
      return change != null;
    }
    return false;
  }

  @Override
  public boolean hasChanges(@NotNull VirtualFile file,
                            @NotNull Project project) {
    final Collection<Change> changes = ChangeListManager.getInstance(project).getChangesIn(file);
    for (Change change : changes) {
      if (change.getType() == Change.Type.DELETED) continue;
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  public Boolean isFileUnderVcs(@NotNull PsiFile psiFile) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(psiFile.getProject());
    return vcsManager.getVcsFor(psiFile.getVirtualFile()) != null;
  }

  @Override
  public @NotNull Set<String> getVcsIgnoreFileNames(@NotNull Project project) {
    return VcsUtil.getVcsIgnoreFileNames(project);
  }

  @Override
  @Nullable
  public ChangedRangesInfo getChangedRangesInfo(@NotNull PsiFile file) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;

    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    List<? extends Range> trackerRanges = tracker != null ? tracker.getRanges() : null;
    if (trackerRanges != null) {
      return getChangedRangesInfo(document, trackerRanges);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      CharSequence testContent = file.getUserData(TEST_REVISION_CONTENT);
      if (testContent != null) {
        return getChangedRangesInfo(document, computeRanges(document, testContent));
      }
    }

    Change change = ChangeListManager.getInstance(project).getChange(file.getVirtualFile());
    if (change == null) {
      return null;
    }
    if (change.getType() == Change.Type.NEW) {
      TextRange fileRange = file.getTextRange();
      return new ChangedRangesInfo(List.of(fileRange), null);
    }

    String contentFromVcs = getRevisionedContentFrom(change.getBeforeRevision());
    if (contentFromVcs == null) {
      return null;
    }
    return getChangedRangesInfo(document, computeRanges(document, contentFromVcs));
  }

  @Override
  public @NotNull List<PsiFile> getChangedFilesFromDirs(@NotNull Project project,
                                                        @NotNull List<? extends PsiDirectory> psiDirs) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    Collection<Change> allChanges = changeListManager.getAllChanges();
    List<VirtualFile> dirs = ContainerUtil.map(psiDirs, dir -> dir.getVirtualFile());
    List<? extends Change> changes = VcsImplUtil.filterChangesUnderFiles(allChanges, dirs).toList();

    return getChangedFiles(project, changes);
  }

  @NotNull
  private static List<PsiFile> getChangedFiles(@NotNull Project project, @NotNull Collection<? extends Change> changes) {
    PsiManager psiManager = PsiManager.getInstance(project);
    return ContainerUtil.mapNotNull(changes, change -> {
      VirtualFile vFile = change.getVirtualFile();
      return vFile != null ? psiManager.findFile(vFile) : null;
    });
  }

  @NotNull
  @Unmodifiable
  public <T extends PsiElement> List<T> getLocalChangedElements(@NotNull Project project,
                                                                @NotNull Change change,
                                                                @NotNull Function<? super VirtualFile, ? extends List<T>> elementExtractor) {
    if (change.getType() == Change.Type.DELETED) return emptyList();
    if (!(change.getAfterRevision() instanceof CurrentContentRevision)) return emptyList();

    VirtualFile file = ((CurrentContentRevision)change.getAfterRevision()).getVirtualFile();
    if (file == null) return emptyList();

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return emptyList();

    List<T> apply = elementExtractor.fun(file);
    List<T> elements = apply == null ? null : ContainerUtil.skipNulls(apply);
    if (ContainerUtil.isEmpty(elements)) return emptyList();

    if (change.getType() == Change.Type.NEW) return elements;

    List<? extends Range> ranges = getChangedRangesFromLineStatusTracker(project, document, change);
    if (ranges == null) ranges = getChangedRangesFromBeforeRevision(document, change);
    if (ranges == null) return elements; // assume the whole file is changed

    BitSet changedLines = createChangedLinesBitSet(ranges);
    return ContainerUtil.filter(elements, element -> isElementChanged(element, document, changedLines));
  }

  @NotNull
  public <T extends PsiElement> List<T> getPostCommitChangedElements(@NotNull Project project,
                                                                     @NotNull Change change,
                                                                     @NotNull Function<? super VirtualFile, ? extends List<T>> elementExtractor) {
    if (change.getType() == Change.Type.DELETED) return emptyList();

    VirtualFile file = ChangesUtil.getFilePath(change).getVirtualFile();
    if (file == null) return emptyList();

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return emptyList();

    List<T> apply = elementExtractor.fun(file);
    List<T> elements = apply == null ? null : ContainerUtil.skipNulls(apply);
    if (ContainerUtil.isEmpty(elements)) return emptyList();

    if (change.getType() == Change.Type.NEW) return elements;

    List<? extends Range> ranges = getChangedRangesForPostCommit(document, change);
    if (ranges == null) return elements; // assume the whole file is changed

    BitSet changedLines = createChangedLinesBitSet(ranges);
    return ContainerUtil.filter(elements, element -> isElementChanged(element, document, changedLines));
  }

  @Nullable
  private static List<? extends Range> getChangedRangesFromLineStatusTracker(@NotNull Project project,
                                                                             @NotNull Document document,
                                                                             @NotNull Change change) {
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    if (tracker == null) return null;

    if (change instanceof ChangeListChange &&
        tracker instanceof PartialLocalLineStatusTracker) {
      String changeListId = ((ChangeListChange)change).getChangeListId();
      List<LocalRange> ranges = ((PartialLocalLineStatusTracker)tracker).getRanges();
      if (ranges == null) return null;

      return ContainerUtil.filter(ranges, range -> range.getChangelistId().equals(changeListId));
    }
    else {
      return tracker.getRanges();
    }
  }

  @Nullable
  private static List<Range> getChangedRangesFromBeforeRevision(@NotNull Document document, @NotNull Change change) {
    String contentFromVcs = getRevisionedContentFrom(change.getBeforeRevision());
    if (contentFromVcs == null) return null;

    return computeRanges(document, contentFromVcs);
  }

  @Nullable
  private static List<Range> getChangedRangesForPostCommit(@NotNull Document document,
                                                           @NotNull Change change) {
    String conventBefore = getRevisionedContentFrom(change.getBeforeRevision());
    if (conventBefore == null) return null;

    String contentAfter = getRevisionedContentFrom(change.getAfterRevision());
    if (contentAfter == null) return null;

    CharSequence beforeText = fixLineSeparators(conventBefore);
    CharSequence afterText = fixLineSeparators(contentAfter);
    CharSequence localText = document.getImmutableCharSequence();

    LineOffsets beforeLineOffsets = LineOffsetsUtil.create(beforeText);
    LineOffsets afterLineOffsets = LineOffsetsUtil.create(afterText);
    LineOffsets localLineOffsets = LineOffsetsUtil.create(document);

    FairDiffIterable committedLines = RangesBuilder.compareLines(beforeText, afterText, beforeLineOffsets, afterLineOffsets);
    FairDiffIterable localLines = RangesBuilder.compareLines(afterText, localText, afterLineOffsets, localLineOffsets);

    ChangedRangesShifter builder = new ChangedRangesShifter();
    List<com.intellij.diff.util.Range> ranges = builder.execute(committedLines, localLines);

    return ContainerUtil.map(ranges, it -> new Range(it.start2, it.end2, it.start1, it.end1, null));
  }

  @NotNull
  private static BitSet createChangedLinesBitSet(@NotNull List<? extends Range> ranges) {
    BitSet changedLines = new BitSet();
    for (Range range : ranges) {
      if (range.hasLines()) {
        changedLines.set(range.getLine1(), range.getLine2());
      }
      else {
        // mark unchanged lines around deleted lines as modified
        changedLines.set(Math.max(0, range.getLine1() - 1), range.getLine1() + 1);
      }
    }
    return changedLines;
  }

  private static boolean isElementChanged(@NotNull PsiElement element, @NotNull Document document, @NotNull BitSet changedLines) {
    TextRange textRange = element.getTextRange();
    int startLine = document.getLineNumber(textRange.getStartOffset());
    int endLine = textRange.isEmpty()
                  ? startLine + 1
                  : document.getLineNumber(textRange.getEndOffset() - 1) + 1;
    int nextSetBit = changedLines.nextSetBit(startLine);
    return nextSetBit != -1 && nextSetBit < endLine;
  }

  @Nullable
  private static String getRevisionedContentFrom(@Nullable ContentRevision contentRevision) {
    if (contentRevision == null) {
      return null;
    }

    try {
      return contentRevision.getContent();
    }
    catch (VcsException e) {
      LOG.warn("Can't get content for: " + contentRevision, e);
      return null;
    }
  }

  @NotNull
  private static List<Range> computeRanges(@NotNull Document document,
                                           @NotNull CharSequence contentFromVcs) {
    return RangesBuilder.createRanges(document.getImmutableCharSequence(),
                                      fixLineSeparators(contentFromVcs));
  }

  @NotNull
  private static CharSequence fixLineSeparators(@NotNull CharSequence contentFromVcs) {
    return StringUtilRt.convertLineSeparators(contentFromVcs, "\n");
  }

  @Override
  public int calculateChangedLinesNumber(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    List<Range> changedRanges = computeRanges(document, contentFromVcs);
    int linesChanges = 0;
    for (Range range : changedRanges) {
      int inserted = range.getLine2() - range.getLine1();
      int deleted = range.getVcsLine2() - range.getVcsLine1();
      linesChanges += Math.max(inserted, deleted);
    }
    return linesChanges;
  }

  @NotNull
  private static ChangedRangesInfo getChangedRangesInfo(@NotNull Document document, @NotNull List<? extends Range> changedRanges) {
    final List<TextRange> ranges = new ArrayList<>();
    final List<TextRange> insertedRanges = new ArrayList<>();

    for (Range range : changedRanges) {
      if (range.hasLines()) {
        int changeStartLine = range.getLine1();
        int changeEndLine = range.getLine2();

        int lineStartOffset = document.getLineStartOffset(changeStartLine);
        int lineEndOffset = document.getLineEndOffset(changeEndLine - 1);

        TextRange changedTextRange = new TextRange(lineStartOffset, lineEndOffset);
        ranges.add(changedTextRange);
        if (!range.hasVcsLines()) {
          insertedRanges.add(changedTextRange);
        }
      }
    }

    return new ChangedRangesInfo(ranges, insertedRanges);
  }

  @Override
  public boolean isChangeNotTrackedForFile(@NotNull Project project, @NotNull PsiFile file) {
    if (isFileUnderVcs(file)) {
      FileStatus status = ChangeListManager.getInstance(project).getStatus(file.getVirtualFile());
      return status == FileStatus.UNKNOWN || status == FileStatus.IGNORED;
    }
    return true;
  }

  @Override
  public void runHeavyModificationTask(@NotNull Project project, @NotNull Document document, @NotNull Runnable o) {
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    if (tracker != null) {
      tracker.doFrozen(o);
    }
    else {
      o.run();
    }
  }

  @Override
  public void markFilesDirty(@NotNull Project project, @NotNull List<? extends VirtualFile> virtualFiles) {
    VcsFileUtil.markFilesDirty(project, virtualFiles);
  }

  @Override
  public JComponent createPatchPreviewComponent(@NotNull Project project, @NotNull ModelPatch patch) {
    List<Change> changes = EntryStream.of(patch.getBranchChanges()).mapKeyValue((file, content) -> {
      FilePath filePath = VcsUtil.getFilePath(file);
      ContentRevision current = new CurrentContentRevision(filePath);
      ContentRevision changed = new SimpleContentRevision(content.toString(), filePath, VcsBundle.message("patched.version.name"));
      return new Change(current, changed);
    }).toList();
    SimpleAsyncChangesBrowser browser = new SimpleAsyncChangesBrowser(project, false, false);
    browser.setChangesToDisplay(changes);
    return browser;
  }
}
