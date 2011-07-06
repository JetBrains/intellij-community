/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.ShiftedSimpleContent;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.TextCompareProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.BackgroundSynchronousInvisibleComputable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.DiffRequestFromChange;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * @author irengrig
 *         Date: 6/15/11
 *         Time: 6:02 PM
 */
public class FragmentedDiffRequestFromChange implements DiffRequestFromChange<ShiftedSimpleContent> {
  private final Project myProject;
  private final SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>> myRangesCache;

  public FragmentedDiffRequestFromChange(Project project) {
    myProject = project;
    myRangesCache = new SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>>(10, 10);
  }

  @Override
  public boolean canCreateRequest(Change change) {
    if (ChangesUtil.isTextConflictingChange(change)) return false;
    if (ShowDiffAction.isBinaryChange(myProject, change)) return false;
    final FilePath filePath = ChangesUtil.getFilePath(change);
    if (filePath.isDirectory()) return false;
    return true;
  }

  @Override
  public List<BeforeAfter<ShiftedSimpleContent>> createRequestForChange(Change change, int extraLines) throws VcsException {
    final FilePath filePath = ChangesUtil.getFilePath(change);

    final RangesCalculator calculator = new RangesCalculator();
    calculator.execute(change, filePath, myRangesCache, LineStatusTrackerManager.getInstance(myProject));
    final VcsException exception = calculator.getException();
    if (exception != null) {
      throw exception;
    }
    final FileType fileType = filePath.getFileType();
    final List<BeforeAfter<TextRange>> ranges = calculator.expand(extraLines);
    final List<BeforeAfter<ShiftedSimpleContent>> result = new ArrayList<BeforeAfter<ShiftedSimpleContent>>(ranges.size());
    final VirtualFile vFile = filePath.getVirtualFile();
    for (BeforeAfter<TextRange> range : ranges) {
      final TextRange beforeRange = range.getBefore();
      final TextRange convertedBefore = new TextRange(calculator.getOldDocument().getLineStartOffset(beforeRange.getStartOffset()),
                                                calculator.getOldDocument().getLineStartOffset(beforeRange.getEndOffset()));
      final ShiftedSimpleContent before = new ShiftedSimpleContent(calculator.getOldDocument().getText(convertedBefore), fileType, beforeRange.getStartOffset());
      final TextRange afterRange = range.getAfter();
      final TextRange convertedAfter = new TextRange(calculator.getDocument().getLineStartOffset(afterRange.getStartOffset()),
                                                calculator.getDocument().getLineStartOffset(afterRange.getEndOffset()));
      final ShiftedSimpleContent after = new ShiftedSimpleContent(calculator.getDocument().getText(convertedAfter), fileType, afterRange.getStartOffset());
      if (vFile != null) {
        before.setCharset(vFile.getCharset());
        before.setBOM(vFile.getBOM());
        after.setCharset(vFile.getCharset());
        after.setBOM(vFile.getBOM());
      }
      result.add(new BeforeAfter<ShiftedSimpleContent>(before, after));
    }
    return result;
  }

  private static class RangesCalculator {
    private List<BeforeAfter<TextRange>> myRanges;
    private VcsException myException;
    private Document myDocument;
    private Document myOldDocument;

    public Document getDocument() {
      return myDocument;
    }

    public Document getOldDocument() {
      return myOldDocument;
    }

