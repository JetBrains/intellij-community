// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.model.ModelPatch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public final class VcsFacadeImpl extends VcsFacade {

  @NotNull
  public static VcsFacadeImpl getVcsInstance() {
    return (VcsFacadeImpl)ApplicationManager.getApplication().getService(VcsFacade.class);
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
      if (change.getType() == Change.Type.NEW || change.getType() == Change.Type.MODIFICATION) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Boolean isFileUnderVcs(@NotNull PsiFile psiFile) {
    return VcsUtil.isFileUnderVcs(psiFile.getProject(), VcsUtil.getFilePath(psiFile.getVirtualFile()));
  }

  @Override
  public @NotNull Set<String> getVcsIgnoreFileNames(@NotNull Project project) {
    return VcsUtil.getVcsIgnoreFileNames(project);
  }

  @Override
  @NotNull
  public List<TextRange> getChangedTextRanges(@NotNull Project project, @NotNull PsiFile file) {
    ChangedRangesInfo helper = getChangedRangesInfo(file);
    return helper != null ? helper.allChangedRanges : new ArrayList<>();
  }

  @Override
  @Nullable
  public ChangedRangesInfo getChangedRangesInfo(@NotNull PsiFile file) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;

    ChangedRangesInfo cachedChangedTextHelper = getCachedChangedLines(project, document);
    if (cachedChangedTextHelper != null) {
      return cachedChangedTextHelper;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      CharSequence testContent = file.getUserData(TEST_REVISION_CONTENT);
      if (testContent != null) {
        return calculateChangedRangesInfo(document, testContent);
      }
    }

    Change change = ChangeListManager.getInstance(project).getChange(file.getVirtualFile());
    if (change == null) {
      return null;
    }
    if (change.getType() == Change.Type.NEW) {
      TextRange fileRange = file.getTextRange();
      return new ChangedRangesInfo(ContainerUtil.newArrayList(fileRange), null);
    }

    String contentFromVcs = getRevisionedContentFrom(change);
    return contentFromVcs != null ? calculateChangedRangesInfo(document, contentFromVcs) : null;
  }

  @Override
  public @NotNull List<PsiFile> getChangedFilesFromDirs(@NotNull Project project,
                                                                   @NotNull List<? extends PsiDirectory> dirs) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    Collection<Change> changes = new ArrayList<>();

    for (PsiDirectory dir : dirs) {
      changes.addAll(changeListManager.getChangesIn(dir.getVirtualFile()));
    }

    return getChangedFiles(project, changes);
  }

  @NotNull
  private static List<PsiFile> getChangedFiles(@NotNull final Project project, @NotNull Collection<? extends Change> changes) {
    Function<Change, PsiFile> changeToPsiFileMapper = new Function<>() {
      private final PsiManager myPsiManager = PsiManager.getInstance(project);

      @Override
      public PsiFile fun(Change change) {
        VirtualFile vFile = change.getVirtualFile();
        return vFile != null ? myPsiManager.findFile(vFile) : null;
      }
    };

    return ContainerUtil.mapNotNull(changes, changeToPsiFileMapper);
  }

  @NotNull
  public <T extends PsiElement> List<T> getChangedElements(@NotNull Project project,
                                                           Change @NotNull [] changes,
                                                           @NotNull Function<? super VirtualFile, ? extends List<T>> elementsConvertor) {
    List<T> result = new SmartList<>();
    for (Change change : changes) {
      if (change.getType() == Change.Type.DELETED) continue;
      if (!(change.getAfterRevision() instanceof CurrentContentRevision)) continue;

      VirtualFile file = ((CurrentContentRevision)change.getAfterRevision()).getVirtualFile();
      if (file == null) continue;

      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) continue;

      List<T> apply = elementsConvertor.fun(file);
      List<T> elements = apply == null ? null : ContainerUtil.skipNulls(apply);
      if (ContainerUtil.isEmpty(elements)) continue;

      BitSet changedLines = getChangedLines(project, document, change);
      if (changedLines != null) {
        for (T element : elements) {
          if (isElementChanged(element, document, changedLines)) {
            result.add(element);
          }
        }
      }
      else {
        result.addAll(elements);
      }
    }
    return result;
  }

  @Nullable
  private static BitSet getChangedLines(@NotNull Project project, @NotNull Document document, @NotNull Change change) {
    if (change.getType() == Change.Type.NEW) return null;

    List<? extends Range> ranges = getChangedRanges(project, document, change);
    if (ranges == null) return null;

    BitSet changedLines = new BitSet();
    for (Range range : ranges) {
      if (!range.hasLines()) {
        if (range.hasVcsLines()) {
          changedLines.set(Math.max(0, range.getLine1() - 1), range.getLine1() + 1);
        }
      }
      else {
        changedLines.set(range.getLine1(), range.getLine2());
      }
    }
    return changedLines;
  }

  @Nullable
  private static List<? extends Range> getChangedRanges(@NotNull Project project, @NotNull Document document, @NotNull Change change) {
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    if (tracker != null) {
      if (change instanceof ChangeListChange && tracker instanceof PartialLocalLineStatusTracker) {
        String changeListId = ((ChangeListChange)change).getChangeListId();
        List<LocalRange> ranges = ((PartialLocalLineStatusTracker)tracker).getRanges();
        if (ranges != null) {
          return ContainerUtil.filter(ranges, range -> range.getChangelistId().equals(changeListId));
        }
        else {
          return null;
        }
      }
      else {
        return tracker.getRanges();
      }
    }
    else {
      String contentFromVcs = getRevisionedContentFrom(change);
      if (contentFromVcs != null) {
        return getRanges(document, contentFromVcs);
      }
      else {
        return null;
      }
    }
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
  private static String getRevisionedContentFrom(@NotNull Change change) {
    ContentRevision revision = change.getBeforeRevision();
    if (revision == null) {
      return null;
    }

    try {
      return revision.getContent();
    }
    catch (VcsException e) {
      LOG.warn("Can't get content for: " + change.getVirtualFile(), e);
      return null;
    }
  }

  @Nullable
  private static ChangedRangesInfo getCachedChangedLines(@NotNull Project project, @NotNull Document document) {
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document);
    if (tracker != null) {
      List<? extends Range> ranges = tracker.getRanges();
      if (ranges != null) {
        return getChangedTextRanges(document, ranges);
      }
    }
    return null;
  }

  @NotNull
  private static ChangedRangesInfo calculateChangedRangesInfo(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    return getChangedTextRanges(document, getRanges(document, contentFromVcs));
  }

  @NotNull
  private static List<Range> getRanges(@NotNull Document document,
                                       @NotNull CharSequence contentFromVcs) {
    return RangesBuilder.createRanges(document.getImmutableCharSequence(), StringUtilRt.convertLineSeparators(contentFromVcs, "\n"));
  }

  @Override
  public int calculateChangedLinesNumber(@NotNull Document document, @NotNull CharSequence contentFromVcs) {
    List<Range> changedRanges = getRanges(document, contentFromVcs);
    int linesChanges = 0;
    for (Range range : changedRanges) {
      int inserted = range.getLine2() - range.getLine1();
      int deleted = range.getVcsLine2() - range.getVcsLine1();
      linesChanges += Math.max(inserted, deleted);
    }
    return linesChanges;
  }

  @NotNull
  private static ChangedRangesInfo getChangedTextRanges(@NotNull Document document, @NotNull List<? extends Range> changedRanges) {
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
    boolean isUnderVcs = VcsUtil.isFileUnderVcs(project, VcsUtil.getFilePath(file.getVirtualFile()));
    if (!isUnderVcs) return true;

    if (ChangeListManager.getInstance(project).isUnversioned(file.getVirtualFile())) {
      return true;
    }

    return false;
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
    return new SimpleChangesBrowser(project, changes);
  }

}