    public void execute(final Change change, final FilePath filePath, final SLRUMap<Pair<Long, String>, List<BeforeAfter<TextRange>>> cache,
                        final LineStatusTrackerManagerI lstManager) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          try {
            myDocument = null;
            final String convertedPath = FilePathsHelper.convertPath(filePath);
            if (filePath.getVirtualFile() != null) {
              myDocument = FileDocumentManager.getInstance().getDocument(filePath.getVirtualFile());
              if (myDocument != null) {
                final List<BeforeAfter<TextRange>> cached = cache.get(new Pair<Long, String>(myDocument.getModificationStamp(), convertedPath));
                if (cached != null) {
                  myOldDocument = documentFromRevision(change.getBeforeRevision());
                  myRanges = cached;
                  return;
                }
              }
            }

            if (myDocument == null) {
              myDocument = documentFromRevision(change.getAfterRevision());
              final List<BeforeAfter<TextRange>> cached = cache.get(new Pair<Long, String>(-1L, convertedPath));
              if (cached != null) {
                myRanges = cached;
                return;
              }
            }

            // calculate from texts
            myOldDocument = documentFromRevision(change.getBeforeRevision());
            final TextCompareProcessor processor = new TextCompareProcessor(ComparisonPolicy.DEFAULT);
            final ArrayList<LineFragment> lineFragments = processor.process(myOldDocument.getText(), myDocument.getText());
            myRanges = new ArrayList<BeforeAfter<TextRange>>(lineFragments.size());
            for (LineFragment lineFragment : lineFragments) {
              if (! lineFragment.isEqual()) {
                final TextRange oldRange = lineFragment.getRange(FragmentSide.SIDE1);
                final TextRange newRange = lineFragment.getRange(FragmentSide.SIDE2);
                myRanges.add(new BeforeAfter<TextRange>(new TextRange(myOldDocument.getLineNumber(oldRange.getStartOffset()),
                                           myOldDocument.getLineNumber(oldRange.getEndOffset())),
                                                        new TextRange(myDocument.getLineNumber(newRange.getStartOffset()),
                                           myDocument.getLineNumber(newRange.getEndOffset()))));
              }
            }
            cache.put(new Pair<Long, String>(myDocument.getModificationStamp(), convertedPath), new ArrayList<BeforeAfter<TextRange>>(myRanges));
          }
          catch (VcsException e) {
            myException = e;
          }
          catch (FilesTooBigForDiffException e) {
            myException = new VcsException(e);
          }
        }
      });
    }

    public List<BeforeAfter<TextRange>> expand(final int lines) {
      if (myRanges == null || myRanges.isEmpty()) return Collections.emptyList();
      final List<BeforeAfter<TextRange>> shiftedRanges = new ArrayList<BeforeAfter<TextRange>>(myRanges.size());
      final int oldLineCount = myOldDocument.getLineCount();
      final int lineCount = myDocument.getLineCount();

      for (BeforeAfter<TextRange> range : myRanges) {
        final TextRange newBefore = expandRange(range.getBefore(), lines, oldLineCount);
        final TextRange newAfter = expandRange(range.getAfter(), lines, lineCount);
        shiftedRanges.add(new BeforeAfter<TextRange>(newBefore, newAfter));
      }

      // and zip
      final List<BeforeAfter<TextRange>> zippedRanges = new ArrayList<BeforeAfter<TextRange>>(myRanges.size());
      final ListIterator<BeforeAfter<TextRange>> iterator = shiftedRanges.listIterator();
      BeforeAfter<TextRange> previous = iterator.next();
      while (iterator.hasNext()) {
        final BeforeAfter<TextRange> current = iterator.next();
        if (previous.getBefore().intersects(current.getBefore()) || previous.getAfter().intersects(current.getAfter())) {
          previous = new BeforeAfter<TextRange>(previous.getBefore().union(current.getBefore()),
                                                previous.getAfter().union(current.getAfter()));
        } else {
          zippedRanges.add(previous);
          previous = current;
        }
      }
      zippedRanges.add(previous);
      return zippedRanges;
    }

    private TextRange expandRange(final TextRange range, final int shift, final int size) {
      return new TextRange(Math.max(0, (range.getStartOffset() - shift)), Math.max(0, Math.min(size - 1, range.getEndOffset() + shift)));
    }

    public List<BeforeAfter<TextRange>> getRanges() {
      return myRanges;
    }

    public VcsException getException() {
      return myException;
    }

    private Document documentFromRevision(final ContentRevision cr) throws VcsException {
      final Document oldDocument = new DocumentImpl(true);
      // todo !!! a question how to show line separators in diff etc
      // todo currently document doesn't allow to put \r as separator
      oldDocument.replaceString(0, oldDocument.getTextLength(), StringUtil.convertLineSeparators(notNullContentRevision(cr)));
      oldDocument.setReadOnly(true);
      return oldDocument;
    }

    private String notNullContentRevision(final ContentRevision cr) throws VcsException {
      if (cr == null) return "";
      final Ref<VcsException> ref = new Ref<VcsException>();
      final String s = new BackgroundSynchronousInvisibleComputable<String>() {
        @Override
        protected String runImpl() {
          try {
            return cr.getContent();
          }
          catch (VcsException e) {
            ref.set(e);
            return null;
          }
        }
      }.compute();
      if (! ref.isNull()) {
        throw ref.get();
      }
      return s == null ? "" : s;
    }
  }

  private static class MyWorker {
    private final Document myDocument;
    private final Document myOldDocument;
    private final List<Range> myRanges;

    private MyWorker(Document document, Document oldDocument, final List<Range> ranges) {
      myDocument = document;
      myOldDocument = oldDocument;
      myRanges = ranges;
    }

    @Nullable
    public List<BeforeAfter<TextRange>> execute() {
      final List<BeforeAfter<TextRange>> result = new ArrayList<BeforeAfter<TextRange>>();
      if (myRanges == null || myRanges.isEmpty()) return Collections.emptyList();
      for (Range range : myRanges) {
        final TextRange before = new TextRange(range.getUOffset1(), range.getUOffset2());
        final TextRange after = new TextRange(range.getOffset1(), range.getOffset2());
        result.add(new BeforeAfter<TextRange>(before, after));
      }
      return result;
    }
  }
}
